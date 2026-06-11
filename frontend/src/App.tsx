import { useEffect, useRef, useState } from 'react';
import Markdown from 'react-markdown';
import Editor from '@monaco-editor/react';
import { Terminal } from '@xterm/xterm';
import { FitAddon } from '@xterm/addon-fit';
import '@xterm/xterm/css/xterm.css';
import './App.css';

const markdownContent = `
# Bridge.AI Evaluation Platform
## API Documentation
Welcome to the evaluation platform API documentation.

### Endpoints
- \`POST /api/evaluate\`: Submits code for evaluation.
- \`GET /api/status/{id}\`: Retrieves evaluation status.

### Example Request
\`\`\`json
{
  "code": "print('Hello, Bridge.AI!')",
  "language": "python"
}
\`\`\`
`;

function App() {
  const terminalRef = useRef<HTMLDivElement>(null);
  const xtermRef = useRef<Terminal | null>(null);
  const [code, setCode] = useState('// Write your code here\nconsole.log("Hello, Bridge.AI!");\n');

  useEffect(() => {
    if (terminalRef.current && !xtermRef.current) {
      const term = new Terminal({
        theme: {
          background: '#1e1e1e',
          foreground: '#cccccc',
          cursor: '#ffffff'
        }
      });
      const fitAddon = new FitAddon();
      term.loadAddon(fitAddon);
      term.open(terminalRef.current);
      fitAddon.fit();

      term.writeln('\x1b[1;32mBridge.AI Telemetry Terminal Ready\x1b[0m');
      term.writeln('Connecting to WebSocket telemetry stream...');

      xtermRef.current = term;

      // Mock WebSocket Connection for UI
      const ws = new WebSocket('ws://localhost:8080/telemetry');

      ws.onopen = () => {
        term.writeln('\x1b[32mConnected to backend.\x1b[0m');
      };

      ws.onmessage = (event) => {
        term.writeln(`\x1b[36m[Telemetry]\x1b[0m ${event.data}`);
      };

      ws.onerror = () => {
        term.writeln('\x1b[31mWebSocket connection error.\x1b[0m');
      };

      ws.onclose = () => {
        term.writeln('\x1b[33mWebSocket connection closed.\x1b[0m');
      };

      const handleResize = () => {
        fitAddon.fit();
      };

      window.addEventListener('resize', handleResize);

      return () => {
        window.removeEventListener('resize', handleResize);
        ws.close();
        term.dispose();
      };
    }
  }, []);

  return (
    <div className="app-container">
      <div className="left-pane">
        <Markdown>{markdownContent}</Markdown>
      </div>
      <div className="right-pane">
        <div className="editor-container">
          <Editor
            height="100%"
            defaultLanguage="javascript"
            theme="vs-dark"
            value={code}
            onChange={(value) => setCode(value || '')}
            options={{
              minimap: { enabled: false },
              fontSize: 14,
            }}
          />
        </div>
        <div className="terminal-container" ref={terminalRef}></div>
      </div>
    </div>
  );
}

export default App;
