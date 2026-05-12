# **Java Engineering 301: DistroTasker Persistence & APIs**

Line spacing: 1.25  
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

### **The Plan:**

* Use Spring Initializr to bootstrap a project with Spring Web and Spring Data JPA.  
* Annotate your services with @Service and your logic from 101/201 will now be managed by the Spring Context.

## **3\. Task 2: Mapping to PostgreSQL (The Memory)**

Transform your Task POJO into a JPA @Entity. Create a TaskRepository that extends JpaRepository.

* **Business Reason:** Durability. If the server crashes, we need to know exactly which tasks were PENDING so we can resume them on restart.  
* **Engineering Reason:** **Object-Relational Mapping (ORM)**. We want to work with Java objects, not write manual INSERT INTO... SQL strings which are prone to SQL injection and syntax errors.

### **The Plan:**

* Setup a local PostgreSQL instance.  
* Map TaskStatus to a String or Integer in the DB using @Enumerated.  
* Replace your ConcurrentHashMap logic with taskRepository.save() and taskRepository.findAll().

## **4\. Task 3: Exposing the Voice (REST APIs)**

Create a TaskController to allow external users to interact with DistroTasker.

* **Business Reason:** Integration. Other systems (like a dashboard or a mobile app) need to submit tasks or check statuses.  
* **Engineering Reason:** **Interface Segregation**. By exposing JSON endpoints, you allow the "Frontend" to be anything without changing a single line of your "Backend" logic.

### **The Plan:**

* **POST /api/tasks:** To submit a new task. (Apply your 201 Rate Limiter here\!)  
* **GET /api/tasks:** To list all tasks and their current statuses.  
* **GET /api/tasks/{id}:** To fetch details of a specific execution.

### 

Expert Tip: The Postman Ritual

Stop using your browser to test APIs. Download **Postman** or use the **IntelliJ HTTP Client**. Professional engineers build "Collections" of requests. When you change your code, you run the collection to ensure you haven't broken your contracts. Also, keep an eye on your **Postgres Logs** to see the actual SQL Spring is generating for you—it's often the best way to debug performance issues.

## **5\. Mandatory Logic Check**

As you move your logic into Spring, ensure your TaskSchedulerService starts automatically. Look into the @PostConstruct annotation or CommandLineRunner to trigger your background "polling" thread that checks the DB for tasks that are due for execution.  
**Graduation Requirement:** You should be able to restart your application, and a task you scheduled *before* the restart should still execute at the correct time. Use Postman to prove it.