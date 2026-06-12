# Bridge.AI Runbook (DevOps and On-Call)

This runbook is for bringing the complete repository up, checking live status, troubleshooting, stopping, and restarting all runtime parts:
- Frontend (React + Vite)
- Backend (Spring Boot)
- Infrastructure services (Postgres, Redis, ZooKeeper, Kafka via Docker Compose)
- Local Kubernetes context (Docker Desktop)

## 1. Architecture Overview

```mermaid
flowchart LR
  subgraph UserSide[User Interaction]
    Browser[Browser at localhost:5173]
  end

  subgraph Frontend[frontend React plus Vite]
    App[App.tsx]
    Monaco[Monaco Editor]
    Xterm[xterm Telemetry Terminal]
    WSClient[WebSocket Client ws://localhost:8080/ws/telemetry]
    App --> Monaco
    App --> Xterm
    App --> WSClient
  end

  subgraph Backend[backend Spring Boot]
    WSCfg[WebSocketConfig]
    WSH[TelemetryWebSocketHandler]
    CodeSvc[CodeExecutionService]
    AISvc[AiOrchestrationService]
    JPARepo[UserProfileRepository]
    RedisTemplate[StringRedisTemplate]
  end

  subgraph Infra[Runtime Infrastructure]
    Kafka[(Kafka)]
    Zoo[(ZooKeeper)]
    Redis[(Redis)]
    PGDocker[(Postgres in Docker)]
    PGWindows[(Windows Postgres service)]
  end

  Browser --> Frontend
  Frontend -->|WebSocket JSON submit| WSH
  WSCfg --> WSH

  WSH -->|publish code-submissions| Kafka
  Kafka -->|consume code-submissions| CodeSvc
  CodeSvc -->|publish code_evaluation_telemetry| Kafka
  Kafka -->|consume code_evaluation_telemetry| AISvc

  CodeSvc -->|send progress| WSH
  AISvc -->|send COMPLETED and summary| WSH
  WSH -->|WebSocket telemetry stream| WSClient

  AISvc --> JPARepo
  AISvc --> RedisTemplate
  JPARepo --> PGWindows
  RedisTemplate --> Redis

  Kafka --> Zoo
  PGDocker -. optional and currently parallel .- Backend
```

## 2. Frontend Flow

```mermaid
flowchart TD
  Start[App mounts] --> Init[Initialize refs and code state]
  Init --> Effect[useEffect executes]
  Effect --> Check{terminalRef exists and terminal not initialized}
  Check -- Yes --> Term[Create xterm Terminal plus FitAddon]
  Term --> Open[Open terminal in DOM and fit]
  Open --> WS[Connect WebSocket to ws://localhost:8080/ws/telemetry]
  WS --> Handlers[Register onopen onmessage onerror onclose handlers]
  Handlers --> Resize[Attach window resize listener]
  Resize --> Ready[UI ready for code submission]
  Check -- No --> Ready

  Ready --> Click[User clicks Run Submit]
  Click --> Conn{WebSocket OPEN}
  Conn -- Yes --> Payload[Build payload user_id challenge_id code_body]
  Payload --> Send[Send JSON payload over WebSocket]
  Send --> Stream[Receive telemetry messages and print to xterm]
  Conn -- No --> Warn[Print websocket not connected]

  Stream --> Cleanup[On unmount remove listener close WS dispose terminal]
  Warn --> Cleanup
```

## 3. Backend Event Flow

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

## 4. Infra Services Topology (Docker + Host)

```mermaid
flowchart LR
  subgraph DockerDesktop[Docker Desktop]
    direction TB
    ZooKeeper[bridgeai_zookeeper on 2181]
    Kafka[bridgeai_kafka on 9092]
    Redis[bridgeai_redis on 6379]
    PostgresContainer[bridgeai_postgres on 5432]
    Kafka --> ZooKeeper
  end

  subgraph HostWindows[Windows Host Services]
    HostPostgres[Native Postgres service on 5432]
    Backend[Spring Boot backend on 8080]
    Frontend[Vite frontend on 5173]
  end

  Browser[Browser client] --> Frontend
  Frontend --> Backend
  Backend --> Kafka
  Backend --> Redis
  Backend --> HostPostgres

  PostgresContainer -. port overlap risk on 5432 .- HostPostgres
  Backend -. optional configuration can target .-> PostgresContainer
```

## 5. What Each Infra Service Does

- Postgres:
  - Primary persistent relational database for JPA entities (UserProfile).
  - Configured in backend datasource at localhost:5432.
- Redis:
  - Fast key-value store for runtime status caching.
  - Used by backend StringRedisTemplate.
- Kafka:
  - Event backbone between WebSocket submissions, execution service, and AI orchestration service.
  - Topics in this app: code-submissions, code_evaluation_telemetry.
- ZooKeeper:
  - Kafka coordination service for broker metadata/leader coordination in this compose setup.

## 6. Prerequisites and Versions

Required runtime baseline:
- Java 21 (backend build is release 21)
- Node 22.12+ (Node 24 LTS is fine)
- Docker Desktop running
- kubectl optional (for k8s checks)

Local versions validated during setup:
- Docker: 28.3.2
- Docker Compose: 2.39.1
- Java runtime used to run backend: Temurin 21.0.11
- Node runtime used for frontend: 24.16.0
- kubectl context: docker-desktop

## 7. Bring Everything Up (Golden Path)

Run from repo root:

~~~bash
cd /d/sample/AWS_CodeStar_ExpressApp
~~~

### 7.1 Start infra services

~~~bash
docker compose up -d
docker compose ps
~~~

### 7.2 Start backend (explicit Java 21)

~~~bash
cd backend
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot" PATH="$JAVA_HOME/bin:$PATH" ./mvnw spring-boot:run
~~~

Expected success signal in logs:
- Started EvaluationApplication

### 7.3 Start frontend

Open a second terminal:

~~~bash
cd /d/sample/AWS_CodeStar_ExpressApp/frontend
npm install
npm run dev -- --host 0.0.0.0 --port 5173
~~~

Expected success signal in logs:
- VITE ready
- Local URL shows http://localhost:5173/

## 8. Live Status and Health Commands

### 8.1 Infra status

~~~bash
cd /d/sample/AWS_CodeStar_ExpressApp
docker compose ps
docker compose logs --tail=100 kafka
docker compose logs --tail=100 redis
docker compose logs --tail=100 postgres
docker compose logs --tail=100 zookeeper
~~~

### 8.2 App status

~~~bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:5173/
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/ws/telemetry
~~~

Interpretation:
- Frontend URL should return 200.
- WebSocket endpoint over plain HTTP should return 400 (this still means backend is up and endpoint exists).

### 8.3 Kafka topic quick checks

~~~bash
docker exec -it bridgeai_kafka kafka-topics --bootstrap-server localhost:9092 --list
~~~

### 8.4 Database existence check for evaluation_db on host Postgres

~~~bash
docker run --rm postgres:15 psql "postgresql://postgres:postgres@host.docker.internal:5432/postgres" -c "SELECT datname FROM pg_database WHERE datname='evaluation_db';"
~~~

## 9. Stop, Kill, Restart

### 9.1 Stop frontend/backend

- In each app terminal, use Ctrl+C.

### 9.2 Stop infra

~~~bash
cd /d/sample/AWS_CodeStar_ExpressApp
docker compose down
~~~

### 9.3 Restart infra

~~~bash
cd /d/sample/AWS_CodeStar_ExpressApp
docker compose restart
~~~

Or restart individual service:

~~~bash
docker compose restart kafka
docker compose restart redis
docker compose restart postgres
docker compose restart zookeeper
~~~

### 9.4 Full clean restart

~~~bash
cd /d/sample/AWS_CodeStar_ExpressApp
docker compose down
docker compose up -d
~~~

Then start backend and frontend again (Section 7.2 and 7.3).

## 10. Kubernetes (Local Docker Desktop)

Current local cluster checks:

~~~bash
kubectl config current-context
kubectl cluster-info
kubectl get nodes
~~~

Project namespace bootstrap:

~~~bash
kubectl create namespace bridgeai --dry-run=client -o yaml | kubectl apply -f -
kubectl get ns bridgeai
~~~

Note:
- This repo currently runs directly via docker compose plus local processes.
- Kubernetes manifests are not yet added in this repo.

## 11. Known Important Gotcha (Port 5432 overlap)

Both of these can exist on the same machine:
- Docker Postgres published on host port 5432
- Native Windows Postgres also on host port 5432

In this environment, backend resolved to the Windows Postgres on localhost:5432, where evaluation_db had to be created manually.

If backend fails with "database evaluation_db does not exist", run:

~~~bash
docker run --rm postgres:15 psql "postgresql://postgres:postgres@host.docker.internal:5432/postgres" -c "CREATE DATABASE evaluation_db;"
~~~

For deterministic behavior, recommended options:
- Keep Windows Postgres and remove Docker Postgres from compose.
- Or remap Docker Postgres to 5433 and update backend datasource URL to localhost:5433.

## 12. Source Map (Key Files)

- Infra compose: docker-compose.yml
- Backend config: backend/src/main/resources/application.yml
- Backend websocket wiring: backend/src/main/java/com/bridgeai/evaluation/config/WebSocketConfig.java
- Backend websocket handler: backend/src/main/java/com/bridgeai/evaluation/websocket/TelemetryWebSocketHandler.java
- Backend execution service: backend/src/main/java/com/bridgeai/evaluation/kafka/CodeExecutionService.java
- Backend orchestration service: backend/src/main/java/com/bridgeai/evaluation/kafka/AiOrchestrationService.java
- Frontend app: frontend/src/App.tsx

## 13. Current Verified State (at runbook creation)

- Infra services were up and healthy in docker compose ps.
- Frontend returned HTTP 200 on localhost:5173.
- Backend WebSocket endpoint responded on localhost:8080/ws/telemetry (HTTP 400 expected for WS endpoint health probe).
- Kubernetes context docker-desktop was active and namespace bridgeai existed.
