<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"/>
  <img src="https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/MongoDB-4EA94B?style=for-the-badge&logo=mongodb&logoColor=white" alt="MongoDB"/>
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis"/>
  <img src="https://img.shields.io/badge/Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" alt="Kafka"/>
  <img src="https://img.shields.io/badge/RabbitMQ-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white" alt="RabbitMQ"/>
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker"/>
</p>

# 💰 Montrax - Money Manager Backend

> **Enterprise-Grade Personal Finance Management API** — A Spring Boot backend for expense tracking, AI-driven financial insights, and subscription-gated budget management, engineered to run reliably on a single low-memory container while staying horizontally scalable.

---

## 🌟 Overview

Montrax Backend is a RESTful API service built with **Spring Boot 4.0.2** and **Java 21**. It is a genuinely polyglot-persistence system: **PostgreSQL** as the system of record, **Redis** as the cache and shared-state store, **MongoDB** as an optional document store for AI history/audit/config, **Kafka** as an append-only event stream, and **RabbitMQ** as a retryable work queue. Every piece of infrastructure was chosen for a specific job rather than "because it's popular" — the sections below explain the *why*, not just the *what*.

### ✨ Key Highlights

- 🔐 **Stateless JWT Authentication** + Google OAuth2, layered behind a 5-filter security chain
- 🤖 **AI-Powered Financial Insights** via Google Gemini, with circuit breaker + client-side rate limiting
- 💳 **Razorpay Subscriptions** with a 7-day grace period and plan-based feature gating (AOP)
- 📨 **Two messaging systems, two different jobs** — Kafka for audit/event streaming, RabbitMQ for retryable background work
- 🧠 **Redis-backed caching** across 9 named caches, each with hand-picked TTLs and a self-healing error handler
- 🏦 **Bank Statement Import** (CSV/Excel) with merchant auto-categorization
- 🐳 **Docker-Ready**, tuned to run inside a 768MB container

---

## 🏗️ Architecture

```
                                   ┌────────────────────────────┐
                                   │   CLIENT (React/TS SPA)    │
                                   └──────────────┬─────────────┘
                                                  │ HTTPS
                                                  ▼
                        ┌─────────────────────────────────────────────────┐
                        │              SPRING BOOT APPLICATION            │
                        │                                                 │
                        │  Security Filter Chain (in order):              │
                        │   CORS → JwtRequestFilter → RateLimitFilter →   │
                        │   IdempotencyFilter → AuditFilter → Dispatcher  │
                        │                                                 │
                        │  Dispatcher → Controller → AOP guards           │
                        │   (@AdminOnly / @PremiumFeature) → Service      │
                        │   → Repository → GlobalExceptionHandler         │
                        └───┬──────────┬───────────┬────────────┬─────────┘
                            │          │           │            │
              ┌─────────────▼──┐ ┌─────▼─────┐ ┌───▼────┐ ┌─────▼──────────────┐
              │  PostgreSQL    │ │   Redis   │ │ MongoDB│ │  Kafka + RabbitMQ  │
              │  (Neon, JPA)   │ │  (cache + │ │(Atlas, │ │  (async backbone)  │
              │  System of     │ │  rate-    │ │optional│ │                    │
              │  record        │ │  limit +  │ │AI/audit│ │ Kafka: audit trail │
              │                │ │  idempo-  │ │/config)│ │ + txn events (fire │
              │                │ │  tency)   │ │        │ │ -and-forget)       │
              │                │ │           │ │        │ │ RabbitMQ: email /  │
              │                │ │           │ │        │ │ CSV / subscription │
              │                │ │           │ │        │ │ jobs (retry + DLQ) │
              └────────────────┘ └───────────┘ └────────┘ └────────────────────┘
                                                  ▲
                                     ┌────────────┴────────────┐
                                     │ External APIs: Gemini AI,│
                                     │ Razorpay, Mailjet SMTP,  │
                                     │ Cloudinary, TextBee SMS  │
                                     └──────────────────────────┘
```

Every external dependency (Mongo, Redis, Kafka, RabbitMQ) is a **soft dependency** — the app degrades gracefully instead of crashing if any of them is unreachable at boot. That design choice shows up repeatedly below (Mongo auto-config exclusion, Kafka listeners started post-boot, Redis cache errors caught and evicted, RabbitMQ producer failures swallowed).

---

## 📡 Request Lifecycle — What Actually Happens on a Request

Take `POST /api/expenses` as the running example.

1. **CORS preflight** is answered by Spring's CORS filter using an allow-list built from `money.manager.frontend.url` (read live from `AppCacheService`, so the frontend origin can change without a redeploy) plus a set of localhost/Vercel patterns for development.

2. **`JwtRequestFilter`** reads the `Authorization: Bearer <token>` header, extracts the email claim, and — if the token is valid and not already authenticated in this request — populates `SecurityContextHolder` with a `UsernamePasswordAuthenticationToken`. Expired/malformed/bad-signature tokens are logged and simply leave the request unauthenticated; Spring Security's `authorizeHttpRequests` rule then rejects it with 401/403 rather than the filter throwing directly. This keeps auth failure handling in one place.

3. **`RateLimitFilter`** runs next. ADMIN users are exempt. For everyone else it resolves the caller's subscription tier (cached in Redis for 10 minutes under `tier_cache:<email>` so it isn't a DB hit on every request) and atomically increments an hourly counter in Redis (`rate_limit:<email>:<yyyyMMddHH>`, `INCR` + `EXPIRE` on first hit). If the tier's hourly budget is exceeded, the request is short-circuited with `429` before it ever reaches a controller.

4. **`IdempotencyFilter`** only engages if the client sent an `X-Idempotency-Key` header (opt-in, used for POST/PUT that shouldn't double-fire on client retry — e.g. payment or expense creation over a flaky connection). It checks Redis for a cached response under `idempotency:<user>:<key>`; if present, the cached status/body is replayed verbatim and the handler never re-executes. Otherwise the response is captured via `ContentCachingResponseWrapper` and, on a 2xx outcome, stored in Redis for 24h.

5. **`AuditFilter`** wraps the rest of the chain in a timer. After the response is written, if the caller is authenticated, it publishes an `AuditEvent` (method, path, status, latency, IP, timestamp) to **Kafka** — fire-and-forget, so a slow or down Kafka broker never adds latency to the user-facing request.

6. **Controller → AOP guards → Service.** `ExpenceController` delegates to `ExpenceService`. If the endpoint were annotated `@PremiumFeature`, `PremiumFeatureAspect` would intercept first and return `403` before the method body runs at all if the caller's plan doesn't qualify. Admin endpoints get the same treatment twice over — once by the `SecurityConfig` path matcher (`hasRole("ADMIN")`) and again by the `@AdminOnly` / `AdminAccessAspect` — defense in depth for the one part of the API that can change other users' data.

7. **Service → Repository → PostgreSQL.** The expense is persisted via Spring Data JPA/Hibernate over a HikariCP connection pool (deliberately small — see [Load Handling](#-load-handling--capacity-limits) below).

8. **Side effects fire *after* the transaction, asynchronously:**
   - `TransactionEventPublisher` sends a `TransactionEvent` to the Kafka topic `moneymanager.transactions` for downstream analytics consumers — best-effort, wrapped in try/catch so a Kafka hiccup never fails the expense creation itself.
   - Any cache keyed on this user's data (`categories`, `analytics`, `dashboard`, etc.) is evicted so the next read is fresh.

9. **Errors**, if any occur anywhere in steps 6-8, are caught by `GlobalExceptionHandler`: known `RuntimeException`s become a structured `400` with the message; anything unexpected becomes a `500` with a generic message to the client while the full stack trace is logged server-side only — clients never see a stack trace.

The same shape (auth → rate limit → idempotency check → audit → AOP guard → service → async side effects) applies to every write endpoint in the API; reads skip the idempotency step since they're naturally safe to repeat.

---

## 🧠 Caching Strategy

Caching is split into two deliberately different mechanisms because they solve different problems:

### 1. Spring Cache abstraction, backed by Redis (`RedisConfig`)

Nine named caches, each with a TTL matched to how often its underlying data actually changes:

| Cache | TTL | Backing service | Why this TTL |
|---|---|---|---|
| `dashboard` | 5 min | Dashboard aggregation | Changes with every transaction; short TTL keeps it close to live |
| `subscription` | 1 hour | `SubscriptionService` | Checked on almost every request (rate limiter tier lookup); rarely changes |
| `categories` | 24 hours | `CategoryService` | User-defined, changes only on explicit CRUD (which evicts immediately) |
| `ai-insights` | 6 hours | AI insight generation | Expensive Gemini calls; stale-for-hours is an acceptable tradeoff |
| `monthly-summary` | 15 min | Monthly rollups | Moderate volatility |
| `financial-health` | 12 hours | AI-computed score | Expensive to compute, doesn't need to be fresh-to-the-minute |
| `analytics` | 10 min | `AnalyticsService` | Changes with every transaction, but tolerates a short lag |
| `predictions` | 6 hours | `SmartInsightsService` | Computed from historical trends, not real-time data |
| `anomalies` | 30 min | `SmartInsightsService` | Recent-data detection, moderate freshness need |
| `ai-tips` | 6 hours | Gemini-generated tips | Same rationale as ai-insights |

All nine share one `CacheErrorHandler`: if a cached value fails to deserialize (e.g. a stale shape left over from a DTO change), the error is logged **and the broken entry is evicted immediately** — so the *next* request self-heals and repopulates from the DB, instead of failing on every request for the rest of the TTL window. `@CacheEvict(allEntries = true)` on every mutating method keeps writes and reads consistent (cache-aside pattern).

### 2. Raw `RedisTemplate` for stateful, non-cache-abstraction use

- **Rate limiting** (`RateLimitFilter`) — hourly fixed-window counters per user.
- **Idempotency replay** (`IdempotencyFilter`) — full HTTP responses, 24h TTL.
- **OTP codes** (`OtpService`) — 5-minute TTL codes, 60-second resend cooldown, attempt counters.

These are correctness-critical shared state, not "nice to have faster" caches — which is exactly why they live in Redis instead of an in-process map: with Redis, all of this state is automatically consistent across every horizontally-scaled instance of the app.

### 3. `AppCacheService` — a *third*, separate in-memory cache for runtime config

This one isn't Spring Cache and isn't Redis. It's a `ConcurrentHashMap<String,String>` loaded from a MongoDB `app_config` collection at boot and refreshed every 30 minutes (`@Scheduled(fixedRate = 1800000)`), with a fallback to `application.properties`/env vars for any key not present in Mongo. It exists so an admin can change runtime config (API keys, feature toggles, the frontend URL used for CORS) from the admin panel (`/api/admin/config`) **without a redeploy** — the in-memory map is refreshed on demand via `/api/admin/cache/refresh` right after a save.

---

## 📨 Async & Messaging — Two Systems, Two Jobs

The app runs **both** Kafka and RabbitMQ, deliberately, because they solve different problems:

| | Kafka | RabbitMQ |
|---|---|---|
| **Used for** | Audit trail (`AuditEvent`), transaction events (`TransactionEvent`) | Email sending, CSV import processing, subscription events |
| **Delivery model** | Append-only log, fire-and-forget publish | Point-to-point work queue |
| **Failure handling** | Publish wrapped in try/catch — a failure is logged, never blocks the request | Consumer throws → Spring AMQP retries 3x → then dead-lettered to a `*.dlq` queue for inspection |
| **Why this tool for this job** | An audit/analytics stream doesn't need guaranteed processing of every message — losing one audit record to a broker outage is acceptable; blocking a user's request on it is not | An email or CSV import genuinely must not be silently dropped — retry + DLQ makes failures visible and recoverable |

**Kafka details:** topics (`moneymanager.transactions`, `moneymanager.audit`, `moneymanager.analytics`) are explicitly provisioned via `NewTopic` beans through `KafkaAdmin` at startup, rather than relying on broker auto-create (which many managed brokers disable by default). The listener container has `autoStartup=false`; `KafkaListenerStarter` starts it manually via an `ApplicationReadyEvent` listener, so a broker that's briefly unreachable — or a DNS hiccup resolving a managed broker's hostname — can't take the whole application down at boot. Producer sends use the async `KafkaTemplate.send()` (a `CompletableFuture`), never blocking the request thread.

**RabbitMQ details:** one topic exchange (`moneymanager.exchange`) routes to three durable queues (`email.queue`, `csv.queue`, `subscription.queue`), each configured with `x-dead-letter-exchange` pointing at a shared DLX. A message that fails all 3 retry attempts lands in its `.dlq` queue instead of vanishing — an operator can inspect and replay it. The connection factory is built directly from a single `rabbitmq.uri` (supports both plain `amqp://` for local/dev and `amqps://` with real TLS verification for a managed broker like CloudAMQP).

---

## 🔒 Security

### Filter chain (in registration order)

```
CORS → JwtRequestFilter → RateLimitFilter → IdempotencyFilter → AuditFilter → Controller
```

The order is intentional: authentication resolves first (so rate limiting and audit know *who* is calling), rate limiting runs before any expensive work, idempotency check happens before the handler executes (so a replay costs almost nothing), and audit logging runs last so the recorded status code and latency reflect what actually happened.

### Implementations

- **BCrypt** password hashing (never plaintext, never reversible)
- **Stateless JWT** (`SessionCreationPolicy.STATELESS`) — no server-side session state, which is what makes horizontal scaling trivial (any instance can serve any authenticated request)
- **Google OAuth2 login** as an alternative to password auth, with dedicated success/failure handlers
- **Tiered, Redis-backed rate limiting** (100/500/2000 requests-per-hour for FREE/BASIC/PREMIUM) — protects the shared instance from any single user's traffic burst, independent of infrastructure capacity
- **Idempotency-key deduplication** for POST/PUT — protects against duplicate side effects from client retries (e.g. a payment double-submitted after a timeout)
- **Two-layer admin authorization** — `SecurityConfig` path matcher (`hasRole("ADMIN")`) *and* the `@AdminOnly` AOP aspect on each admin controller method
- **Subscription-gated features** via `@PremiumFeature` + `PremiumFeatureAspect` — a 403 with an upgrade message, decided *before* the guarded method body executes
- **Centralized exception translation** (`GlobalExceptionHandler`) — clients get a clean JSON error, the server log gets the real stack trace
- **Non-root Docker user** (`appuser`, uid 1001) in the container image

---

## 🤖 AI Integration Resilience (Google Gemini)

Calling a third-party LLM API from a low-memory single instance needs guardrails, so `GeminiService` layers three of them:

1. **Client-side rate limiting** — a Guava `RateLimiter.create(0.167)` throttles outbound calls to roughly 1 every 6 seconds, matched to Gemini's free-tier RPM ceiling, so the app itself never gets rate-limited upstream.
2. **Circuit breaker** — after repeated consecutive failures, the circuit opens for 120 seconds; further calls fail fast with a clear "temporarily unavailable, retry in Ns" message instead of piling up slow, doomed HTTP calls.
3. **Retry with backoff** — up to 2 retries with a 5-second initial backoff for transient failures.

On top of that, every AI-derived result (`ai-insights`, `predictions`, `anomalies`, `ai-tips`, `financial-health`) is cached in Redis for hours, not minutes — the single most effective way to avoid hitting any of the above limits in the first place.

---

## 💳 Subscription Tiers & Feature Gating

| Plan | Price | Categories | AI Queries | CSV Imports | Data Retention | Rate Limit |
|---|---|---|---|---|---|---|
| **FREE** | ₹0 | 5 max | — | — | 3 months | 100 req/hr |
| **BASIC** | ₹99/mo | Unlimited | 5 | 3 | 12 months | 500 req/hr |
| **PREMIUM** | ₹299/mo | Unlimited | 50 | Unlimited | Unlimited | 2,000 req/hr |

Payments go through **Razorpay**: order creation → client-side checkout → signature-verified payment confirmation → webhook for out-of-band status updates (subscription renewal/failure). A **7-day grace period** (`GRACE_PERIOD` status) keeps premium features available briefly after a failed renewal before the plan actually downgrades, so a transient card decline doesn't instantly cut off a paying user.

---

## ⏰ Scheduled Jobs

| Job | Schedule | Purpose |
|---|---|---|
| `DataRetentionService.cleanupOldData` | `0 0 3 * * *` (3 AM IST) | Deletes expenses/incomes/bank transactions older than the caller's plan retention window; emails the user if >10 records were removed |
| `AccountDeletionScheduler.processScheduledDeletions` | `0 0 2 * * *` (2 AM) | Permanently deletes accounts past their 3-day deletion grace period, in FK-safe child-to-parent order |
| `AppCacheService.scheduledRefresh` | every 30 min | Reloads runtime config from MongoDB into the in-memory map |

All three run inside `@Transactional` boundaries where they mutate data, and log per-profile failures without aborting the whole batch — one broken profile doesn't stop the nightly job for everyone else.

---

## 📦 Tech Stack

| Category | Technology | Purpose |
|----------|------------|---------|
| **Framework** | Spring Boot 4.0.2 | Application framework |
| **Language** | Java 21 (LTS) | Core language |
| **Primary Database** | PostgreSQL (Neon) | System of record — JPA/Hibernate |
| **Cache / Shared State** | Redis | Spring Cache backend, rate limiting, idempotency, OTP |
| **Document Store** | MongoDB Atlas (optional) | AI insight history, audit log, runtime config |
| **Event Stream** | Apache Kafka | Audit trail + transaction event stream |
| **Work Queue** | RabbitMQ | Email / CSV import / subscription jobs, with DLQ |
| **Security** | Spring Security + JWT + OAuth2 | Authentication & authorization |
| **AI Engine** | Google Gemini | Financial analysis & insights |
| **Payments** | Razorpay | Subscription billing |
| **Email** | Mailjet SMTP | Transactional emails (queued via RabbitMQ) |
| **SMS/OTP** | TextBee | Phone verification |
| **File Storage** | Cloudinary | Profile images |
| **Build** | Maven | Dependency & build management |
| **Container** | Docker (Alpine JRE) | Deployment |

---

## 🚀 Features

### 📊 Core Financial Management
Expense/income CRUD with categorization and date filtering, budget goals, custom categories, recurring transactions.

### 🤖 AI-Powered (plan-gated)
Financial health analysis, smart spending insights, money-saving tips, natural-language queries, automatic merchant categorization.

### 🏦 Bank Integration
CSV/Excel bank statement import with intelligent merchant-to-category mapping and duplicate detection.

### 💳 Subscription & Payments
FREE/BASIC/PREMIUM tiers, Razorpay checkout + webhook, 7-day grace period.

### 📈 Analytics & Reports
Dashboard widgets, category breakdowns, PDF export, daily/weekly/monthly/yearly filters.

### 👤 User Management
JWT + Google OAuth2 login, profile image upload, email activation, phone OTP verification, password reset, GDPR-style account deletion with grace period.

### 🛠️ Admin Panel
User list/search, role management, system stats, live runtime config editing (`/api/admin/config`), manual cache refresh.

---

## 🗂️ Project Structure

```
moneymanager/
├── src/main/java/in/tracking/moneymanager/
│   ├── MoneymanagerApplication.java   # Entry point; loads .env, excludes Mongo autoconfig
│   ├── annotation/                    # @PremiumFeature, @AdminOnly
│   ├── aspect/                        # PremiumFeatureAspect, AdminAccessAspect (AOP guards)
│   ├── config/                        # Redis, Kafka, RabbitMQ, Security, Mongo, Razorpay, Cloudinary...
│   ├── controller/                    # REST controllers (one per domain)
│   ├── security/                      # JwtRequestFilter, RateLimitFilter, IdempotencyFilter, AuditFilter, OAuth2 handlers
│   ├── service/
│   │   ├── event/                     # Kafka publishers (audit, transaction events)
│   │   ├── messaging/                 # RabbitMQ producer/consumer (email)
│   │   └── ...                        # Business logic services
│   ├── document/                      # MongoDB documents (AppConfig, AiQueryHistory, AuditLog)
│   ├── dto/, entity/, repository/     # Transfer objects, JPA entities, Spring Data repos
│   └── exception/                     # GlobalExceptionHandler
├── src/main/resources/application.properties
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## ⚡ Quick Start

### Prerequisites

- **Java 21** (JDK), **Maven 3.9+**
- **Docker & Docker Compose** (for containerized deployment)
- **PostgreSQL** (or Neon cloud database) — required
- **Redis**, **MongoDB**, **Kafka**, **RabbitMQ** — all optional; the app degrades gracefully without them

### 🔧 Local Development

```bash
git clone https://github.com/nitesh-narwal/montrax-springboot.git
cp .env.example .env   # fill in your configuration
./mvnw spring-boot:run
```

```
http://localhost:8090
Health Check: http://localhost:8090/actuator/health
```

### 🐳 Docker Deployment

```bash
./mvnw clean package -DskipTests
docker build -t montrax:latest .
docker compose up -d
```

Local RabbitMQ + Kafka for development (not started by default, since production points at managed brokers):

```bash
docker compose --profile infra up -d
```

---

## 🔐 Configuration

Create a `.env` file in the project root (docker-compose reads the same variables):

```env
# DATABASE (required)
DB_URI=jdbc:postgresql://your-host/database?sslmode=require
DB_USERNAME=your_username
DB_PASSWORD=your_password

# JWT (required)
JWT_SECRET_KEY=your-256-bit-secret-key-here-minimum-32-characters

# EMAIL (MAILJET)
MAILJET_API_KEY=your_mailjet_api_key
MAILJET_SECRET_KEY=your_mailjet_secret_key
MAILJET_MAIL_FROM=noreply@yourdomain.com

# PAYMENTS (RAZORPAY)
RAZORPAY_KEY_ID=rzp_live_xxxxx
RAZORPAY_KEY_SECRET=your_razorpay_secret
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret

# AI (GOOGLE GEMINI)
GEMINI_API_KEY=your_gemini_api_key
GEMINI_MODEL=gemini-2.5-flash

# MONGODB (optional - enables AI history/audit/runtime config)
MONGODB_URI=mongodb+srv://user:pass@cluster.mongodb.net/montrax
MONGODB_DATABASE=montrax

# REDIS (optional but strongly recommended - see Caching Strategy)
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password
CACHE_TYPE=redis   # 'simple' if Redis is unavailable

# CLOUDINARY (profile images)
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_cloudinary_api_key
CLOUDINARY_API_SECRET=your_cloudinary_secret

# GOOGLE OAUTH2 LOGIN (leave unset to disable Google sign-in)
GOOGLE_CLIENT_ID=your_google_oauth_client_id
GOOGLE_CLIENT_SECRET=your_google_oauth_client_secret

# ADMIN SEED (first run only)
ADMIN_EMAIL=admin@yourdomain.com
ADMIN_PASSWORD=change-me-immediately

# TEXTBEE (phone OTP)
TEXTBEE_API_KEY=your_textbee_api_key
TEXTBEE_DEVICE_ID=your_textbee_device_id

# RABBITMQ (amqp:// local, amqps:// managed e.g. CloudAMQP)
RABBITMQ_HOST=amqp://guest:guest@localhost:5672

# KAFKA (leave KAFKA_USER unset for a plain local broker)
KAFKA_HOST=localhost
KAFKA_PORT=9092
KAFKA_USER=
KAFKA_PASSWORD=

# URLS
MONEY_MANAGER_FRONTEND_URL=https://your-frontend-domain.com
APP_ACTIVATION_URL=https://your-backend-domain.com
```

---

## 📡 API Reference

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/register` | Register new user |
| `POST` | `/login` | User login |
| `GET` | `/activate/**` | Activate account |
| `POST` | `/forgot-password` / `/reset-password` | Password recovery |
| `GET` | `/oauth2/**`, `/login/oauth2/**` | Google OAuth2 login |
| `POST` | `/api/otp/**` | Phone OTP send/verify |

### Core Data
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET/POST/PUT/DELETE` | `/api/expenses` | Expense CRUD |
| `GET/POST/PUT/DELETE` | `/api/incomes` | Income CRUD |
| `GET/POST/PUT/DELETE` | `/api/categories` | Category CRUD |
| `GET/POST/PUT/DELETE` | `/api/budget-goals` | Budget goals |
| `GET/POST/PUT/DELETE` | `/api/recurring` | Recurring transactions |

### AI & Analytics (plan-gated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/ai/financial-health` | AI financial analysis |
| `POST` | `/api/ai/query` | Natural-language finance query |
| `GET` | `/api/insights/money-saving-tips` | AI saving tips |
| `GET` | `/api/insights/smart-insights` | Smart spending insights |
| `GET` | `/api/dashboard` | Dashboard aggregation |
| `GET` | `/api/analytics/**` | Category/trend analytics |

### Bank Import (plan-gated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/bank/import/csv` | Import CSV statement |
| `POST` | `/api/bank/import/excel` | Import Excel statement |

### Subscription & Payments
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/subscription/plans` | Public plan list |
| `GET` | `/api/subscription/status` | Current user's subscription |
| `POST` | `/api/payments/create-order` | Create Razorpay order |
| `POST` | `/api/payments/verify` | Verify payment signature |
| `POST` | `/api/payments/webhook` | Razorpay webhook |

### Admin (`ADMIN` role required)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/admin/users` | Paginated/searchable user list |
| `PUT` | `/api/admin/users/{id}/role` | Change a user's role |
| `GET` | `/api/admin/stats` | System stats |
| `GET/PUT` | `/api/admin/config/**` | Read/edit runtime config |
| `POST` | `/api/admin/cache/refresh` | Force-refresh `AppCacheService` |

---

## 📊 Load Handling & Capacity Limits

This section works backward from the actual configured limits in `application.properties` and `docker-compose.yml` — these are **derived estimates from configuration, not measured load-test results**. Run a real load test (k6, JMeter, Gatling) against your target environment before treating these as guarantees.

```
┌─────────┐     ┌──────────┐     ┌─────────────┐     ┌──────────┐
│ Client  │────▶│  Login   │───▶ │  Validate   │────▶│  Generate│
│         │     │ Request  │     │ Credentials │     │   JWT    │
└─────────┘     └──────────┘     └─────────────┘     └────┬─────┘
                                                          │
     ┌────────────────────────────────────────────────────┘
     │
     ▼
┌──────────────┐     ┌─────────────────┐     ┌──────────────┐
│ Return Token │────▶│ Client Stores   │───▶ │ Subsequent   │
│ to Client    │     │ Token           │     │ Requests     │
└──────────────┘     └─────────────────┘     └──────┬───────┘
                                                    │
                                                    ▼
                                            ┌─────────────────┐
                                            │ JwtRequestFilter│
                                            │ Validates Token │
                                            └─────────────────┘
```
### The two hard ceilings on a single instance

| Resource | Configured limit | What happens beyond it |
|---|---|---|
| **Tomcat worker threads** | `server.tomcat.threads.max=10` (2 min-spare) | The 11th concurrent request queues in the acceptor backlog instead of being rejected outright — it waits for a thread to free up |
| **HikariCP DB connections** | `maximum-pool-size=5`, `connection-timeout=20000ms` | The 6th concurrent DB-bound request queues for a connection; after 20s with none free, it fails with a pool-timeout exception |

The **connection pool, not the thread pool, is the tighter bottleneck** for any endpoint that touches Postgres (i.e. almost all of them). Back-of-envelope: if a typical DB-bound request (Neon round-trip + query) takes ~50-150ms, 5 connections in steady rotation give a *theoretical* ceiling around **35-100 requests/second** for that one instance before requests start queuing for a connection. Non-DB endpoints (health checks, static responses) can burst higher since they're only bounded by the 10 Tomcat threads.

### Per-user throttling, independent of the above

Regardless of how much headroom the instance has, no single user can exceed their tier's hourly budget: **100/500/2,000 requests/hour** for FREE/BASIC/PREMIUM (`RateLimitFilter`, enforced via a Redis counter). This exists specifically to stop one noisy client from starving everyone else on a shared, resource-constrained instance.

### Memory

The container is capped at **768MB** (`docker-compose.yml` `deploy.resources.limits.memory`), with the JVM heap capped well below that at **384MB** (`-Xmx384m`), 256MB metaspace, and Serial GC (chosen specifically for low-memory footprint over throughput — a fine tradeoff at this scale, since Serial GC's stop-the-world pauses only start to hurt under high allocation rates that this instance size won't sustain anyway). `-XX:+ExitOnOutOfMemoryError` makes an OOM a fast, visible container restart instead of a slow zombie process.

### How many concurrent *users* is that, practically?

Not every request touches the DB, and not every user is active every second. As a rough guide for this configuration: comfortably tens of concurrently *active* users (mid-request at the same instant) per instance, with hundreds to low-thousands of total registered users generating realistic, bursty traffic across a day — provided Redis is enabled (disabling it removes caching entirely, which multiplies DB load on every previously-cached read).

### Scaling past a single instance

The app is intentionally **fully stateless** — no server-side sessions, no in-process state that matters for correctness (the one exception, `AppCacheService`'s config map, is safely re-derivable from MongoDB by any instance). Every piece of shared state that *must* be consistent across instances already lives externally:

- Auth: JWT, verified independently by any instance
- Rate limits & idempotency: Redis
- Cache: Redis
- Data: PostgreSQL / MongoDB
- Events/jobs: Kafka / RabbitMQ

That means scaling out is just **adding more container replicas behind a load balancer** — no sticky sessions, no cache-warming coordination needed. The actual system-wide ceiling then shifts to the shared backing services: Postgres's own connection limit (each replica brings its own 5-connection Hikari pool, so N replicas need Postgres to allow ~5N connections, or a pooler like PgBouncer in front of Neon), and Redis/Kafka/RabbitMQ throughput (all comfortably capable of far more than this app will produce at this scale).

---

## 🩺 Health Checks

```bash
curl http://localhost:8090/actuator/health            # overall
curl http://localhost:8090/actuator/health/liveness    # k8s liveness probe
curl http://localhost:8090/actuator/health/readiness   # k8s readiness probe
```

MongoDB and Redis health indicators are explicitly disabled (`management.health.mongo.enabled=false`, `management.health.redis.enabled=false`) — since both are optional soft dependencies, their absence must never flip the app's own health check to `DOWN`.

---

## 🧪 Testing

```bash
./mvnw test                     # run all tests
./mvnw test jacoco:report       # with coverage
./mvnw package -DskipTests      # skip tests during build
```

---

## 📋 Deployment Checklist

- [ ] All required env vars set (`DB_URI`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`)
- [ ] `MONEY_MANAGER_FRONTEND_URL` set to the production frontend origin
- [ ] Razorpay webhook URL configured in the Razorpay dashboard
- [ ] Cloudinary upload preset configured
- [ ] MongoDB Atlas network access allows the deployment's egress IP (or leave `MONGODB_URI` unset to run without AI history/audit/config)
- [ ] Redis configured with a password (`CACHE_TYPE=redis` in production — running with `simple` disables all Redis-backed caching, rate limiting, and idempotency)
- [ ] Kafka/RabbitMQ pointed at managed brokers in production (`--profile infra` is for local dev only)
- [ ] SSL/TLS terminated via reverse proxy / platform load balancer
- [ ] Log levels set appropriately (`WARN` defaults are tuned for low-noise production logs)
- [ ] Monitoring/alerting wired to `/actuator/health`

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- [Spring Boot](https://spring.io/projects/spring-boot) · [Google Gemini](https://ai.google.dev/) · [Razorpay](https://razorpay.com/) · [Neon](https://neon.tech/) · [MongoDB Atlas](https://www.mongodb.com/atlas) · [Cloudinary](https://cloudinary.com/) · [Apache Kafka](https://kafka.apache.org/) · [RabbitMQ](https://www.rabbitmq.com/)

---

<p align="center">
  <b>Built with ❤️ for better financial management</b>
</p>

<p align="center">
  <a href="#-montrax---money-manager-backend">⬆️ Back to Top</a>
</p>
