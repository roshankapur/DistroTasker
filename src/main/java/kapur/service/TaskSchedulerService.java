package kapur.service;

import jakarta.annotation.PostConstruct;
import kapur.model.Task;
import kapur.model.TaskStatus;
import kapur.repository.TaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
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
//MockTaskRepository instance is injected by spring

//investigate more threads in JVM used than available from OS and its management
@Service
public class TaskSchedulerService {
    private final TaskRepository taskRepository;
    private final ScheduledExecutorService scheduler;

    public TaskSchedulerService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
        this.scheduler = Executors.newScheduledThreadPool(getAvailableCoreCount());
    }

    // this method will later replace hardcoded thread count in constructor
    // i wanna figure out method behaviour on initialization verses if available
    // threads can be somehow changed
    public static int getAvailableCoreCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Starup recovery /"hydration" logic
     * runs once after Spring initializes the class
     * DB is queried for any QUEUED tasks left over
     * from previous run and requeues them to ScheduledExecutorService.
     */
    @PostConstruct
    public void recoverTasksOnStartup() {
        List<Task> queuedTasks = taskRepository.findByStatus(TaskStatus.QUEUED);

        if (queuedTasks.isEmpty()) {
            System.out.println("[RECOVERY] NO QUEUED TASKS FOUND. INITIATING CLEAN START");
        } else {
            System.out.println(
                    "[RECOVERY] FOUND " + queuedTasks.size() + " TASKS QUEUED IN DB — INITIATING REQUEUED START");

            for (Task task : queuedTasks) {
                long delayMillis = Duration.between(LocalDateTime.now(), task.getScheduledTime()).toMillis();
                if (delayMillis < 0)
                    delayMillis = 0; // set past due tasks to be executed asap

                System.out.println("[RECOVERY] TASK WITH ID: " + task.getId()
                        + " FIRES IN" + delayMillis + "ms");
                scheduler.schedule(() -> executeTask(task), delayMillis, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * adds task marked pending to repository
     * and calls @scheduler (ScheduledExececutorService) to use executeTask() method
     */
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

    // method simulating task/script execution to be used as the run() method by
    // @scheduler
    // @Transactional tells spring to treat function as a database transaction
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
            // mark failed
            dbTask.setStatus(TaskStatus.FAILED);
            taskRepository.save(dbTask);
            System.out.println("[FAILED] Task " + task.getId() + " ERROR: " + e.getMessage());
        }
    }

    /**
     * //commented out cause spring scheduling is not using ScheduledExecutorService
     * and its threadpool
     * //method used by spring to execute scheduled tasks every 2000ms
     * 
     * @Scheduled (fixedRate = 2000)
     *            public void pollAndUpdate(){}
     */

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