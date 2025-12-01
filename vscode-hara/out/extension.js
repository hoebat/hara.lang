"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.activate = activate;
exports.deactivate = deactivate;
const vscode = require("vscode");
const net = require("net");
function activate(context) {
    const outputChannel = vscode.window.createOutputChannel("Hara");
    const clientWrapper = new HaraClient(outputChannel, context);
    // Create status bar item for current session
    const statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
    statusBarItem.command = 'hara.switchSession';
    statusBarItem.tooltip = 'Click to switch Hara session';
    statusBarItem.show();
    context.subscriptions.push(statusBarItem);
    // Update status bar with current session
    const updateStatusBar = () => {
        const session = clientWrapper.getCurrentSession();
        statusBarItem.text = `$(database) Hara: ${session}`;
    };
    updateStatusBar();
    let connectDisposable = vscode.commands.registerCommand('hara.connect', async () => {
        try {
            await clientWrapper.connect();
            vscode.window.showInformationMessage("Connected to Hara Server.");
        }
        catch (e) {
            vscode.window.showErrorMessage(`Connection failed: ${e.message}`);
        }
    });
    let evalDisposable = vscode.commands.registerCommand('hara.evaluateSelection', async () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            return;
        }
        const selection = editor.selection;
        const text = editor.document.getText(selection);
        if (!text) {
            vscode.window.showInformationMessage("No text selected.");
            return;
        }
        const session = clientWrapper.getCurrentSession();
        try {
            outputChannel.appendLine(`Evaluating in ${session}: ${text}`);
            const result = await clientWrapper.send(['EVAL', session, text]);
            outputChannel.appendLine(`Result: ${result}`);
            vscode.window.showInformationMessage(`Result: ${result}`);
        }
        catch (e) {
            outputChannel.appendLine(`Error: ${e.message}`);
            vscode.window.showErrorMessage(`Evaluation Error: ${e.message}`);
        }
    });
    let listSessionsDisposable = vscode.commands.registerCommand('hara.listSessions', async () => {
        try {
            const sessions = await clientWrapper.listSessions();
            outputChannel.appendLine(`Available sessions: ${sessions.join(', ')}`);
            vscode.window.showInformationMessage(`Sessions: ${sessions.join(', ')}`);
        }
        catch (e) {
            vscode.window.showErrorMessage(`Failed to list sessions: ${e.message}`);
        }
    });
    let createSessionDisposable = vscode.commands.registerCommand('hara.createSession', async () => {
        const sessionName = await vscode.window.showInputBox({
            prompt: 'Enter new session name',
            placeHolder: 'my-session',
            validateInput: (value) => {
                if (!value || value.trim().length === 0) {
                    return 'Session name cannot be empty';
                }
                if (!/^[a-zA-Z0-9_-]+$/.test(value)) {
                    return 'Session name can only contain letters, numbers, hyphens, and underscores';
                }
                return null;
            }
        });
        if (!sessionName) {
            return;
        }
        try {
            await clientWrapper.createSession(sessionName);
            vscode.window.showInformationMessage(`Session '${sessionName}' created successfully.`);
            // Ask if user wants to switch to the new session
            const switchNow = await vscode.window.showQuickPick(['Yes', 'No'], {
                placeHolder: `Switch to session '${sessionName}' now?`
            });
            if (switchNow === 'Yes') {
                clientWrapper.setCurrentSession(sessionName);
                updateStatusBar();
                vscode.window.showInformationMessage(`Switched to session '${sessionName}'.`);
            }
        }
        catch (e) {
            vscode.window.showErrorMessage(`Failed to create session: ${e.message}`);
        }
    });
    let switchSessionDisposable = vscode.commands.registerCommand('hara.switchSession', async () => {
        try {
            const sessions = await clientWrapper.listSessions();
            const currentSession = clientWrapper.getCurrentSession();
            const items = sessions.map((s) => ({
                label: s,
                description: s === currentSession ? '(current)' : ''
            }));
            const selected = await vscode.window.showQuickPick(items, {
                placeHolder: 'Select a session to switch to'
            });
            if (selected && selected.label !== currentSession) {
                clientWrapper.setCurrentSession(selected.label);
                updateStatusBar();
                vscode.window.showInformationMessage(`Switched to session '${selected.label}'.`);
            }
        }
        catch (e) {
            vscode.window.showErrorMessage(`Failed to switch session: ${e.message}`);
        }
    });
    let deleteSessionDisposable = vscode.commands.registerCommand('hara.deleteSession', async () => {
        try {
            const sessions = await clientWrapper.listSessions();
            const currentSession = clientWrapper.getCurrentSession();
            // Filter out ROOT session (cannot be deleted)
            const deletableSessions = sessions.filter((s) => s !== 'ROOT');
            if (deletableSessions.length === 0) {
                vscode.window.showInformationMessage('No sessions available to delete (ROOT cannot be deleted).');
                return;
            }
            const selected = await vscode.window.showQuickPick(deletableSessions, {
                placeHolder: 'Select a session to delete'
            });
            if (!selected) {
                return;
            }
            // Confirm deletion
            const confirm = await vscode.window.showWarningMessage(`Are you sure you want to delete session '${selected}'?`, { modal: true }, 'Delete');
            if (confirm !== 'Delete') {
                return;
            }
            await clientWrapper.deleteSession(selected);
            vscode.window.showInformationMessage(`Session '${selected}' deleted successfully.`);
            // If we deleted the current session, switch to ROOT
            if (selected === currentSession) {
                clientWrapper.setCurrentSession('ROOT');
                updateStatusBar();
                vscode.window.showInformationMessage('Switched to ROOT session.');
            }
        }
        catch (e) {
            vscode.window.showErrorMessage(`Failed to delete session: ${e.message}`);
        }
    });
    context.subscriptions.push(connectDisposable);
    context.subscriptions.push(evalDisposable);
    context.subscriptions.push(listSessionsDisposable);
    context.subscriptions.push(createSessionDisposable);
    context.subscriptions.push(switchSessionDisposable);
    context.subscriptions.push(deleteSessionDisposable);
    context.subscriptions.push(outputChannel);
}
function deactivate() { }
class HaraClient {
    constructor(output, context) {
        this.client = null;
        this.connected = false;
        this.buffer = Buffer.alloc(0);
        this.pendingRequest = null;
        this.output = output;
        this.context = context;
        // Load session from workspace state or use default
        const config = vscode.workspace.getConfiguration('hara');
        const defaultSession = config.get('session', 'ROOT');
        this.currentSession = context.workspaceState.get('hara.currentSession', defaultSession);
    }
    getConfig() {
        const config = vscode.workspace.getConfiguration('hara');
        return {
            host: config.get('server.host', '127.0.0.1'),
            port: config.get('server.port', 4164),
        };
    }
    getCurrentSession() {
        return this.currentSession;
    }
    setCurrentSession(session) {
        this.currentSession = session;
        this.context.workspaceState.update('hara.currentSession', session);
    }
    async listSessions() {
        const result = await this.send(['SESSION', 'LIST']);
        try {
            // Parse the result - expecting a JSON array
            const parsed = JSON.parse(result);
            if (Array.isArray(parsed)) {
                return parsed;
            }
            throw new Error('Invalid response format');
        }
        catch (e) {
            throw new Error(`Failed to parse session list: ${e.message}`);
        }
    }
    async createSession(name) {
        await this.send(['SESSION', 'CREATE', name]);
    }
    async deleteSession(name) {
        await this.send(['SESSION', 'DELETE', name]);
    }
    connect() {
        if (this.client) {
            this.client.destroy();
            this.client = null;
            this.connected = false;
        }
        const config = this.getConfig();
        return new Promise((resolve, reject) => {
            this.output.appendLine(`Connecting to ${config.host}:${config.port}...`);
            this.client = new net.Socket();
            const errorHandler = (err) => {
                this.connected = false;
                this.output.appendLine(`Connection Error: ${err.message}`);
                reject(err);
            };
            this.client.connect(config.port, config.host, () => {
                this.connected = true;
                this.client?.removeListener('error', errorHandler);
                this.client?.on('error', (err) => {
                    this.connected = false;
                    this.output.appendLine(`Socket Error: ${err.message}`);
                    if (this.pendingRequest) {
                        this.pendingRequest.reject(err);
                        this.pendingRequest = null;
                    }
                });
                this.output.appendLine("Connected.");
                resolve();
            });
            this.client.on('error', errorHandler);
            this.client.on('close', () => {
                this.connected = false;
                this.output.appendLine("Connection closed.");
                if (this.pendingRequest) {
                    this.pendingRequest.reject(new Error("Connection closed"));
                    this.pendingRequest = null;
                }
            });
            this.client.on('data', (data) => {
                this.buffer = Buffer.concat([this.buffer, data]);
                this.processBuffer();
            });
        });
    }
    async send(args) {
        if (!this.connected || !this.client) {
            await this.connect();
        }
        if (this.pendingRequest) {
            throw new Error("Another request is pending");
        }
        return new Promise((resolve, reject) => {
            this.pendingRequest = { resolve, reject };
            const CRLF = '\r\n';
            let cmdStr = `*${args.length}${CRLF}`;
            for (const arg of args) {
                const buffer = Buffer.from(arg, 'utf8');
                cmdStr += `$${buffer.length}${CRLF}${arg}${CRLF}`;
            }
            this.client?.write(cmdStr);
        });
    }
    processBuffer() {
        if (!this.pendingRequest)
            return;
        try {
            const { result, consumed } = this.parseAny(0);
            // Consumed must be > 0 if success
            this.buffer = this.buffer.subarray(consumed);
            const { resolve } = this.pendingRequest;
            this.pendingRequest = null;
            if (typeof result === 'string') {
                resolve(result);
            }
            else {
                resolve(JSON.stringify(result));
            }
        }
        catch (e) {
            if (e.message === "Incomplete") {
                return;
            }
            if (this.pendingRequest) {
                const { reject } = this.pendingRequest;
                this.pendingRequest = null;
                reject(e);
            }
        }
    }
    // Returns result and number of bytes consumed
    parseAny(offset) {
        if (offset >= this.buffer.length)
            throw new Error("Incomplete");
        const type = String.fromCharCode(this.buffer[offset]);
        const CRLF_BUF = Buffer.from('\r\n');
        const findCrlf = (start) => {
            const idx = this.buffer.indexOf(CRLF_BUF, start);
            return idx;
        };
        if (type === '+' || type === '-' || type === ':') {
            const idx = findCrlf(offset + 1);
            if (idx === -1)
                throw new Error("Incomplete");
            const content = this.buffer.toString('utf8', offset + 1, idx);
            const consumed = idx + 2 - offset; // +2 for CRLF
            if (type === '-')
                throw new Error(content);
            if (type === ':')
                return { result: parseInt(content), consumed };
            return { result: content, consumed };
        }
        else if (type === '$') {
            const idx = findCrlf(offset + 1);
            if (idx === -1)
                throw new Error("Incomplete");
            const lenStr = this.buffer.toString('utf8', offset + 1, idx);
            const len = parseInt(lenStr);
            if (isNaN(len))
                throw new Error("Invalid bulk string length");
            // Current pos: idx + 2
            const dataStart = idx + 2;
            if (len === -1) {
                return { result: null, consumed: dataStart - offset };
            }
            const contentEnd = dataStart + len;
            // Need content + CRLF
            if (this.buffer.length < contentEnd + 2)
                throw new Error("Incomplete");
            // verify CRLF
            if (this.buffer[contentEnd] !== 13 || this.buffer[contentEnd + 1] !== 10) {
                throw new Error("Protocol Error: Expected CRLF after bulk string");
            }
            const val = this.buffer.toString('utf8', dataStart, contentEnd);
            return { result: val, consumed: contentEnd + 2 - offset };
        }
        else if (type === '*') {
            const idx = findCrlf(offset + 1);
            if (idx === -1)
                throw new Error("Incomplete");
            const countStr = this.buffer.toString('utf8', offset + 1, idx);
            const count = parseInt(countStr);
            if (isNaN(count))
                throw new Error("Invalid array length");
            let currentOffset = idx + 2; // header consumed
            if (count === -1) {
                return { result: null, consumed: currentOffset - offset };
            }
            const arr = [];
            for (let i = 0; i < count; i++) {
                const res = this.parseAny(currentOffset);
                arr.push(res.result);
                currentOffset += res.consumed;
            }
            return { result: arr, consumed: currentOffset - offset };
        }
        throw new Error("Unknown RESP type: " + type + " at offset " + offset);
    }
}
//# sourceMappingURL=extension.js.map