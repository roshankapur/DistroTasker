package kapur.client;

import kapur.model.Task;
import kapur.model.TaskStatus;
import kapur.service.TaskSchedulerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;


//WRITTEN BY CLAUDE
/**
 * Mock client for 101 graduation — schedules 5 tasks with staggered times
 * to prove ScheduledExecutorService runs them in parallel across different threads.
 *
 * Spring auto-discovers this @Component and calls run() after the app boots.
 * Will be replaced with real CLI/API client in later modules.
 */
@Component
public class MockTaskRunner implements CommandLineRunner {

    private final TaskSchedulerService schedulerService;

    public MockTaskRunner(TaskSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== MockTaskRunner: Scheduling 5 tasks ===");

        for (int i = 1; i <= TaskSchedulerService.getAvailableCoreCount()-1; i++) {
            Task task = new Task(
                    UUID.randomUUID(),
                    "/scripts/mock_job_" + i + ".sh",
                    LocalDateTime.now().plusSeconds(i * 3L),  // fire at 3s, 6s, 9s, 12s, 15s
                    TaskStatus.PENDING
            );
            schedulerService.scheduleTask(task);
        }

        System.out.println("=== MockTaskRunner: All 5 tasks submitted to scheduler ===");

        // Keep the JVM alive long enough for the last task (15s delay + 2s execution) to finish
        // Without this, Spring Boot may shut down before the executor threads complete
        Thread.sleep(20000);

        System.out.println("=== MockTaskRunner: Demo complete ===");
    }
}
