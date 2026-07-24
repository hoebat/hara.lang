use hara_wasm::native_cli::RuntimeBroker;
use hara_wasm::resp::{RespConnection, RespServer, RespValue};
use hara_wasm::Runtime;
use rustyline::completion::{Completer, Pair};
use rustyline::error::ReadlineError;
use rustyline::highlight::Highlighter;
use rustyline::hint::{Hinter, HistoryHinter};
use rustyline::history::DefaultHistory;
use rustyline::validate::{ValidationContext, ValidationResult, Validator};
use rustyline::{Config, Context, Editor, Helper};
use std::env;
use std::fs;
use std::io::{self, BufRead, Read};
use std::net::TcpStream;
use std::path::PathBuf;
use std::sync::{Arc, RwLock};

const COMMANDS: &[&str] = &[
    "/help", "/history", "/clear", "/splash", "/status", "/resp", "/ns", "/quit",
];

#[derive(Default)]
struct Options {
    root: Option<PathBuf>,
    native_sockets: bool,
    offline: bool,
    host: String,
    port: u16,
    command: Vec<String>,
}

fn main() {
    let options = match parse_options() {
        Ok(options) => options,
        Err(error) => exit_error(&error, 2),
    };
    if let Err(error) = run(options) {
        exit_error(&error, 1);
    }
}

fn parse_options() -> Result<Options, String> {
    let mut options = Options {
        host: "127.0.0.1".into(),
        port: 1311,
        ..Options::default()
    };
    let mut args = env::args().skip(1);
    while let Some(argument) = args.next() {
        match argument.as_str() {
            "--help" | "-h" => {
                usage();
                std::process::exit(0);
            }
            "--version" | "-V" => {
                println!("hara native {}", env!("CARGO_PKG_VERSION"));
                std::process::exit(0);
            }
            "--root" => options.root = Some(PathBuf::from(required(&mut args, "--root")?)),
            "--native-sockets" | "--allow-net" => options.native_sockets = true,
            "--offline" => options.offline = true,
            "--host" => options.host = required(&mut args, "--host")?,
            "--port" => {
                options.port = required(&mut args, "--port")?
                    .parse()
                    .map_err(|_| "--port must be between 0 and 65535".to_owned())?
            }
            value if value.starts_with('-') => return Err(format!("unknown option: {value}")),
            value => {
                options.command.push(value.into());
                options.command.extend(args);
                break;
            }
        }
    }
    Ok(options)
}

fn required(args: &mut impl Iterator<Item = String>, option: &str) -> Result<String, String> {
    args.next()
        .ok_or_else(|| format!("{option} requires a value"))
}

fn run(options: Options) -> Result<(), String> {
    match options.command.first().map(String::as_str) {
        Some("eval") => direct_eval(&options, &options.command[1..].join(" ")),
        Some("run") | Some("--file") => {
            let path = options
                .command
                .get(1)
                .ok_or_else(|| "run requires a file path".to_owned())?;
            let source =
                fs::read_to_string(path).map_err(|error| format!("cannot read {path}: {error}"))?;
            direct_eval(&options, &source)
        }
        Some("stdin") => {
            let mut source = String::new();
            io::stdin()
                .read_to_string(&mut source)
                .map_err(|error| format!("stdin: {error}"))?;
            direct_eval(&options, &source)
        }
        Some("headless" | "server") => run_headless(&options),
        Some("remote") => run_remote(
            options
                .command
                .get(1)
                .ok_or_else(|| "remote requires HOST:PORT".to_owned())?,
        ),
        Some("standalone") => run_repl(&options, true),
        Some("repl") | None => run_repl(&options, options.offline),
        Some(command) => Err(format!("unknown command: {command}")),
    }
}

fn direct_eval(options: &Options, source: &str) -> Result<(), String> {
    if source.is_empty() {
        return Err("eval requires a Hara expression".into());
    }
    let mut runtime = Runtime::new();
    if let Some(root) = &options.root {
        runtime.install_native_file_provider(root.to_string_lossy().as_ref());
    }
    if options.native_sockets {
        runtime.install_native_socket_provider();
    }
    println!("{}", runtime.eval_native_traced(source)?);
    Ok(())
}

fn run_headless(options: &Options) -> Result<(), String> {
    if options.offline {
        return Err("--offline cannot be used with headless".into());
    }
    let broker = RuntimeBroker::start_with(options.root.clone(), options.native_sockets)?;
    let server = RespServer::start(&options.host, options.port, broker)?;
    println!("HARA RESP {} · session ROOT", server.endpoint());
    loop {
        std::thread::park();
    }
}

struct RespController {
    host: String,
    port: u16,
    broker: RuntimeBroker,
    server: Option<RespServer>,
}

impl RespController {
    fn new(host: String, port: u16, broker: RuntimeBroker) -> Self {
        Self {
            host,
            port,
            broker,
            server: None,
        }
    }

    fn start(&mut self, endpoint: Option<&str>) -> Result<String, String> {
        if let Some(endpoint) = endpoint {
            let (host, port) = parse_endpoint(endpoint, &self.host)?;
            self.host = host;
            self.port = port;
        }
        if self.server.is_none() {
            self.server = Some(RespServer::start(
                &self.host,
                self.port,
                self.broker.clone(),
            )?);
        }
        Ok(self.status())
    }

    fn stop(&mut self) -> String {
        if let Some(mut server) = self.server.take() {
            server.stop();
        }
        self.status()
    }

    fn restart(&mut self, endpoint: Option<&str>) -> Result<String, String> {
        self.stop();
        self.start(endpoint)
    }

    fn status(&self) -> String {
        self.server.as_ref().map_or_else(
            || "RESP ○ offline".into(),
            |server| format!("RESP ● {}", server.endpoint()),
        )
    }
}

fn parse_endpoint(value: &str, fallback_host: &str) -> Result<(String, u16), String> {
    if let Ok(port) = value.parse::<u16>() {
        return Ok((fallback_host.into(), port));
    }
    let (host, port) = value
        .rsplit_once(':')
        .ok_or_else(|| "endpoint must be PORT or HOST:PORT".to_owned())?;
    let port = port
        .parse::<u16>()
        .map_err(|_| "port must be between 0 and 65535".to_owned())?;
    Ok((host.into(), port))
}

fn run_repl(options: &Options, offline: bool) -> Result<(), String> {
    let broker = RuntimeBroker::start_with(options.root.clone(), options.native_sockets)?;
    let mut resp = RespController::new(options.host.clone(), options.port, broker.clone());
    if !offline {
        resp.start(None)?;
    }
    if !is_terminal() {
        return run_plain_repl(&broker, &mut resp);
    }

    splash(&resp.status());
    let symbols = Arc::new(RwLock::new(Vec::new()));
    let helper = ReplHelper {
        symbols: symbols.clone(),
        hinter: HistoryHinter::new(),
    };
    let config = Config::builder().auto_add_history(false).build();
    let mut editor = Editor::<ReplHelper, DefaultHistory>::with_config(config)
        .map_err(|error| format!("terminal initialization failed: {error}"))?;
    editor.set_helper(Some(helper));
    let history_file = history_file();
    let _ = editor.load_history(&history_file);
    let mut entered = Vec::new();

    loop {
        *symbols.write().expect("completion symbols") = completion_values(&broker);
        let namespace = broker.namespace("ROOT").unwrap_or_else(|_| "user".into());
        let prompt = format!("[{namespace}] ");
        match editor.readline(&prompt) {
            Ok(line) => {
                let source = line.trim();
                if source.is_empty() {
                    continue;
                }
                if source.starts_with('/') {
                    if !repl_command(source, &broker, &mut resp, &entered)? {
                        break;
                    }
                    continue;
                }
                let _ = editor.add_history_entry(source);
                let _ = editor.save_history(&history_file);
                entered.push(source.to_owned());
                match broker.eval("ROOT", source) {
                    Ok(value) => println!("{value}"),
                    Err(error) => eprintln!("{error}"),
                }
            }
            Err(ReadlineError::Interrupted) => println!("^C"),
            Err(ReadlineError::Eof) => break,
            Err(error) => return Err(format!("terminal read failed: {error}")),
        }
    }
    let _ = editor.save_history(&history_file);
    Ok(())
}

fn run_plain_repl(broker: &RuntimeBroker, resp: &mut RespController) -> Result<(), String> {
    let mut entered = Vec::new();
    let mut pending = String::new();
    for line in io::stdin().lock().lines() {
        let line = line.map_err(|error| format!("stdin: {error}"))?;
        if pending.is_empty() && line.trim_start().starts_with('/') {
            if !repl_command(line.trim(), broker, resp, &entered)? {
                break;
            }
            continue;
        }
        if !pending.is_empty() {
            pending.push('\n');
        }
        pending.push_str(&line);
        if incomplete(&pending) {
            continue;
        }
        let source = pending.trim();
        if source == ":quit" || source == ":exit" {
            break;
        }
        if !source.is_empty() {
            entered.push(source.into());
            match broker.eval("ROOT", source) {
                Ok(value) => println!("{value}"),
                Err(error) => eprintln!("{error}"),
            }
        }
        pending.clear();
    }
    Ok(())
}

fn repl_command(
    source: &str,
    broker: &RuntimeBroker,
    resp: &mut RespController,
    history: &[String],
) -> Result<bool, String> {
    let parts = source.split_whitespace().collect::<Vec<_>>();
    match parts.first().copied().unwrap_or("") {
        "/quit" => return Ok(false),
        "/help" => println!(
            "/help /history /clear /splash /status /resp [start|stop|restart] /ns [NAME] /quit"
        ),
        "/history" => {
            for (index, value) in history.iter().enumerate() {
                println!("{}: {value}", index + 1);
            }
        }
        "/clear" => print!("\x1b[2J\x1b[H"),
        "/splash" => splash(&resp.status()),
        "/status" => println!("ROOT {} · {}", broker.info("ROOT")?, resp.status()),
        "/ns" if parts.len() == 1 => println!("{}", broker.namespace("ROOT")?),
        "/ns" if parts.len() == 2 => {
            println!("{}", broker.eval("ROOT", &format!("(ns {})", parts[1]))?)
        }
        "/resp" if parts.len() == 1 => println!("{}", resp.status()),
        "/resp" if parts.get(1) == Some(&"start") => {
            println!("{}", resp.start(parts.get(2).copied())?)
        }
        "/resp" if parts.get(1) == Some(&"stop") => println!("{}", resp.stop()),
        "/resp" if parts.get(1) == Some(&"restart") => {
            println!("{}", resp.restart(parts.get(2).copied())?)
        }
        command => eprintln!("Unknown REPL command: {command}"),
    }
    Ok(true)
}

fn completion_values(broker: &RuntimeBroker) -> Vec<String> {
    let mut values = COMMANDS
        .iter()
        .map(|value| (*value).into())
        .collect::<Vec<_>>();
    if let Ok(mut symbols) = broker.complete("ROOT", "") {
        values.append(&mut symbols);
    }
    values.sort();
    values.dedup();
    values
}

struct ReplHelper {
    symbols: Arc<RwLock<Vec<String>>>,
    hinter: HistoryHinter,
}

impl Helper for ReplHelper {}
impl Highlighter for ReplHelper {}
impl Hinter for ReplHelper {
    type Hint = String;
    fn hint(&self, line: &str, position: usize, context: &Context<'_>) -> Option<String> {
        self.hinter.hint(line, position, context)
    }
}
impl Validator for ReplHelper {
    fn validate(&self, context: &mut ValidationContext<'_>) -> rustyline::Result<ValidationResult> {
        Ok(if incomplete(context.input()) {
            ValidationResult::Incomplete
        } else {
            ValidationResult::Valid(None)
        })
    }
}
impl Completer for ReplHelper {
    type Candidate = Pair;
    fn complete(
        &self,
        line: &str,
        position: usize,
        _context: &Context<'_>,
    ) -> rustyline::Result<(usize, Vec<Pair>)> {
        let start = line[..position]
            .rfind(|ch: char| ch.is_whitespace() || "()[]{}\"'".contains(ch))
            .map_or(0, |index| index + 1);
        let prefix = &line[start..position];
        let values = self
            .symbols
            .read()
            .expect("completion symbols")
            .iter()
            .filter(|value| value.starts_with(prefix))
            .map(|value| Pair {
                display: value.clone(),
                replacement: value.clone(),
            })
            .collect();
        Ok((start, values))
    }
}

fn incomplete(source: &str) -> bool {
    let mut stack = Vec::new();
    let mut string = false;
    let mut escape = false;
    let mut comment = false;
    for ch in source.chars() {
        if comment {
            if ch == '\n' {
                comment = false;
            }
            continue;
        }
        if string {
            if escape {
                escape = false;
            } else if ch == '\\' {
                escape = true;
            } else if ch == '"' {
                string = false;
            }
            continue;
        }
        match ch {
            ';' => comment = true,
            '"' => string = true,
            '(' | '[' | '{' => stack.push(ch),
            ')' => {
                if stack.last() == Some(&'(') {
                    stack.pop();
                }
            }
            ']' => {
                if stack.last() == Some(&'[') {
                    stack.pop();
                }
            }
            '}' => {
                if stack.last() == Some(&'{') {
                    stack.pop();
                }
            }
            _ => {}
        }
    }
    string || !stack.is_empty()
}

fn run_remote(endpoint: &str) -> Result<(), String> {
    let (host, port) = parse_endpoint(endpoint, "127.0.0.1")?;
    let stream = TcpStream::connect((host.as_str(), port))
        .map_err(|error| format!("remote connect failed: {error}"))?;
    let mut connection = RespConnection::new(stream)?;
    connection.write(&RespValue::array(["HELLO", "4", "CLIENT", "HARA-REMOTE"]))?;
    println!(
        "{}",
        response_text(connection.read()?.ok_or("remote closed")?)
    );
    let mut request = 0_u64;
    for line in io::stdin().lock().lines() {
        let source = line.map_err(|error| format!("stdin: {error}"))?;
        if matches!(source.trim(), "/quit" | ":quit") {
            connection.write(&RespValue::array(["QUIT"]))?;
            break;
        }
        request += 1;
        let id = format!("REMOTE-{request}");
        connection.write(&RespValue::array(["EVAL", &id, source.trim()]))?;
        if let Some(value) = connection.read()? {
            println!("{}", response_text(value));
        }
        let _ = connection.read()?;
    }
    Ok(())
}

fn response_text(value: RespValue) -> String {
    match value {
        RespValue::Array(Some(values)) => values
            .into_iter()
            .map(response_text)
            .collect::<Vec<_>>()
            .join(" "),
        RespValue::Bulk(Some(bytes)) => String::from_utf8_lossy(&bytes).into_owned(),
        RespValue::Simple(value) | RespValue::Error(value) => value,
        RespValue::Integer(value) => value.to_string(),
        RespValue::Bulk(None) | RespValue::Array(None) => "nil".into(),
    }
}

fn history_file() -> PathBuf {
    env::var_os("HARA_HISTORY")
        .map(PathBuf::from)
        .or_else(|| env::var_os("HOME").map(|home| PathBuf::from(home).join(".hara_history")))
        .unwrap_or_else(|| PathBuf::from(".hara_history"))
}

fn splash(resp: &str) {
    if env::var_os("HARA_REPL_SPLASH").as_deref() == Some(std::ffi::OsStr::new("false")) {
        return;
    }
    println!("HARA · Journey Within · RUST · ROOT\n{resp}\n");
}

fn is_terminal() -> bool {
    unsafe { libc_isatty(0) != 0 }
}

#[cfg(unix)]
unsafe fn libc_isatty(fd: i32) -> i32 {
    unsafe extern "C" {
        fn isatty(fd: i32) -> i32;
    }
    unsafe { isatty(fd) }
}
#[cfg(not(unix))]
unsafe fn libc_isatty(_fd: i32) -> i32 {
    0
}

fn usage() {
    println!("hara [OPTIONS] [repl|standalone|headless|server|remote HOST:PORT|eval SOURCE|run FILE|stdin]");
    println!("  --offline --host HOST --port PORT --root PATH --allow-net");
}

fn exit_error(message: &str, status: i32) -> ! {
    eprintln!("hara: {message}");
    std::process::exit(status)
}

#[cfg(test)]
mod tests {
    use super::incomplete;

    #[test]
    fn multiline_detection_ignores_strings_comments_and_escapes() {
        assert!(incomplete("(defn value [x]\n  (+ x 1)"));
        assert!(!incomplete("(defn value [x]\n  (+ x 1))"));
        assert!(!incomplete("(str \"[\" ) ; ("));
    }
}
