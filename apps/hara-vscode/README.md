# Hara VS Code Extension

A VS Code extension that provides interactive development support for the Hara language by connecting to a Hara server via the Redis protocol.

## Features

- **Connect to Server**: Establish a connection to a running Hara server
- **Evaluate Selection**: Send selected code to the server for evaluation and see results instantly
- **Output Channel**: View evaluation results and server communication in a dedicated output panel

## Getting Started

### Prerequisites

Before installing the extension, ensure you have:

1. **Node.js and npm** installed (version 18 or higher recommended)
2. **Visual Studio Code** (version 1.80.0 or higher)
3. **Hara Runtime** - The main Hara language runtime must be built and available

### Installation

#### Step 1: Install Dependencies

Navigate to the `apps/hara-vscode` directory and install the required npm packages:

```bash
cd apps/hara-vscode
npm install
```

#### Step 2: Compile the Extension

Compile the TypeScript source code:

```bash
npm run compile
```

This will generate the compiled JavaScript in the `out/` directory.

#### Step 3: Install the Extension in VS Code

You have two options:

**Option A: Development Mode (Recommended for testing)**

1. Open the `apps/hara-vscode` folder in VS Code
2. Press `F5` to launch a new VS Code window with the extension loaded
3. The extension will be active in the Extension Development Host window

**Option B: Package and Install**

1. Install the VS Code Extension Manager CLI:
   ```bash
   npm install -g @vscode/vsce
   ```

2. Package the extension:
   ```bash
   vsce package
   ```

3. Install the generated `.vsix` file:
   - In VS Code, press `Cmd+Shift+P` (Mac) or `Ctrl+Shift+P` (Windows/Linux)
   - Type "Extensions: Install from VSIX..."
   - Select the generated `vscode-hara-0.0.1.vsix` file

### Step 4: Start the Hara Server

Before using the extension, you need to start the Hara server. From the main `hara.lang` project directory:

```bash
mvn exec:java -Dexec.mainClass="hara.kernel.Main"
```

The server will start on `127.0.0.1:1311` by default.

## Usage

### Connecting to the Server

1. Open the Command Palette (`Cmd+Shift+P` on Mac, `Ctrl+Shift+P` on Windows/Linux)
2. Type "Hara: Connect to Server" and press Enter
3. You should see a confirmation message: "Connected to Hara Server"

The extension will automatically connect when you evaluate code if not already connected.

### Evaluating Code

1. Open a file or create a new one with Hara code
2. Select the code you want to evaluate
3. Open the Command Palette and run "Hara: Evaluate Selection"
4. The result will appear in:
   - A popup notification
   - The "Hara" output channel (View → Output → Select "Hara")

**Example:**

```clojure
(+ 1 2 3)
```

Select this code and evaluate it. You should see the result: `6`

### Viewing Output

To see detailed evaluation logs:

1. Go to View → Output (or press `Cmd+Shift+U` / `Ctrl+Shift+U`)
2. Select "Hara" from the dropdown menu
3. You'll see connection status, evaluation requests, and results

## Configuration

You can customize the extension settings in VS Code:

1. Go to Code → Settings → Settings (or File → Preferences → Settings on Windows/Linux)
2. Search for "Hara"
3. Configure the following options:

| Setting | Description | Default |
|---------|-------------|---------|
| `hara.server.host` | Hostname of the Hara server | `127.0.0.1` |
| `hara.server.port` | Port of the Hara server | `1311` |
| `hara.session` | Session to use for evaluation | `ROOT` |

**Example settings.json:**

```json
{
  "hara.server.host": "127.0.0.1",
  "hara.server.port": 1311,
  "hara.session": "ROOT"
}
```

## Session Management

The extension provides comprehensive session management capabilities, allowing you to work with multiple isolated Hara runtime sessions.

### Status Bar

The current session is displayed in the status bar at the bottom left of VS Code:

```
$(database) Hara: ROOT
```

**Click the status bar item** to quickly switch between sessions.

### Managing Sessions

#### List Sessions

View all available sessions on the server:

1. Open Command Palette (`Cmd+Shift+P` / `Ctrl+Shift+P`)
2. Run "Hara: List Sessions"
3. Sessions will be displayed in a notification and the output channel

#### Create a New Session

Create a new isolated session:

1. Open Command Palette
2. Run "Hara: Create Session"
3. Enter a session name (letters, numbers, hyphens, and underscores only)
4. Choose whether to switch to the new session immediately

**Example:** Create a session called `dev-test` for experimental code.

#### Switch Sessions

Switch to a different session:

1. Click the status bar item showing the current session, OR
2. Open Command Palette and run "Hara: Switch Session"
3. Select a session from the quick pick menu
4. The status bar will update to show the new session

All subsequent code evaluations will use the selected session.

#### Delete a Session

Remove a session you no longer need:

1. Open Command Palette
2. Run "Hara: Delete Session"
3. Select a session to delete (ROOT cannot be deleted)
4. Confirm the deletion
5. If you delete the current session, you'll automatically switch to ROOT

### Session Isolation

Each session maintains its own isolated state:

- Variables and functions defined in one session don't affect others
- You can test different versions of code in parallel
- Sessions persist until explicitly deleted or the server restarts

**Example Workflow:**

```clojure
; In session "main"
(def x 10)
(+ x 5)  ; => 15

; Switch to session "test"
x  ; => Error: x is not defined

; Define x differently in "test"
(def x 100)
(+ x 5)  ; => 105

; Switch back to "main"
x  ; => 10 (unchanged)
```

### Default Session

The `hara.session` configuration setting determines the default session when VS Code starts. The extension remembers your last active session per workspace.

## Keyboard Shortcuts

For faster workflow, you can add custom keybindings:

1. Go to Code → Settings → Keyboard Shortcuts (or File → Preferences → Keyboard Shortcuts)
2. Search for "Hara: Evaluate Selection"
3. Click the `+` icon to add a keybinding (e.g., `Cmd+E` or `Ctrl+E`)

**Example keybindings.json:**

```json
[
  {
    "key": "cmd+e",
    "command": "hara.evaluateSelection",
    "when": "editorTextFocus"
  },
  {
    "key": "cmd+shift+c",
    "command": "hara.connect"
  },
  {
    "key": "cmd+shift+s",
    "command": "hara.switchSession"
  },
  {
    "key": "cmd+shift+n",
    "command": "hara.createSession"
  }
]
```

## Troubleshooting

### Connection Failed

**Problem:** "Connection failed: connect ECONNREFUSED 127.0.0.1:1311"

**Solution:**
- Ensure the Hara server is running: `mvn exec:java -Dexec.mainClass="hara.kernel.Main"`
- Check that the server is listening on the correct port (default: 1311)
- Verify your `hara.server.host` and `hara.server.port` settings

### No Text Selected

**Problem:** "No text selected" message when trying to evaluate

**Solution:**
- Make sure you have selected the code you want to evaluate before running the command
- The selection must contain at least one character

### Evaluation Error

**Problem:** Server returns an error during evaluation

**Solution:**
- Check the Hara output channel for detailed error messages
- Verify your code syntax is correct
- Ensure the session exists and has the required definitions

### Extension Not Loading

**Problem:** Extension commands don't appear in the Command Palette

**Solution:**
- Reload VS Code window (Command Palette → "Developer: Reload Window")
- Check that the extension is enabled in the Extensions panel
- If running in development mode, ensure you pressed `F5` from the `apps/hara-vscode` folder

## Development

### Building from Source

```bash
# Install dependencies
npm install

# Compile TypeScript
npm run compile

# Watch mode for development
npm run watch
```

### Project Structure

```
hara-vscode/
├── src/
│   └── extension.ts    # Main extension code
├── out/                # Compiled JavaScript (generated)
├── package.json        # Extension manifest
├── tsconfig.json       # TypeScript configuration
└── README.md          # This file
```

## Requirements

- **VS Code**: Version 1.80.0 or higher
- **Node.js**: Version 18 or higher
- **Hara Server**: A running instance of the Hara language server

## License

See the main Hara project for license information.
