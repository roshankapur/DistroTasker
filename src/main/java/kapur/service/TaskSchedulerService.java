package kapur.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import kapur.model.Task;
import kapur.model.TaskStatus;
import kapur.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//number of concurency threads should generally be equal to number of cores
//each core can only execute one thread except in cases of "hyperthreading"

//investigate more threads in JVM used than available from OS and its management
//semaphores can be added as a limiter on concurrency (currently only present on api/scheduling)
@Service
public class TaskSchedulerService {
    private final TaskRepository taskRepository;
    private final ScheduledExecutorService scheduler;

    //retry base delay
    private final long baseDelayMs;

    public TaskSchedulerService(
            TaskRepository taskRepository,
            @Value("${distrotasker.retry.base-delay-ms}") long baseDelayMs) {
        this.taskRepository = taskRepository;
        this.scheduler = Executors.newScheduledThreadPool(getAvailableCoreCount());
        this.baseDelayMs = baseDelayMs;

        System.out.println("[INIT] ScheduledExecutorService pool initialized with " + getAvailableCoreCount() + " threads (Available Cores)");
        System.out.println("[INIT] Retry base delay: " + baseDelayMs + "ms");
    }

    //helper method to get number of availble OS threads at runtime
    public static int getAvailableCoreCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Starup recovery /"hydration" logic
     * runs once after Spring initializes the class
     * DB is queried for any QUEUED or RETRYING tasks left over
     * from previous run and requeues them to ScheduledExecutorService.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverTasksOnStartup() {
        //list @recoverable stores all tasks recovered on server restart
        List<Task> recoverable = new java.util.ArrayList<>(taskRepository.findByStatus(TaskStatus.QUEUED));
        recoverable.addAll(taskRepository.findByStatus(TaskStatus.RETRYING));
        // any task marked RUNNING on boot is a zombie from a server crash, queue it again
        recoverable.addAll(taskRepository.findByStatus(TaskStatus.RUNNING));

        if (recoverable.isEmpty()) {
            System.out.println("[RECOVERY] NO QUEUED/RETRYING TASKS FOUND. INITIATING CLEAN START");
        } else {
            System.out.println(
                    "[RECOVERY] FOUND " + recoverable.size() + " TASKS IN DB — INITIATING REQUEUED START");

            for (Task task : recoverable) {
                long delayMillis = Duration.between(LocalDateTime.now(), task.getScheduledTime()).toMillis();
                if (delayMillis < 0)
                    delayMillis = 0; // set past due tasks to be executed asap

                System.out.println("[RECOVERY] TASK WITH ID: " + task.getId()
                        + " FIRES IN " + delayMillis + "ms");
                scheduler.schedule(() -> executeTask(task), delayMillis, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * adds task marked QUEUED to db
     * and calls @scheduler (ScheduledExececutorService) to use executeTask() method
     *
     * @Async makes Spring run this on a threadpool separate from
     * the main app thread running DB transactions/scheduledExecutorService execution
     */
    @Async
    public void scheduleTask(Task task) {
        task.setStatus(TaskStatus.QUEUED);
        taskRepository.save(task);

        // delay = scheduledTime - currentTime
        long delayMillis = Duration.between(LocalDateTime.now(), task.getScheduledTime()).toMillis();
        if (delayMillis < 0)
            delayMillis = 0; // set past due tasks to be executed presently

        // print statements simulating logs on each layer, modify later to actually log
        // in a file
        System.out.println("[QUEUED] TASK WITH ID: " + task.getId()
                + " FIRES IN " + delayMillis + "ms");

        // calls scheduler to append @task to internal blocking queue to be executed in
        // @delayMillies ms
        scheduler.schedule(() -> executeTask(task), delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * method simulating task/script execution to be used as the run() method by @scheduler
     * @Transactional tells spring to treat function as a database transaction
     *
     * Retry on failure, checks retryCount < maxRetries. If retriable,
     * If max retries reached, task marks FAILED.
     */
    @Transactional
    public void executeTask(Task task) {
        Task dbTask = taskRepository.findById(task.getId()).orElseThrow();
        dbTask.setStatus(TaskStatus.RUNNING);
            taskRepository.save(dbTask);

            System.out.println("[RUNNING] TASK WITH ID: " + task.getId()    
                    + " ON THREAD: " + Thread.currentThread().getName()
                    + " INITIATED AT: " + LocalDateTime.now());

            // simulating script execution we just make the jvm wait lol
            // maybe we use ProcessBuilder to add real tasks later
            try {
                Thread.sleep(2000);

                // mark completed
                dbTask.setStatus(TaskStatus.COMPLETED);
                taskRepository.save(dbTask);
                System.out.println("[COMPLETED] TASK " + task.getId() + " ON THREAD: " + Thread.currentThread().getName());
            } catch (Exception e) {
                retryTaskOrElseFailed(dbTask, e);
            }
    }

    /**
     * retry task or mark failed if retries exhausted
     * retry delay = baseDelay * 2^retryCount (2s, 4s, 8s, ...)
     */
    private void retryTaskOrElseFailed(Task task, Exception e) {
        if (task.getRetryCount() < task.getMaxRetries()) {
            task.setRetryCount(task.getRetryCount() + 1);
            task.setStatus(TaskStatus.RETRYING);
            taskRepository.save(task);

            long backoffDelay = baseDelayMs * (long) Math.pow(2, task.getRetryCount());

            System.out.println("[RETRYING] TASK " + task.getId()
                    + " | attempt " + task.getRetryCount() + "/" + task.getMaxRetries()
                    + " | backoff " + backoffDelay + "ms"
                    + " | ERROR: " + e.getMessage());

            scheduler.schedule(() -> executeTask(task), backoffDelay, TimeUnit.MILLISECONDS);
        } else {
            task.setStatus(TaskStatus.FAILED);
            taskRepository.save(task);
            System.out.println("[FAILED] TASK " + task.getId()
                    + " | MARKED FAILED AFTER " + task.getMaxRetries() + " RETRIES"
                    + " | ERROR: " + e.getMessage());
        }
    }

    /**
     * TODO:
     * frontend should schedule task
     * 
     * there exists a thread (main application thread) outside the
     * scheduledExecutorService threadpool
     * that is responsible for running spring, api transactions (unless main thread
     * delegates this to other threadpool)
     * and scheduler.
     *
     * there may be a delay in scheduling by the main app thread such that
     * scheduled time becomes in the past as it is scheduling. sometimes script
     * execution is time critical
     *
     * blocking queue is internally being sorted as soon as new tasks come which
     * also creates delay
     *
     * OOM: out of memory
     *
     * rate limiting: 10reqs/5s != 2reqs/sec should be at api layer
     *
     * do ui also same week as rate limiting
     *
     * user should be able to fill up text boxes with data and mention how much
     * delay they want in secs
     * and after that delay something should pop up on the ui
     * basically ui shows when task is completed
     *
     *
     * this is tough because frontend can call/locate backend but backend cannot
     * locate frontend
     *
     * also we're gonna be rate limiting at controller/api layer to avoid bs computations at service layer
     * */
}