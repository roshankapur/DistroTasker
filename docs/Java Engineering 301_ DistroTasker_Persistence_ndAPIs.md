# **Java Engineering 301: DistroTasker Persistence & APIs**

Welcome to the big leagues. Up until now, every time you stopped your application, your data died. In a production environment, that’s a "Company-Ending Event." In 301, we give **DistroTasker** a memory and a voice. We are moving to **Spring Boot**, **PostgreSQL**, and **REST APIs**.

## **1\. The Shift: Professional Persistence**

| Concept | "College/101" Approach | "Professional/301" Approach   |
| :---- | :---- | :---- |
| **Data Storage** | ConcurrentHashMap (Volatile) | PostgreSQL (Persistent & ACID Compliant) |
| **Data Access** | Manual List iteration | Spring Data JPA (Repository Pattern) |
| **Interface** | Scanner / Console Input | RESTful Endpoints (JSON over HTTP) |
| **Config** | Hardcoded variables | application.properties / application.yml |

## **2\. Task 1: The Spring Boot Skeleton**

Convert your standalone Java app into a Spring Boot application. This isn't just "adding a library"; it's changing how the application lifecycle works.

* **Business Reason:** Industry standard. Using Spring Boot means your code is easier to maintain, hire for, and deploy to any cloud provider.  
* **Engineering Reason:** **Dependency Injection (DI)**. Instead of manually creating new TaskSchedulerService(), let Spring manage the "Beans." This makes the code testable and decoupled.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ Inserted by opus to fill in missing components avoiding functionality failure   │
└─────────────────────────────────────────────────────────────────────────────────┘
BACKWARD REFERENCE:
Your layered structure from 101 (model → repository → service → client) maps
directly to Spring's annotation model: @Entity, @Repository, @Service,
@RestController. The separation you built from the beginning pays off here — the
swap from ConcurrentHashMap to PostgreSQL only requires changes in the repository
layer, exactly as designed.
```

### **The Plan:**

* Use Spring Initializr to bootstrap a project with Spring Web and Spring Data JPA.  
* Annotate your services with @Service and your logic from 101/201 will now be managed by the Spring Context.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ Inserted by opus to fill in missing components avoiding functionality failure   │
└─────────────────────────────────────────────────────────────────────────────────┘
SPRING INITIALIZR — COMPLETE SETUP:
Go to start.spring.io and configure:
  Project:   Maven
  Language:  Java
  Boot:      Latest stable 3.x
  Group:     com.distrotasker
  Artifact:  distrotasker
  Packaging: Jar
  Java:      17 or 21 (LTS)

Required Dependencies:
  1. Spring Web              (spring-boot-starter-web)
     → REST controllers, embedded Tomcat, Jackson JSON serialization
  2. Spring Data JPA         (spring-boot-starter-data-jpa)
     → JPA + Hibernate ORM for PostgreSQL mapping
  3. PostgreSQL Driver       (postgresql)
     → JDBC driver so JPA can actually talk to Postgres

Optional but Recommended:
  4. Spring Boot DevTools    → hot-reload during development
  5. Lombok                  → reduces boilerplate (@Data, @Builder)
  6. Validation              (spring-boot-starter-validation)
     → @Valid, @NotNull, @NotBlank on request bodies
```

## **3\. Task 2: Mapping to PostgreSQL (The Memory)**

Transform your Task POJO into a JPA @Entity. Create a TaskRepository that extends JpaRepository.

* **Business Reason:** Durability. If the server crashes, we need to know exactly which tasks were PENDING so we can resume them on restart.  
* **Engineering Reason:** **Object-Relational Mapping (ORM)**. We want to work with Java objects, not write manual INSERT INTO... SQL strings which are prone to SQL injection and syntax errors.

### **The Plan:**

* Setup a local PostgreSQL instance.  
* Map TaskStatus to a String or Integer in the DB using @Enumerated.  
* Replace your ConcurrentHashMap logic with taskRepository.save() and taskRepository.findAll().

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ Inserted by opus to fill in missing components avoiding functionality failure   │
└─────────────────────────────────────────────────────────────────────────────────┘
APPLICATION CONFIGURATION + LOGGING:
Add the following to application.properties (or application.yml):

  # Database
  spring.datasource.url=jdbc:postgresql://localhost:5432/distrotasker
  spring.datasource.username=postgres
  spring.datasource.password=your_password

  # JPA / Hibernate
  spring.jpa.hibernate.ddl-auto=update
  spring.jpa.show-sql=true
  spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

  # Logging (carries forward your SLF4J/Logback from 101)
  logging.level.root=INFO
  logging.level.com.distrotasker=DEBUG
  logging.level.org.hibernate.SQL=DEBUG

  # Server
  server.port=8080

Note: ddl-auto=update is fine for development. For production, use a migration
tool like Flyway (spring-boot-starter-flyway) or Liquibase.

LOGGING CONFIG:
Spring Boot auto-configures Logback (the same framework recommended in 101). The
logging.level properties above replace any manual logger configuration. Your
existing log.info() and log.debug() calls from 101/201 work unchanged — Spring
just manages the configuration externally now via application.properties.
```

## **4\. Task 3: Exposing the Voice (REST APIs)**

Create a TaskController to allow external users to interact with DistroTasker.

* **Business Reason:** Integration. Other systems (like a dashboard or a mobile app) need to submit tasks or check statuses.  
* **Engineering Reason:** **Interface Segregation**. By exposing JSON endpoints, you allow the "Frontend" to be anything without changing a single line of your "Backend" logic.

### **The Plan:**

* **POST /api/tasks:** To submit a new task. (Apply your 201 Rate Limiter here\!)  
* **GET /api/tasks:** To list all tasks and their current statuses.  
* **GET /api/tasks/{id}:** To fetch details of a specific execution.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ Inserted by opus to fill in missing components avoiding functionality failure   │
└─────────────────────────────────────────────────────────────────────────────────┘
RATE LIMITER INTEGRATION (BRIDGING 201 → 301):
Annotate your RateLimiter class from 201 with @Component so Spring manages it.
Inject it into your TaskController via constructor injection. Call
rateLimiter.tryConsume() before taskRepository.save(). If it throws
RateLimitExceededException, the exception handler (below) returns HTTP 429.

EXCEPTION HANDLING:
Add a @ControllerAdvice class (e.g. GlobalExceptionHandler) with @ExceptionHandler
methods to translate Java exceptions into proper HTTP responses:
  - RateLimitExceededException  →  HTTP 429 Too Many Requests
  - TaskNotFoundException       →  HTTP 404 Not Found
  - Generic Exception           →  HTTP 500 Internal Server Error
Return a consistent JSON body: { "error": "message", "status": 429 }
Without this, your API leaks raw Java stack traces to the client.

DTO PATTERN:
Do not expose your JPA @Entity directly in API responses. Create a TaskDTO (or
TaskResponse) class containing only the fields the client needs. Use a simple
mapper method to convert Entity → DTO.
Why: Your entity may contain internal fields (retryCount, maxRetries, JPA
metadata) that clients should not see or manipulate. DTOs also protect you from
accidentally exposing future database columns.

ADDITIONAL ENDPOINTS:
  - DELETE /api/tasks/{id}
    Cancel a scheduled task. Set status to CANCELLED and remove it from the
    ScheduledExecutorService if it hasn't fired yet. A scheduler without
    cancellation is not production-ready.

  - PUT /api/tasks/{id}
    Reschedule an existing task. Update the scheduledTime, cancel the old
    executor future, and re-submit with the new delay. Only allow rescheduling
    for tasks still in PENDING status.
```

### 

Expert Tip: The Postman Ritual

Stop using your browser to test APIs. Download **Postman** or use the **IntelliJ HTTP Client**. Professional engineers build "Collections" of requests. When you change your code, you run the collection to ensure you haven't broken your contracts. Also, keep an eye on your **Postgres Logs** to see the actual SQL Spring is generating for you—it's often the best way to debug performance issues.

## **5\. Mandatory Logic Check**

As you move your logic into Spring, ensure your TaskSchedulerService starts automatically. Look into the @PostConstruct annotation or CommandLineRunner to trigger your background "polling" thread that checks the DB for tasks that are due for execution.  
**Graduation Requirement:** You should be able to restart your application, and a task you scheduled *before* the restart should still execute at the correct time. Use Postman to prove it.