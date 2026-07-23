# Hara REPL UX specification

The REPL is a JLine-based interactive shell. It provides line editing, persistent history,
completion, and inline documentation while evaluating ordinary Hara forms.

## Session flow

```text
start
  |
  v
custom splash / session banner
  |
  v
read line --edit-- history --complete-- docs
  |
  v
read complete Hara form
  |
  v
evaluate -> print value
  |
  +----> print friendly error and continue
  |
  +----> EOF -> exit
```

The current banner identifies the Hara runtime environment and session key. Embedders may replace
the banner or suppress it; banner customization must not change evaluation semantics.

## History

History is persisted in the user's Hara history file and is incrementally updated while editing:

```text
previous session ---> ~/.hara_history <--- current session
       ^                                      |
       +------ up/down, search, replay -------+
```

History stores entered forms, not evaluated values. Implementations should avoid writing secrets
or incomplete input when the host provides a history filter.

## Completion

Completion has two sources:

```text
typed prefix
     |
     +--> visible Hara symbols ----+
     |                              |
     +--> Java packages/classes ----+--> candidates
```

Completion understands Lisp delimiters: `(`, `)`, `[`, `]`, `{`, `}`, and whitespace. Symbol
completion comes from the runtime-visible namespace; class completion comes from the asynchronously
scanned package tree. A slow classpath scan must not prevent ordinary symbol completion.

Examples:

```text
(con<TAB>       -> concat
(hara.lib.<TAB> -> available library symbols
java.lang.Str<TAB> -> java.lang.String
```

## Documentation widget

The documentation widget is bound to common help keys, including `Alt-q`, `F1`, and supported
Shift-Enter terminal sequences:

```text
cursor on symbol -> inspect metadata -> show doc/arglists -> redraw prompt
```

Missing metadata is a no-op. Documentation output must not alter the input buffer or evaluation
state.

## Slash-command boundary

The dispatcher described below is the target control-layer contract; the current implementation provides the JLine loop, history, completion, and documentation widget, while slash-command dispatch remains to be implemented.

Slash commands are intentionally a separate REPL control layer. They are not Hara symbols and are
never sent to the evaluator:

```text
input line
   |
   +--> starts with / ? -- yes --> REPL command dispatcher
   |                                  |
   |                                  +--> /help /history /clear /quit
   |
   +--> no ------------------------> Hara reader/evaluator
```

The command vocabulary is an extension point. Unknown commands should produce a concise REPL
error and leave the session running. A slash command must not shadow a valid Hara form because the
dispatch decision is made only at the beginning of an input line.
