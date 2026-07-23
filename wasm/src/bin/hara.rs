use hara_wasm::Runtime;
use std::env;
use std::fs;
use std::io::{self, BufRead, Write};
use std::path::PathBuf;

fn usage() {
    eprintln!("Usage: hara [--root PATH] [--native-sockets] eval SOURCE");
    eprintln!("       hara [--root PATH] [--native-sockets] --file PATH");
    eprintln!("       hara [--root PATH] [--native-sockets]            # interactive stdin");
}

fn main() {
    let mut args = env::args().skip(1).peekable();
    let mut root: Option<PathBuf> = None;
    let mut native_sockets = false;
    let mut source: Option<String> = None;
    let mut file: Option<PathBuf> = None;

    while let Some(arg) = args.next() {
        match arg.as_str() {
            "--help" | "-h" => {
                usage();
                return;
            }
            "--version" | "-V" => {
                println!("hara native {}", env!("CARGO_PKG_VERSION"));
                return;
            }
            "--root" => {
                let Some(value) = args.next() else {
                    eprintln!("hara: --root requires a path");
                    std::process::exit(2);
                };
                root = Some(PathBuf::from(value));
            }
            "--native-sockets" => native_sockets = true,
            "--file" => {
                let Some(value) = args.next() else {
                    eprintln!("hara: --file requires a path");
                    std::process::exit(2);
                };
                file = Some(PathBuf::from(value));
            }
            "eval" => {
                source = Some(args.collect::<Vec<_>>().join(" "));
                break;
            }
            value if value.starts_with('-') => {
                eprintln!("hara: unknown option {value}");
                usage();
                std::process::exit(2);
            }
            value => {
                source = Some(value.to_string());
                break;
            }
        }
    }

    let mut runtime = Runtime::new();
    if let Some(root) = root {
        runtime.install_native_file_provider(root.to_string_lossy().as_ref());
    }
    if native_sockets {
        runtime.install_native_socket_provider();
    }

    if let Some(path) = file {
        match fs::read_to_string(&path) {
            Ok(program) => evaluate(&mut runtime, &program),
            Err(error) => fail(format!("cannot read {}: {error}", path.display())),
        }
    } else if let Some(program) = source {
        evaluate(&mut runtime, &program);
    } else {
        repl(&mut runtime);
    }
}

fn evaluate(runtime: &mut Runtime, source: &str) {
    match runtime.eval_native(source) {
        Ok(value) => println!("{value}"),
        Err(error) => fail(error),
    }
}

fn repl(runtime: &mut Runtime) {
    let stdin = io::stdin();
    let mut input = stdin.lock();
    let interactive = atty();
    let mut line = String::new();
    loop {
        if interactive {
            print!("hara> ");
            let _ = io::stdout().flush();
        }
        line.clear();
        match input.read_line(&mut line) {
            Ok(0) => break,
            Ok(_) => {
                let source = line.trim();
                if source == ":quit" || source == ":exit" { break; }
                if !source.is_empty() { evaluate(runtime, source); }
            }
            Err(error) => fail(format!("stdin: {error}")),
        }
    }
}

fn atty() -> bool {
    // Avoid another dependency: a prompt is useful only for a terminal stdin.
    unsafe { libc_isatty(0) != 0 }
}

#[cfg(unix)]
unsafe fn libc_isatty(fd: i32) -> i32 { extern "C" { fn isatty(fd: i32) -> i32; } isatty(fd) }
#[cfg(not(unix))]
unsafe fn libc_isatty(_fd: i32) -> i32 { 0 }

fn fail(message: String) -> ! {
    eprintln!("hara: {message}");
    std::process::exit(1);
}
