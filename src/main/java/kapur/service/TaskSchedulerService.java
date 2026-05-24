package kapur.service;

import kapur.model.Task;
import kapur.model.TaskStatus;
import kapur.repository.TaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
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

    //this method will later replace hardcoded thread count in constructor
    //i wanna figure out method behaviour on initialization verses if available
    //threads can be somehow changed
    public static int getAvailableCoreCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**adds task marked pending to repository
     * and calls @scheduler (ScheduledExececutorService) to use executeTask() method*/
    public void scheduleTask(Task task){
        task.setStatus(TaskStatus.PENDING);
        taskRepository.save(task);

        //delay = scheduledTime - currentTime
        long delayMillis = Duration.between(LocalDateTime.now(), task.getScheduledTime()).toMillis();
        if(delayMillis < 0) delayMillis = 0; //set past due tasks to be executed presently

        //print statements simulating logs on each layer, modify later to actually log in a file
        System.out.println("[SCHEDULED] TASK WITH ID: "+task.getId()
                            +" FIRES IN "+delayMillis+"ms");

        //calls scheduler to append @task to internal blocking queue to be executed in @delayMillies ms
        scheduler.schedule(() -> executeTask(task), delayMillis, TimeUnit.MILLISECONDS);
    }

    //method simulating task/script execution to be used as the run() method by @scheduler
    public void executeTask(Task task){
        taskRepository.updateTaskStatus(task.getId(), TaskStatus.RUNNING);
        System.out.println("[RUNNING] TASK WITH ID: " +task.getId()
                + " ON THREAD: " +Thread.currentThread().getName()
                + " INITIATED AT: " + LocalDateTime.now());

        //simulating script execution we just make the jvm wait lol
        //maybe we use ProcessBuilder to add real tasks later
        try {
            Thread.sleep(2000);

            //mark completed
            taskRepository.updateTaskStatus(task.getId(), TaskStatus.COMPLETED);
            System.out.println("[COMPLETED] TASK " +task.getId()+ " ON THREAD: " +Thread.currentThread().getName());
        } catch (Exception e) {
            //mark failed
            taskRepository.updateTaskStatus(task.getId(), TaskStatus.FAILED);
            System.out.println("[FAILED] Task " + task.getId() + "ERROR: " + e.getMessage());
        }
    }

    /** //commented out cause spring scheduling is not using ScheduledExecutorService and its threadpool
    //method used by spring to execute scheduled tasks every 2000ms
    @Scheduled (fixedRate = 2000)
    public void pollAndUpdate(){}
    */

    /** TODO:
     * simulate the error when threads are overburdened as new tasks are pushed to @scheduler.
     * show bhai when threadpool is full and experiences task overflow
     *
     * frontend should schedule task
     *
     * expose backend through api???
     * do api and persistence before rate limiting if dependancies allow otherwise simulate dependancies
     */
}
