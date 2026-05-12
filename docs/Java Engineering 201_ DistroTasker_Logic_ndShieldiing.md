# **Java Engineering 201: DistroTasker Logic & Shielding**

Welcome back. You’ve built the "Heart"—the tasks are running. But in the real world, "it works" isn't enough. In the real world, users are malicious or incompetent, and systems have limits. If a user submits 10,000 tasks at once, your 101 engine will crash the JVM or melt the CPU.  
In 201, we build the **Shield**. We are moving from "Just execute it" to "Traffic Shaping."

## **1\. Professional Shielding vs. Naive Execution**

| Feature | Naive (101) | Professional (201)   |
| :---- | :---- | :---- |
| **Submission** | Accept everything immediately. | Rate-limited via Token Bucket. |
| **Execution** | Run as many as the pool allows. | Concurrently limited via Semaphores. |
| **Failure** | Log it and forget it. | Automatic Retry with Exponential Backoff. |

## **2\. Task 1: The Token Bucket Rate Limiter**

We need to ensure that no single client can flood the TaskSchedulerService. You will implement a **Token Bucket Algorithm**.  
**Business Reason:** Fairness. One user shouldn't be allowed to "starve" others by taking up all the scheduling slots.  
**Engineering Reason:** Protecting the Heap. Every task scheduled takes up memory. We need a way to reject requests at the *edge* before they consume resources.

### **The Math**

The number of available tokens at any time \\(t\\) is:  
\\\[ Tokens\_{t} \= \\min(Capacity, Tokens\_{last} \+ (t \- t\_{last}) \\times RefillRate) \\\]  
**Your Goal:** Create a RateLimiter class. It should track available "tokens." Every time a task is submitted, it "consumes" a token. If the bucket is empty, throw a RateLimitExceededException.

## **3\. Task 2: Resource Shielding (Semaphores)**

While the ScheduledExecutorService handles the queue, we need a hard limit on how many *heavy* scripts are physically running at the exact same time to protect CPU/IO.  
**Business Reason:** Cost and Stability. Running too many scripts simultaneously can slow down the entire server, making the OS unresponsive.  
**Engineering Reason:** Context Switching. If you have 4 CPU cores and try to run 100 intensive scripts, the CPU spends more time switching between them than doing work. Use a java.util.concurrent.Semaphore to limit *active* executions.  
**Your Goal:** Wrap the task execution logic in the TaskSchedulerService with a Semaphore. Acquire before running, release in a finally block.

## **4\. Task 3: The "Resilient" Retry Logic**

Scripts fail. Network blips happen. We shouldn't just give up. However, retrying immediately is a recipe for a "Thundering Herd" problem.  
**Business Reason:** Reliability. Users expect the system to handle transient errors automatically.  
**Engineering Reason:** Preventing Cascading Failures. Use **Exponential Backoff** to give the failing dependency room to breathe.

### **The Math**

The wait time for retry attempt \\(n\\) is:  
\\\[ WaitTime \= Base \\times 2^{n} \\\]  
**Your Goal:** Update your TaskStatus logic. If a task fails, re-schedule it with an incremented retry count and a longer delay.

**Expert Tip: The "Thread-Safe" Trap** When implementing the Token Bucket, don't just use int tokens. You’re in a multi-threaded environment. Use AtomicInteger or proper synchronized blocks. If two threads check for a token at the same microsecond, they might both think they got the last one. That’s a Race Condition. Be better than that.

**Graduation Requirement:** Run a load test. Submit 50 tasks with a rate limit of 5 per second and a concurrency limit of 2\. Your logs should show the 45 tasks being rejected or queued properly, and only 2 scripts ever running at the exact same moment.