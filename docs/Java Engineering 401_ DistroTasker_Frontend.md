# **Java Engineering 401: DistroTasker Live Frontend**

Line spacing: 1.25  
You’ve built the **Heart** (101), the **Shield** (201), and the **Memory** (301). Now, we give DistroTasker a **Face**. In the professional world, a backend without a dashboard is just a black box. Today, you move from testing with Postman to building a real-time monitoring interface.  
**Strict Rule:** No React, No Vue, No Angular. We are using **Vanilla HTML, CSS, and JavaScript**. If you can't manipulate the DOM yourself, you don't actually understand the web.

## **1\. The Goal: The "Observer" Dashboard**

We need a single-page dashboard that shows us the "Pulse" of our system in real-time. This isn't just about pretty buttons; it's about system visibility.

| Layer | Responsibility | Why?   |
| :---- | :---- | :---- |
| **HTML** | Structural skeleton (Submission Form, Status Table). | Separation of concerns. HTML is for structure, not logic. |
| **CSS** | Layout and "Status Colors" (Red for Fail, Green for Success). | User Experience. A scheduler needs to signal health at a glance. |
| **Vanilla JS** | Polling the /api/tasks endpoint and updating the DOM. | Interactivity without the overhead of heavy frameworks. |

## **2\. Task 1: The "Live" State Table**

Build a clean HTML table that maps to your Task model from 301\. Your JavaScript must fetch the data from GET /api/tasks and render rows dynamically.

* **Business Reason:** The operations team needs a "birds-eye view" of all scheduled work without looking at database logs.  
* **Engineering Reason:** You must learn to map JSON responses from your Spring Boot API to dynamic DOM elements using document.createElement or template literals.

## **3\. Task 2: Polling & Traffic Math**

Instead of making the user hit "Refresh," implement a setInterval in JavaScript that fetches the task list every 5 seconds.  
**The Math of Polling:**  
`RPS = N / T`  
If you have 100 users (N) watching the dashboard with a polling interval of 1 second (T), your backend gets 100 requests every second just for checking status. We will use T \= 5s to keep the load manageable.

## **4\. Task 3: The Submission Form**

Create a simple form to POST a new task (Name, Script Path, Schedule Time).

* **Business Reason:** To move from a "Developer Tool" (Postman) to a "Product" (Dashboard).  
* **Engineering Reason:** You must handle JSON.stringify() and set the correct headers (Content-Type: application/json) in the fetch() API.

## **5\. Mandatory Logic Check: The Hydration Bridge**

In your 301 Java Backend, you must now implement the **Hydration Logic**. When the app starts, it shouldn't just sit there. It must query the DB for any PENDING tasks and re-submit them to the ScheduledExecutorService.

* **Expert Tip:** Use the ApplicationReadyEvent in Spring Boot to trigger this process.

### **Expert Tip: The "CORS" Wall**

Because your HTML is likely running on localhost:5500 (Live Server) and your Java API is on localhost:8080, the browser will block your requests. You MUST add @CrossOrigin(origins \= "\*") to your Spring Boot Controller to allow the communication.

## **Graduation Requirement**

Open your index.html. Fill out the form to schedule a script (e.g., echo "DistroTasker Lives") for 30 seconds from now. Watch the table row change from **PENDING** to **RUNNING** to **COMPLETED** automatically without you ever touching the refresh button.