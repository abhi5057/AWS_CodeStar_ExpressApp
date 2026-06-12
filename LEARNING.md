# Full-Stack Learning Guide: Bridge.AI Evaluation Platform

Welcome! As a frontend developer, jumping into a full-stack, event-driven microservices architecture can feel overwhelming. This guide breaks down every component of the Bridge.AI platform, from the database up to the deployment infrastructure, explaining the "what" and "why" behind the choices we made.

---

## 1. Node & Frontend Infrastructure

You are likely already familiar with Node.js, but let's re-contextualize it within this monorepo.
- **What it is:** Node is the JavaScript runtime that powers your frontend tooling.
- **How it's used here:** We use Node to run **Vite** (our blazing-fast build tool) and **NPM** (to manage dependencies like React, TypeScript, Monaco Editor, and Xterm.js).
- **The Flow:** When you run `npm run dev`, Node spins up a local Vite server. It compiles your TypeScript to JavaScript and serves the React application. The frontend establishes a persistent **WebSocket** connection to the backend to listen for live updates.

---

## 2. Backend Framework (Java & Spring Boot)

- **What it is:** Java 21 is a robust, strictly-typed language. **Spring Boot** is a massive, highly-opinionated framework that makes building Java web applications incredibly fast by auto-configuring things like web servers (Tomcat) and database connections.
- **How it's used here:** The backend serves as the traffic cop. It doesn't render HTML; it only deals in data (JSON and WebSocket streams).
- **Key Concepts:**
  - **Controllers/Handlers:** In our app, `TelemetryWebSocketHandler` acts as the entry point. It receives a message from the frontend and decides what to do with it.
  - **Services:** Where the business logic lives (e.g., `CodeExecutionService`, `AiOrchestrationService`).
  - **Event-Driven:** Instead of processing code synchronously (which would block the server and cause timeouts), the backend immediately replies "Got it!" and passes the work to a background queue (Kafka).

---

## 3. Database (PostgreSQL)

- **What it is:** PostgreSQL is a powerful, open-source relational database. Data is stored in strict tables with rows and columns.
- **How it's used here:** We use it to permanently store user profile data and their AI-generated portfolio summaries.
- **JPA & Hibernate:** You don't write raw SQL strings in Spring Boot. You use **JPA (Java Persistence API)**. You define a Java Class (like `UserProfile`), and Hibernate (the engine behind JPA) automatically creates the corresponding SQL table and handles the `INSERT` and `SELECT` queries for you.

---

## 4. Cache (Redis)

- **What it is:** Redis is an in-memory data structure store. Because it runs in RAM, it is exponentially faster than a disk-based database like PostgreSQL.
- **How it's used here:** We use it to store fast, ephemeral data. Specifically, we cache the "status" of a user's code execution (e.g., "COMPLETED").
- **Why?** If the frontend reconnects and asks "Is my code done?", hitting PostgreSQL every time is slow and expensive. Hitting Redis takes microseconds.

---

## 5. Message Queuing (Kafka)

- **What it is:** Apache Kafka is an event streaming platform. Think of it as a highly durable, highly scalable message board.
- **How it's used here:**
  1. The frontend sends code. The WebSocket handler publishes a message to the `code-submissions` **Topic** (a specific category on the message board).
  2. The `CodeExecutionService` is a **Consumer** listening to that topic. It picks up the message, simulates execution, and publishes the results to a new topic: `code_evaluation_telemetry`.
  3. The `AiOrchestrationService` listens to the telemetry topic, calls the AI, and saves the result.
- **Why?** Decoupling. If the AI service goes down, the `code-submissions` keep piling up safely in Kafka. When the AI service comes back online, it just picks up where it left off. No data is lost.

---

## 6. Environment & Infrastructure (Docker)

- **What it is:** Docker packages software into standardized units called **Containers**. A container has everything the software needs to run (libraries, system tools, code), ensuring it runs the exact same way on your laptop as it does in the cloud.
- **Docker Compose:** The `docker-compose.yml` file is a recipe. Instead of you manually installing Postgres, Redis, Kafka, and Zookeeper on your Mac/Windows, you run `docker compose up -d`. Docker reads the recipe, downloads the official images, wires their networks together, and injects **Environment Variables** (like `POSTGRES_USER=postgres`) so they can talk to each other.

---

## 7. Cloud Services (AWS vs. Azure)

While this specific repo's GitHub Actions (`main.yml`) deploys to **Azure** (Azure Container Apps for backend, Azure Static Web Apps for frontend), here is how this architecture translates directly to **AWS Services**, which are the industry standard:

| Concept | Azure (Current Repo) | AWS Equivalent |
| :--- | :--- | :--- |
| **Frontend Hosting** | Azure Static Web Apps | **AWS Amplify** or **S3 + CloudFront** |
| **Backend Container** | Azure Container Apps | **AWS Fargate** (Serverless ECS) or **EKS** (Kubernetes) |
| **Database** | Azure Database for PostgreSQL | **Amazon RDS for PostgreSQL** or **Aurora** |
| **Cache** | Azure Cache for Redis | **Amazon ElastiCache** |
| **Message Queue** | Azure Event Hubs (Kafka compat) | **Amazon MSK** (Managed Streaming for Kafka) |
| **AI Routing** | Azure OpenAI | **Amazon Bedrock** (to route to Claude/Mistral/etc.) |

---

## 8. Dashboards for Monitoring Logs & Status

As a frontend developer, you might want to build a dashboard to monitor system health. How is this done?
- **The Telemetry Approach:** You can build a React dashboard that connects to the same WebSocket (`/ws/telemetry`) to visualize the stream of logs and metrics in real-time.
- **The Industry Standard (ELK / Grafana):** In production, backends don't just print to the console. They send structured JSON logs to a centralized logging system like **Elasticsearch, Logstash, and Kibana (ELK)**, or **Datadog**. You then use Kibana or **Grafana** to build visual dashboards showing error rates, CPU usage, and Kafka lag.

---

## 9. Health Checks

- **What they are:** Automated pings to ensure a service is actually functioning, not just powered on.
- **How it's used here:** Look at our `docker-compose.yml`. The postgres service has a `healthcheck` that runs `pg_isready`. Docker won't mark the database as "healthy" until that command succeeds.
- **In Spring Boot:** Production Spring Boot apps use **Actuator**, exposing an endpoint at `/actuator/health`. Load balancers (like AWS ALB) constantly hit this endpoint. If it returns 500, the load balancer stops sending traffic to that specific pod.

---

## 10. Ways to Scale Up Pods Based on Traffic

When you deploy a container (often called a "Pod" in Kubernetes), you start with one instance. If 10,000 users log on, that one pod will crash.
- **Horizontal Scaling:** You spin up *more* identical pods.
- **How it works:**
  - **CPU/Memory Metrics (HPA):** A Kubernetes Horizontal Pod Autoscaler (HPA) watches CPU usage. If CPU > 80%, it automatically boots up 5 more pods.
  - **Event-Driven Scaling (KEDA):** This is crucial for our Kafka architecture. If 50,000 code submissions hit Kafka, CPU might be low, but the queue is huge. Tools like KEDA watch the Kafka topic depth. If the queue > 1000 messages, it scales the `CodeExecutionService` pods from 1 to 50 to chew through the backlog, then scales back down to 1 when the queue is empty.

---

## 11. Monitoring Traffic and "Clearing Things Up" (Data Lifecycle)

Over time, databases, caches, and queues get full. "Clearing things up" is automated data lifecycle management.
- **Redis TTL (Time To Live):** When we save the "COMPLETED" status to Redis, we should set a TTL (e.g., 24 hours). After 24 hours, Redis deletes it automatically. This prevents RAM from filling up.
- **Kafka Retention Policies:** Kafka isn't a permanent database. We configure topics to retain messages based on time (e.g., delete after 7 days) or size (delete oldest when topic hits 50GB). This is called Log Compaction or Retention.
- **Database Archiving:** For PostgreSQL, you wouldn't typically delete old data immediately. You would set up a cron job (or an AWS EventBridge rule) to archive data older than 1 year to cheap, cold storage (like AWS S3 Glacier) and then delete it from the hot Postgres database to keep queries fast.
