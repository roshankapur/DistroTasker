# **Java Engineering 101: DistroTasker Core**

Welcome to the first leg of your journey. We aren't building a "To-Do" list; we are building the foundation of a **Distributed Task Scheduler**. In 101, we focus on the "Heart": the ability to schedule and execute logic at a specific point in time using standard Java concurrency tools.

## **1\. The Architecture: Professional Layering**

Even for a console app, we don't dump everything in one file. We use a decoupled structure to ensure that when we swap our Mock data for a real database in 301, the rest of our code doesn't break.

| Layer | Responsibility | Why?   |
| :---- | :---- | :---- |
| **model** | Pure Data Objects (POJOs). No logic. | Separates "What the data is" from "What we do with it." |
| **repository** | Data Access (In-memory for now). | Abstraction. The service shouldn't care if data is in a List or PostgreSQL. |
| **service** | The "Brain." Business logic lives here. | Centralizes rules. This is the most important layer to test. |
| **client** | The entry point (Main/CLI). | Provides a way for the user to interact with the system. |

## **2\. The Blueprint**

### **Task 1: Define the Domain Model**

Create a Task class. It should represent a unit of work that needs to be executed.

* **Fields:** UUID id, String scriptPath, LocalDateTime scheduledTime, TaskStatus status (Enum: PENDING, RUNNING, COMPLETED, FAILED).

**Business Reason:** We need a standard way to track what work is pending and what is done for reporting and auditing.  
**Engineering Reason:** Encapsulation. By using private fields and a status Enum, we prevent external classes from putting a Task into an invalid state (e.g., a Task shouldn't be "COMPLETED" before it starts).

### **Task 2: The Mock Repository**

Implement a MockTaskRepository. For now, use a simple ConcurrentHashMap or a List to store your tasks.  
**Business Reason:** Users need to be able to "submit" tasks for the future even if the system is currently busy.  
**Engineering Reason:** Using thread-safe collections (like ConcurrentHashMap) is non-negotiable once we introduce the Task Runner.

### **Task 3: The Scheduler Engine (Threading)**

This is where the magic happens. You must use Java's ScheduledExecutorService.

* Create a TaskSchedulerService.  
* It should have a method: schedule(Task task).  
* Calculate the delay: $$Delay = ScheduledTime - CurrentTime$$.  
* Submit the task to the executor to run after that delay.

**Business Reason:** Tasks represent expensive operations (scripts). We can't let one long-running script block the entire system.  
**Engineering Reason:** ExecutorServices manage a pool of worker threads. This prevents "Thread Explosion" (creating 1,000 threads for 1,000 tasks, which would crash the JVM memory).

## **3\. Expert Tip: Logging is Your Best Friend**

Since we don't have a UI yet, you are flying blind. Do not use System.out.println. Use a proper logging framework (like SLF4J/Logback) or at least create a Logger utility. You need to see timestamped logs of when a task was *scheduled* vs when it actually *started*. This is how you verify that your delay logic actually works.  
**Graduation Requirement:** You must be able to add 5 tasks via the Main class with different execution times, and see them fire off correctly in parallel across different threads.