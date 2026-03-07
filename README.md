<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"/>
  <img src="https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/MongoDB-4EA94B?style=for-the-badge&logo=mongodb&logoColor=white" alt="MongoDB"/>
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis"/>
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker"/>
</p>

# 💰 Montrax - Money Manager Backend

> **Enterprise-Grade Personal Finance Management API** — A robust, scalable Spring Boot backend powering intelligent expense tracking, AI-driven financial insights, and comprehensive budget management.

---

## 🌟 Overview

Montrax Backend is a production-ready RESTful API service built with **Spring Boot 4.0.2** and **Java 21**, designed to handle personal finance management with enterprise-level security, AI integration, and multi-database architecture.

### ✨ Key Highlights

- 🔐 **JWT-based Authentication** with secure session management
- 🤖 **AI-Powered Financial Insights** using Google Gemini 2.5
- 💳 **Razorpay Payment Integration** for subscription management
- 📊 **Advanced Analytics Engine** with smart categorization
- 🏦 **Bank Statement Import** with intelligent transaction parsing
- 📧 **Automated Email Notifications** via Mailjet SMTP
- ☁️ **Cloudinary Integration** for profile image management
- 🐳 **Docker-Ready** with optimized resource configuration

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                             │
│                    (React/TypeScript Frontend)                  │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API GATEWAY (NGINX)                        │
│                    SSL Termination & Routing                    │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                   SPRING BOOT APPLICATION                       │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐        │
│  │   Security    │  │  Controllers  │  │    Services   │        │
│  │  (JWT Auth)   │  │  (REST APIs)  │  │ (Business)    │        │
│  └───────────────┘  └───────────────┘  └───────────────┘        │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐        │
│  │   Entities    │  │     DTOs      │  │  Repositories │        │
│  │   (JPA)       │  │  (Transfer)   │  │  (Data Access)│        │
│  └───────────────┘  └───────────────┘  └───────────────┘        │
└──────────────┬────────────────┬─────────────────┬───────────────┘
               │                │                 │
      ┌────────▼────────┐  ┌────▼────┐   ┌────────▼────────┐
      │   PostgreSQL    │  │  Redis  │   │     MongoDB     │
      │  (Primary DB)   │  │ (Cache) │   │ (AI Insights)   │
      │   Neon Cloud    │  │         │   │   Atlas         │
      └─────────────────┘  └─────────┘   └─────────────────┘
```

---

## 📦 Tech Stack

| Category | Technology | Purpose |
|----------|------------|---------|
| **Framework** | Spring Boot 4.0.2 | Application framework |
| **Language** | Java 21 (LTS) | Core programming language |
| **Primary Database** | PostgreSQL (Neon) | Relational data storage |
| **Cache** | Redis | Session & query caching |
| **Document Store** | MongoDB Atlas | AI insights persistence |
| **Security** | Spring Security + JWT | Authentication & Authorization |
| **AI Engine** | Google Gemini 2.5 Flash | Financial analysis & insights |
| **Payment** | Razorpay | Subscription management |
| **Email** | Mailjet SMTP | Transactional emails |
| **File Storage** | Cloudinary | Profile images |
| **Build Tool** | Maven | Dependency & build management |
| **Containerization** | Docker | Deployment |

---

## 🚀 Features

### 📊 Core Financial Management

| Feature | Description |
|---------|-------------|
| **Expense Tracking** | Full CRUD operations with categorization, tags, and date filtering |
| **Income Management** | Track multiple income sources with recurring support |
| **Budget Goals** | Set and monitor spending limits per category |
| **Categories** | Custom category creation with icon support |
| **Recurring Transactions** | Automated recurring income/expense handling |

### 🤖 AI-Powered Features (Premium)

| Feature | Description |
|---------|-------------|
| **Financial Health Analysis** | AI-generated comprehensive financial health reports |
| **Smart Insights** | Personalized spending patterns and recommendations |
| **Money Saving Tips** | AI-curated tips based on your spending behavior |
| **Natural Language Queries** | Ask anything about your finances in plain English |
| **Merchant Categorization** | AI-powered automatic transaction categorization |

### 🏦 Bank Integration

| Feature | Description |
|---------|-------------|
| **CSV Import** | Parse bank statements from multiple banks |
| **Excel Import** | Support for .xlsx bank exports |
| **Auto-Categorization** | Intelligent merchant-to-category mapping |
| **Duplicate Detection** | Prevent importing duplicate transactions |

### 💳 Subscription & Payments

| Feature | Description |
|---------|-------------|
| **Subscription Plans** | Free, Premium, and Pro tiers |
| **Razorpay Integration** | Secure payment processing |
| **Webhook Handling** | Automated subscription status updates |
| **Grace Period** | 7-day grace period for failed payments |

### 📈 Analytics & Reports

| Feature | Description |
|---------|-------------|
| **Dashboard Widgets** | Real-time balance, savings rate, recent transactions |
| **Spending Analytics** | Category-wise breakdown with trend analysis |
| **Export to PDF** | Generate detailed financial reports |
| **Time-based Filters** | Daily, weekly, monthly, yearly views |

### 👤 User Management

| Feature | Description |
|---------|-------------|
| **JWT Authentication** | Stateless, secure authentication |
| **Profile Management** | Update profile with image upload |
| **Account Activation** | Email-based account verification |
| **Password Reset** | Secure password recovery flow |
| **Account Deletion** | GDPR-compliant account deletion with grace period |

---

## 🗂️ Project Structure

```
moneymanager/
├── 📁 src/main/java/in/tracking/moneymanager/
│   ├── 📄 MoneymanagerApplication.java    # Application entry point
│   │
│   ├── 📁 annotation/                     # Custom annotations
│   │   └── PremiumFeature.java            # Premium feature access control
│   │
│   ├── 📁 aspect/                         # AOP aspects
│   │   └── PremiumFeatureAspect.java      # Subscription validation
│   │
│   ├── 📁 config/                         # Configuration classes
│   │   ├── CloudinaryConfig.java          # Image upload config
│   │   ├── MongoConfig.java               # MongoDB configuration
│   │   ├── RazorpayConfig.java            # Payment gateway config
│   │   ├── RedisConfig.java               # Cache configuration
│   │   └── SecurityConfig.java            # Security & CORS config
│   │
│   ├── 📁 controller/                     # REST Controllers
│   │   ├── AiController.java              # AI analysis endpoints
│   │   ├── AnalyticsController.java       # Analytics & reports
│   │   ├── BankController.java            # Bank import endpoints
│   │   ├── BudgetGoalController.java      # Budget management
│   │   ├── CategoryController.java        # Category CRUD
│   │   ├── DashboardController.java       # Dashboard data
│   │   ├── ExpenceController.java         # Expense management
│   │   ├── IncomeController.java          # Income management
│   │   ├── PaymentController.java         # Razorpay integration
│   │   ├── ProfileController.java         # User profile
│   │   ├── RecurringTransactionController.java
│   │   ├── SmartInsightsController.java   # AI insights
│   │   └── SubscriptionController.java    # Subscription plans
│   │
│   ├── 📁 document/                       # MongoDB documents
│   │   └── AiQueryHistory.java            # AI query persistence
│   │
│   ├── 📁 dto/                            # Data Transfer Objects
│   │   ├── AuthDTO.java                   # Authentication DTOs
│   │   ├── ExpenceDTO.java                # Expense DTOs
│   │   ├── IncomeDTO.java                 # Income DTOs
│   │   └── ...                            # Other DTOs
│   │
│   ├── 📁 entity/                         # JPA Entities
│   │   ├── ProfileEntity.java             # User profile
│   │   ├── ExpenceEntity.java             # Expenses
│   │   ├── IncomeEntity.java              # Incomes
│   │   ├── CategoryEntity.java            # Categories
│   │   ├── BudgetGoalEntity.java          # Budget goals
│   │   ├── SubscriptionEntity.java        # User subscriptions
│   │   └── ...                            # Other entities
│   │
│   ├── 📁 repository/                     # Spring Data repositories
│   │   └── ...                            # JPA & MongoDB repos
│   │
│   ├── 📁 security/                       # Security components
│   │   └── JwtRequestFilter.java          # JWT validation filter
│   │
│   ├── 📁 service/                        # Business logic
│   │   ├── AiAnalysisService.java         # AI processing
│   │   ├── GeminiService.java             # Google Gemini integration
│   │   ├── CloudinaryService.java         # Image uploads
│   │   ├── EmailService.java              # Email notifications
│   │   ├── PaymentService.java            # Payment processing
│   │   ├── DataRetentionService.java      # Data cleanup scheduler
│   │   └── ...                            # Other services
│   │
│   └── 📁 util/                           # Utility classes
│       └── ...                            # Helper utilities
│
├── 📁 src/main/resources/
│   └── application.properties             # Application configuration
│
├── 📄 Dockerfile                          # Docker image definition
├── 📄 docker-compose.yml                  # Container orchestration
├── 📄 pom.xml                             # Maven dependencies
└── 📄 README.md                           # You are here!
```

---

## ⚡ Quick Start

### Prerequisites

- **Java 21** (JDK)
- **Maven 3.9+**
- **Docker & Docker Compose** (for containerized deployment)
- **PostgreSQL** (or Neon cloud database)
- **Redis** (optional, for caching)
- **MongoDB Atlas** (optional, for AI features)

### 🔧 Local Development

1. **Clone the repository**
   ```bash
   git clone https://github.com/nitesh-narwal/montrax-springboot.git
   ```

2. **Create environment file**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Configure environment variables** (see [Configuration](#-configuration))

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

5. **Access the API**
   ```
   http://localhost:8090
   Health Check: http://localhost:8090/actuator/health
   ```

### 🐳 Docker Deployment

1. **Build the JAR**
   ```bash
   ./mvnw clean package -DskipTests
   ```

2. **Build Docker image**
   ```bash
   docker build -t montrax:latest .
   ```

3. **Run with Docker Compose**
   ```bash
   docker-compose up -d
   ```

---

## 🔐 Configuration

### Environment Variables

Create a `.env` file in the project root:

```env
# ═══════════════════════════════════════════════════════════════
# DATABASE CONFIGURATION
# ═══════════════════════════════════════════════════════════════
DB_URI=jdbc:postgresql://your-host/database?sslmode=require
DB_USERNAME=your_username
DB_PASSWORD=your_password

# ═══════════════════════════════════════════════════════════════
# JWT SECURITY
# ═══════════════════════════════════════════════════════════════
JWT_SECRET_KEY=your-256-bit-secret-key-here-minimum-32-characters

# ═══════════════════════════════════════════════════════════════
# EMAIL (MAILJET)
# ═══════════════════════════════════════════════════════════════
MAILJET_API_KEY=your_mailjet_api_key
MAILJET_SECRET_KEY=your_mailjet_secret_key
MAILJET_MAIL_FROM=noreply@yourdomain.com

# ═══════════════════════════════════════════════════════════════
# PAYMENT (RAZORPAY)
# ═══════════════════════════════════════════════════════════════
RAZORPAY_KEY_ID=rzp_live_xxxxx
RAZORPAY_KEY_SECRET=your_razorpay_secret
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret

# ═══════════════════════════════════════════════════════════════
# AI (GOOGLE GEMINI)
# ═══════════════════════════════════════════════════════════════
GEMINI_API_KEY=your_gemini_api_key
GEMINI_MODEL=gemini-2.5-flash

# ═══════════════════════════════════════════════════════════════
# MONGODB (AI FEATURES)
# ═══════════════════════════════════════════════════════════════
MONGODB_URI=mongodb+srv://user:pass@cluster.mongodb.net/montrax
MONGODB_DATABASE=montrax

# ═══════════════════════════════════════════════════════════════
# REDIS (CACHING)
# ═══════════════════════════════════════════════════════════════
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password
CACHE_TYPE=redis  # Use 'simple' if Redis is unavailable

# ═══════════════════════════════════════════════════════════════
# CLOUDINARY (IMAGE UPLOADS)
# ═══════════════════════════════════════════════════════════════
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_cloudinary_api_key
CLOUDINARY_API_SECRET=your_cloudinary_secret

# ═══════════════════════════════════════════════════════════════
# APPLICATION URLs
# ═══════════════════════════════════════════════════════════════
MONEY_MANAGER_FRONTEND_URL=https://your-frontend-domain.com
APP_ACTIVATION_URL=https://your-backend-domain.com
```

---

## 📡 API Reference

### Authentication Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/register` | Register new user |
| `POST` | `/api/auth/login` | User login |
| `GET` | `/api/auth/activate` | Activate account |
| `POST` | `/api/auth/forgot-password` | Request password reset |
| `POST` | `/api/auth/reset-password` | Reset password |

### Expense Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/expenses` | Get all expenses |
| `POST` | `/api/expenses` | Create expense |
| `PUT` | `/api/expenses/{id}` | Update expense |
| `DELETE` | `/api/expenses/{id}` | Delete expense |

### Income Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/incomes` | Get all incomes |
| `POST` | `/api/incomes` | Create income |
| `PUT` | `/api/incomes/{id}` | Update income |
| `DELETE` | `/api/incomes/{id}` | Delete income |

### Categories

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/categories` | Get user categories |
| `POST` | `/api/categories` | Create category |
| `PUT` | `/api/categories/{id}` | Update category |
| `DELETE` | `/api/categories/{id}` | Delete category |

### Budget Goals

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/budget-goals` | Get all budget goals |
| `POST` | `/api/budget-goals` | Create budget goal |
| `PUT` | `/api/budget-goals/{id}` | Update budget goal |
| `DELETE` | `/api/budget-goals/{id}` | Delete budget goal |

### AI Features (Premium)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/ai/financial-health` | Get AI financial analysis |
| `POST` | `/api/ai/query` | Ask AI a question |
| `GET` | `/api/insights/money-saving-tips` | Get saving tips |
| `GET` | `/api/insights/smart-insights` | Get smart insights |

### Bank Import (Premium)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/bank/import/csv` | Import CSV statement |
| `POST` | `/api/bank/import/excel` | Import Excel statement |

### Subscription & Payments

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/subscription/plans` | Get subscription plans |
| `GET` | `/api/subscription/status` | Get user subscription |
| `POST` | `/api/payments/create-order` | Create Razorpay order |
| `POST` | `/api/payments/verify` | Verify payment |
| `POST` | `/api/payments/webhook` | Razorpay webhook |

### Dashboard & Analytics

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/dashboard` | Get dashboard data |
| `GET` | `/api/analytics/summary` | Get analytics summary |
| `GET` | `/api/analytics/category-breakdown` | Category-wise analysis |

---

## 🔒 Security Features

### Authentication Flow

```
┌─────────┐     ┌──────────┐     ┌─────────────┐     ┌──────────┐
│ Client  │────▶│  Login   │────▶│  Validate   │────▶│  Generate│
│         │     │ Request  │     │ Credentials │     │   JWT    │
└─────────┘     └──────────┘     └─────────────┘     └────┬─────┘
                                                          │
     ┌────────────────────────────────────────────────────┘
     │
     ▼
┌──────────────┐     ┌─────────────────┐     ┌──────────────┐
│ Return Token │────▶│ Client Stores   │────▶│ Subsequent   │
│ to Client    │     │ Token           │     │ Requests     │
└──────────────┘     └─────────────────┘     └──────┬───────┘
                                                    │
                                                    ▼
                                            ┌─────────────────┐
                                            │ JwtRequestFilter│
                                            │ Validates Token │
                                            └─────────────────┘
```

### Security Implementations

- ✅ **JWT Token Authentication** with configurable expiration
- ✅ **Password Hashing** using BCrypt
- ✅ **CORS Configuration** for frontend integration
- ✅ **Rate Limiting** for AI endpoints (Guava RateLimiter)
- ✅ **Input Validation** on all DTOs
- ✅ **SQL Injection Prevention** via JPA/Hibernate
- ✅ **Non-root Docker User** for container security
- ✅ **Subscription-based Access Control** via AOP

---

## 🐳 Docker Configuration

### Resource Requirements

| Environment | Memory | CPU | Notes |
|-------------|--------|-----|-------|
| **Development** | 512MB | 0.5 | Local testing |
| **Production** | 768MB | 0.75 | Recommended minimum |
| **High Load** | 1GB+ | 1+ | For concurrent users |

### JVM Optimization

```dockerfile
JAVA_OPTS="-Xmx384m -Xms256m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
```

| Flag | Purpose |
|------|---------|
| `-Xmx384m` | Maximum heap size (384MB) |
| `-Xms256m` | Initial heap size (256MB) |
| `-XX:MaxMetaspaceSize=256m` | Metaspace limit for classes |
| `-XX:+UseSerialGC` | Serial GC for low memory footprint |
| `-XX:+ExitOnOutOfMemoryError` | Fast fail on OOM |

---

## 📊 Performance Optimization

### Database Connection Pool (HikariCP)

```properties
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.minimum-idle=1
spring.datasource.hikari.idle-timeout=30000
spring.datasource.hikari.connection-timeout=20000
```

### Tomcat Thread Pool

```properties
server.tomcat.threads.max=10
server.tomcat.threads.min-spare=2
```

### Caching Strategy

- **Redis Cache** for subscription status, dashboard data
- **In-memory Cache** fallback when Redis is unavailable
- **Cache Invalidation** on data modifications

---

## 🔄 Data Retention

The application includes automatic data cleanup based on subscription tiers:

| Plan | Retention Period |
|------|-----------------|
| Free | 3 months |
| Premium | 1 year |
| Pro | Unlimited |

Data retention scheduler runs daily to clean up expired data for inactive users.

---

## 🩺 Health Checks

### Actuator Endpoints

```bash
# Overall health
curl http://localhost:8090/actuator/health

# Liveness probe (for Kubernetes)
curl http://localhost:8090/actuator/health/liveness

# Readiness probe (for Kubernetes)
curl http://localhost:8090/actuator/health/readiness
```

### Response Example

```json
{
  "status": "UP",
  "groups": ["liveness", "readiness"]
}
```

---

## 🧪 Testing

```bash
# Run all tests
./mvnw test

# Run with coverage
./mvnw test jacoco:report

# Skip tests during build
./mvnw package -DskipTests
```

---

## 📋 Deployment Checklist

- [ ] Configure all environment variables
- [ ] Set `MONEY_MANAGER_FRONTEND_URL` to production URL
- [ ] Configure Razorpay webhook URL
- [ ] Set up Cloudinary upload preset
- [ ] Configure MongoDB Atlas network access
- [ ] Set up Redis with password
- [ ] Configure SSL/TLS via reverse proxy
- [ ] Set appropriate log levels for production
- [ ] Enable rate limiting for API endpoints
- [ ] Set up monitoring and alerting

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

- [Spring Boot](https://spring.io/projects/spring-boot) - The amazing Java framework
- [Google Gemini](https://ai.google.dev/) - AI-powered financial insights
- [Razorpay](https://razorpay.com/) - Payment gateway integration
- [Neon](https://neon.tech/) - Serverless PostgreSQL
- [MongoDB Atlas](https://www.mongodb.com/atlas) - Cloud document database
- [Cloudinary](https://cloudinary.com/) - Image management

---

<p align="center">
  <b>Built with ❤️ for better financial management</b>
</p>

<p align="center">
  <a href="#-montrax---money-manager-backend">⬆️ Back to Top</a>
</p>

