# Comprehensive 7 YoE Python Backend Developer Interview Guide

This guide covers an exhaustive list of questions and solutions expected for a Senior Python Backend Developer with ~7 years of experience. At this level, candidates are expected to understand not just how to write code, but how Python works under the hood, how to design scalable systems, and how to deploy and maintain them in production.

---

## 1. Python Core & Advanced

### Q: How does Python manage memory? Explain Garbage Collection and Reference Counting.
**Solution:**
Python primarily uses **Reference Counting** for memory management. Every object in Python has a reference count, which is incremented when a reference to it is created and decremented when a reference is deleted or goes out of scope. When the reference count reaches zero, the memory is deallocated.
However, reference counting cannot handle **reference cycles** (e.g., Object A references Object B, and Object B references Object A). To handle this, Python includes a **Generational Garbage Collector** (GC). The GC periodically scans objects and looks for unreachable circular references. It divides objects into three generations, where newly created objects start in the first generation. Objects that survive garbage collection are promoted to older generations, which are scanned less frequently.

### Q: What is the Global Interpreter Lock (GIL)? How does it affect multi-threading in Python, and how do you bypass it?
**Solution:**
The GIL is a mutex that protects access to Python objects, preventing multiple native threads from executing Python bytecodes at once in CPython. This means CPU-bound multi-threading in Python does not achieve true parallelism.
- **I/O Bound tasks:** Threads are useful because the GIL is released during I/O operations (network requests, file reading).
- **CPU Bound tasks:** Threads will not give a performance boost. To bypass the GIL for CPU-bound tasks, we use the `multiprocessing` module, which spawns separate OS processes, each with its own Python interpreter and memory space, effectively bypassing the GIL. Alternatively, extensions written in C (like NumPy) can release the GIL during intense computations.

### Q: Explain `asyncio`. How does the event loop work in Python?
**Solution:**
`asyncio` is a library to write concurrent code using the `async`/`await` syntax, based on an **event loop**. The event loop is the core of asynchronous programming; it runs asynchronous tasks and callbacks, performs network IO operations, and runs subprocesses.
Unlike multi-threading (preemptive multitasking), `asyncio` uses cooperative multitasking. A coroutine voluntarily yields control back to the event loop using `await` when waiting for an I/O operation, allowing the event loop to run other coroutines. This is highly efficient for handling thousands of concurrent I/O-bound connections (e.g., in web servers like FastAPI).

### Q: What are Metaclasses in Python? When would you use them?
**Solution:**
In Python, classes are objects too. Just as a class defines the behavior of an instance, a **metaclass** defines the behavior of a class. The default metaclass is `type`.
You would use a metaclass to intercept class creation, modify class attributes, or enforce certain patterns across multiple classes.
**Example usage:** Django's ORM uses metaclasses to parse model fields and dynamically create database schemas.
```python
class SingletonMeta(type):
    _instances = {}
    def __call__(cls, *args, **kwargs):
        if cls not in cls._instances:
            cls._instances[cls] = super().__call__(*args, **kwargs)
        return cls._instances[cls]

class Database(metaclass=SingletonMeta):
    pass
```

### Q: How do context managers work? Write a custom context manager.
**Solution:**
Context managers are used to manage resources, ensuring they are properly acquired and released (e.g., opening files, database connections). They are implemented using `__enter__` and `__exit__` dunder methods, or the `@contextlib.contextmanager` decorator.
```python
class Timer:
    import time
    def __enter__(self):
        self.start = self.time.time()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.end = self.time.time()
        print(f"Time taken: {self.end - self.start}s")

with Timer():
    sum(range(1000000))
```

---

## 2. Data Structures & Algorithms (DSA)

### Q: Implement an LRU (Least Recently Used) Cache.
**Solution:**
An LRU Cache can be efficiently implemented using a Hash Map and a Doubly Linked List, giving O(1) time complexity for both `get` and `put`. In Python, `collections.OrderedDict` internally uses a doubly linked list and a hash map, making it perfect for this.
```python
from collections import OrderedDict

class LRUCache:
    def __init__(self, capacity: int):
        self.cache = OrderedDict()
        self.capacity = capacity

    def get(self, key: int) -> int:
        if key not in self.cache:
            return -1
        self.cache.move_to_end(key)
        return self.cache[key]

    def put(self, key: int, value: int) -> None:
        if key in self.cache:
            self.cache.move_to_end(key)
        self.cache[key] = value
        if len(self.cache) > self.capacity:
            self.cache.popitem(last=False)
```

### Q: How do you detect a cycle in a Directed Graph?
**Solution:**
We can use Depth First Search (DFS) with a recursion stack (or coloring technique: 0=unvisited, 1=visiting, 2=visited).
```python
def is_cyclic(V, adj):
    visited = [False] * V
    rec_stack = [False] * V

    def dfs(node):
        visited[node] = True
        rec_stack[node] = True
        for neighbor in adj[node]:
            if not visited[neighbor]:
                if dfs(neighbor):
                    return True
            elif rec_stack[neighbor]:
                return True
        rec_stack[node] = False
        return False

    for i in range(V):
        if not visited[i]:
            if dfs(i):
                return True
    return False
```

---

## 3. Web Frameworks: Django, Flask, FastAPI

### Q: Django: Explain the request-response cycle and the role of Middleware.
**Solution:**
1. Request comes in from a web server (e.g., Gunicorn/WSGI).
2. Request passes through the **Middleware** stack (top-down). Middleware can modify the request, handle sessions, authentication, or even return a response early.
3. URL Router resolves the URL to a specific **View**.
4. The View handles the business logic, interacting with Models (ORM) if necessary.
5. View returns a response (or renders a Template).
6. Response passes back up through the **Middleware** stack (bottom-up).
7. Server sends the response to the client.

### Q: Django: What is the N+1 query problem in Django ORM and how do you solve it?
**Solution:**
The N+1 problem occurs when a framework executes 1 query to fetch a list of N objects, and then executes N additional queries to fetch related data for each object.
**Solution:** Use `select_related()` for foreign key/one-to-one relationships (it does a SQL JOIN) and `prefetch_related()` for many-to-many/reverse foreign key relationships (it executes a separate query and joins them in Python).

### Q: Flask: What is the Application Context and Request Context in Flask?
**Solution:**
Flask uses thread-local objects to handle global state.
- **Application Context (`current_app`, `g`):** Keeps track of application-level data during a request or CLI command.
- **Request Context (`request`, `session`):** Keeps track of request-level data (headers, query params).
When a request comes in, Flask pushes a request context (which also pushes an app context if needed), allowing views to access `request` globally without passing it as an argument.

### Q: FastAPI: Why is FastAPI faster than traditional frameworks?
**Solution:**
1. Built on **Starlette** (for ASGI) and **Pydantic** (for data validation).
2. It uses `asyncio` heavily, allowing it to handle concurrent connections efficiently (unlike synchronous WSGI frameworks).
3. It minimizes data serialization overhead by using Pydantic's optimized core (written in Rust in V2).

---

## 4. Pydantic

### Q: What role does Pydantic play in modern Python backends? Explain with an example.
**Solution:**
Pydantic is a data validation and settings management library using Python type annotations. It enforces type hints at runtime and provides user-friendly errors when data is invalid.
```python
from pydantic import BaseModel, EmailStr, Field

class User(BaseModel):
    id: int
    name: str
    email: EmailStr
    age: int = Field(gt=18, description="User must be strictly over 18")

# Validates and parses data
user = User(id="123", name="Alice", email="alice@example.com", age=25)
print(type(user.id)) # Int (parsed from string)
```

---

## 5. NumPy & AI/ML Basics

### Q: How does NumPy achieve such high performance compared to native Python lists?
**Solution:**
1. **Contiguous Memory:** NumPy arrays are stored in contiguous blocks of memory, allowing efficient CPU cache utilization.
2. **C Implementation:** Vectorized operations are executed in highly optimized C code, avoiding the overhead of Python loops and type checking.
3. **Static Typing:** Arrays hold elements of a single C data type, avoiding Python's object overhead (reference counts, type pointers).

### Q: What is the typical architecture for serving a Machine Learning model in a Python backend?
**Solution:**
Since ML models are often CPU/GPU intensive and synchronous, they shouldn't block the main async web server (like FastAPI).
**Architecture:**
1. Fast REST/gRPC API (FastAPI) receives the prediction request.
2. The API sends the request payload to a message queue (e.g., Celery + Redis / RabbitMQ / Kafka).
3. A pool of worker processes (which preload the heavy ML model into memory) consumes from the queue, performs inference (using PyTorch/TensorFlow ONNX runtime), and stores the result in a cache or DB.
4. The API either polls for the result or uses WebSockets to notify the client.
Alternatively, use specialized model servers like **Triton Inference Server** or **TensorFlow Serving** and have the Python backend communicate with them via gRPC.

---

## 6. PostgreSQL & Databases

### Q: How do you optimize a slow query in PostgreSQL?
**Solution:**
1. Use `EXPLAIN ANALYZE` to read the query execution plan (look for Sequential Scans instead of Index Scans).
2. Create appropriate **Indexes** (B-Tree for equality/range, GIN for JSONB/full-text search).
3. Avoid `SELECT *`, fetch only needed columns.
4. Check for proper **Pagination** (Keyset pagination vs Offset pagination, as large offsets are slow).
5. Analyze database locks if there are concurrency issues.
6. Tune `work_mem` for heavy sorting/joins.

### Q: What is MVCC in PostgreSQL?
**Solution:**
Multi-Version Concurrency Control (MVCC) allows PostgreSQL to handle concurrent reads and writes without locking. Every transaction sees a snapshot of the data. When a row is updated, a new version of the row is created (the old one remains for existing transactions). `VACUUM` processes are later used to clean up dead tuples (old versions).

### Q: Explain Database Connection Pooling and why it's critical for Python backends.
**Solution:**
Opening a new database connection is expensive (TCP handshake, Postgres process fork). Python web servers often use many worker processes (Gunicorn) or async event loops. Without pooling, the DB would be overwhelmed with connection attempts. We use tools like **PgBouncer** or connection pooling libraries (SQLAlchemy QueuePool) to maintain a pool of persistent connections that are reused across requests.

---

## 7. Containerization (Docker) & Orchestration (Kubernetes)

### Q: Write a production-ready Dockerfile for a Python web application.
**Solution:**
A production Dockerfile should use multi-stage builds, run as a non-root user, and optimize caching.
```dockerfile
# Stage 1: Builder
FROM python:3.11-slim as builder
WORKDIR /app
RUN apt-get update && apt-get install -y gcc libpq-dev
COPY requirements.txt .
RUN pip wheel --no-cache-dir --no-deps --wheel-dir /app/wheels -r requirements.txt

# Stage 2: Runner
FROM python:3.11-slim
WORKDIR /app
# Install runtime dependencies (e.g., pg client)
RUN apt-get update && apt-get install -y libpq5 && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/wheels /wheels
RUN pip install --no-cache /wheels/*
COPY . .
# Create non-root user
RUN adduser --disabled-password myuser
USER myuser
EXPOSE 8000
CMD ["gunicorn", "-k", "uvicorn.workers.UvicornWorker", "-c", "gunicorn_conf.py", "main:app"]
```

### Q: Explain the core components of Kubernetes. How do you deploy a Python app on K8s?
**Solution:**
- **Pod:** Smallest unit, runs your Docker container.
- **Deployment:** Manages replica sets of Pods, handles rolling updates and scaling.
- **Service:** Exposes your Pods to network traffic (ClusterIP, NodePort, LoadBalancer).
- **Ingress:** Manages external access (HTTP/HTTPS) to services, provides routing.
- **ConfigMap/Secret:** Injects environment variables and configuration into Pods.

To deploy: Create a Deployment YAML manifest for the Python app, a Service to expose it, and an Ingress to route traffic. Use HPA (Horizontal Pod Autoscaler) to scale Pods based on CPU or custom metrics.

---

## 8. Deployment Pipelines (CI/CD)

### Q: Describe an ideal CI/CD pipeline for a Python microservices architecture.
**Solution:**
**Continuous Integration (CI):**
1. **Linting & Formatting:** `flake8`, `black`, `isort`.
2. **Type Checking:** `mypy`.
3. **Unit & Integration Tests:** Run `pytest` with a temporary Postgres database (using Docker services in CI).
4. **Security Scans:** `bandit` for AST security checks, `safety` for dependency vulnerabilities.
5. **Build:** Build Docker image.

**Continuous Deployment (CD):**
1. Push Docker image to a Container Registry (ECR, GCR).
2. Update Kubernetes manifests (or Helm charts) with the new image tag.
3. Deploy to Staging. Run E2E tests.
4. Deploy to Production (using strategies like Canary or Blue/Green deployment).

---

## 9. System Design & Architecture

### Q: Design a scalable URL Shortener (like Bitly) using Python stack.
**Solution:**
- **API Layer:** FastAPI (high throughput, async).
- **Database:** PostgreSQL for persistent mapping. Schema: `(id, short_url, long_url, created_at, user_id)`.
- **Key Generation:** Pre-generate base62 strings offline using a worker (Celery) and store them in Redis or a Zookeeper service (to avoid DB auto-increment bottlenecks and collisions).
- **Caching:** Redis. Before hitting Postgres, check Redis for the `short_url`. If not found, fetch from Postgres and cache it with a TTL.
- **Analytics:** Send click events to a message queue (Kafka), which are consumed by a separate analytical service (Python worker) and saved into an OLAP database (ClickHouse) for dashboards.
- **Scaling:** Deploy FastAPI behind an AWS ALB across multiple AZs. Use Postgres read-replicas for heavy read traffic.

### Q: How do you handle distributed transactions across microservices?
**Solution:**
ACID transactions are hard across microservices (Two-Phase Commit is slow and blocks). We use the **Saga Pattern**.
- **Choreography:** Each service publishes an event (to Kafka/RabbitMQ) after completing its local transaction. Other services listen and react.
- **Orchestration:** A central Orchestrator service manages the transaction lifecycle and commands other services.
If a step fails, the system executes **Compensating Transactions** to undo the previous steps (e.g., if inventory is deducted but payment fails, issue an event to restore inventory).
