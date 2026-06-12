# Bridge.AI Developer Agent Instructions

Welcome to the Bridge.AI evaluation platform repository. This document outlines the overall architecture, context, and deployment targets to help AI agents (like yourself) navigate and modify this monorepo effectively.

## 1. Monorepo Architecture

This project is structured as a full-stack monorepo:

### Backend (`/backend`)
- **Technology Stack:** Java 21, Spring Boot 3.3.x, Maven
- **Core Dependencies:**
  - Spring Web, Spring Data JPA, Spring Data Redis, Spring Websocket
  - Spring Kafka (for event-driven communication)
  - PostgreSQL (persistent storage via JDBC)
  - Redis (high-speed active session caching)
  - Azure OpenAI SDK (\`com.azure:azure-ai-openai\`) for endpoint routing
- **Event Flow:** The service acts as both a producer and consumer for the \`code_evaluation_telemetry\` Kafka topic.
- **Run Locally:** Ensure PostgreSQL, Redis, and Kafka are running, then execute \`./mvnw spring-boot:run\` from the \`/backend\` directory.

### Frontend (`/frontend`)
- **Technology Stack:** React 19, TypeScript, Vite
- **UI Architecture:** Dark-themed, side-by-side split-pane layout.
  - **Left Pane:** Displays structured API documentation via \`react-markdown\`.
  - **Right Pane:** Incorporates a live code editor (\`@monaco-editor/react\`) and a telemetry terminal (\`@xterm/xterm\`, \`@xterm/addon-fit\`). The terminal consumes WebSocket telemetry logs emitted by the backend.
- **Run Locally:** Execute \`npm install\` followed by \`npm run dev\` from the \`/frontend\` directory.

## 2. Kafka Event Flows
The primary topic is \`code_evaluation_telemetry\`.
- **Producer:** The backend service publishes code evaluation statuses, standard out logs, and agent routing telemetry to this topic.
- **Consumer:** The backend simultaneously listens to this topic and pipes the consumed telemetry to connected frontend clients via WebSockets, creating a real-time log terminal effect in the browser.

## 3. CI/CD and Deployment
The repository includes a GitHub Actions workflow located at \`.github/workflows/main.yml\`.
- **Backend Deployment:** Built with Maven and deployed to **Azure Container Apps**.
- **Frontend Deployment:** Built with Vite (\`npm run build\`) and deployed to **Azure Static Web Apps**.

When making changes, ensure that both backend tests (via \`./mvnw test\`) and frontend builds succeed, and adhere to the architectural styles defined above.

## 4. End-to-End Sequence Diagram

The following sequence diagram details the full path from code submission to AI evaluation and UI update.

```mermaid
sequenceDiagram
  participant FE as Frontend App.tsx
  participant WSH as TelemetryWebSocketHandler
  participant K as Kafka
  participant CES as CodeExecutionService
  participant AIS as AiOrchestrationService
  participant PG as PostgreSQL
  participant R as Redis

  FE->>WSH: WebSocket connect at /ws/telemetry
  FE->>WSH: Send code submission payload
  WSH->>K: Publish topic code-submissions key=sessionId
  K->>CES: Deliver code-submissions message
  CES->>FE: Status update Compiling
  CES->>FE: Status update Running Test Cases
  CES->>FE: Status update Calculating Latency
  CES->>K: Publish topic code_evaluation_telemetry with metrics
  K->>AIS: Deliver code_evaluation_telemetry message
  AIS->>PG: Save UserProfile summary
  AIS->>R: Set status user as COMPLETED
  AIS->>FE: Send COMPLETED plus four line summary
```

## 5. Frontend High-Level Design (HLD)

The following flowchart details the initialization and behavior of the main `App.tsx` component.

```mermaid
flowchart TD
    A["App component mount"] --> B["Initialize refs and code state"]
    B --> C["Render UI layout"]

    C --> D{"useEffect runs and terminalRef exists?"}
    D -->|Yes| E["Create Terminal and FitAddon"]
    E --> F["Open terminal and fit to container"]
    F --> G["Write startup messages"]
    G --> H["Create WebSocket to ws://localhost:8080/ws/telemetry"]

    H --> I{"WebSocket event handlers"}
    I --> I1["onopen: write connected message"]
    I --> I2["onmessage: write telemetry data"]
    I --> I3["onerror: write connection error"]
    I --> I4["onclose: write connection closed"]

    I1 --> J["Add window resize listener"]
    I2 --> J
    I3 --> J
    I4 --> J

    J --> K["Cleanup on unmount: remove listener, close WebSocket, dispose terminal"]
    D -->|No| L["Skip terminal initialization"]

    C --> M["Markdown panel renders API docs"]
    C --> N["Editor panel renders code and updates code state on change"]
    C --> O["Run/Submit button triggers handleRunSubmit()"]

    O --> P{"WebSocket connected?"}
    P -->|Yes| Q["Write submitting message to terminal"]
    Q --> R["Build payload: user_id, challenge_id, code_body"]
    R --> S["Send JSON payload over WebSocket"]
    P -->|No| T["Write 'WebSocket is not connected' to terminal"]
```
