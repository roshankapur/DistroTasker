package kapur.controller;

import kapur.model.Task;
import kapur.model.TaskStatus;
import kapur.repository.TaskRepository;
import kapur.service.RateLimiter;
import kapur.service.TaskSchedulerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All endpoints are under /api/tasks --> submit tasks via HTTP.
 *
 * @CrossOrigin allows the frontend on port 5500 to call API on port 8080
 * without being blocked by the browser's same-origin policy
 *
 * Rate limiter present at api layer to restrict toomanyrequest before any service/DB operation or access.
 */
@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskSchedulerService schedulerService;
    private final TaskRepository taskRepository;
    private final RateLimiter rateLimiter;

    // Spring injects all three beans via constructor injection
    public TaskController(TaskSchedulerService schedulerService, TaskRepository taskRepository,RateLimiter rateLimiter) {
        this.schedulerService = schedulerService;
        this.taskRepository = taskRepository;
        this.rateLimiter = rateLimiter;
    }

    /**
     * POST /api/tasks
     *
     * This is the gate guarded by RateLimiter if token bucket is empty,
     * requests are rejected with 429 TMR before DB or scheduler operations.
     *
     * Example request AIGEN CMD:
     * Invoke-RestMethod -Uri "http://localhost:8080/api/tasks" -Method POST -ContentType "application/json" -Body '{"scriptPath": "/scripts/my_test_job.sh", "scheduledTime": "2026-05-29T15:00:00"}'
     */
    @PostMapping
    public ResponseEntity<?> createTask(@RequestBody Task task) {
        if (!rateLimiter.tryConsume()) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)  //429
                    .body(Map.of(
                            "error", "[API] RATE LIMIT EXCEEDED. TRY AGAIN SHORTLY",
                            "status", 429
                    ));
        }

        task.setId(UUID.randomUUID()); //server generated random id
        schedulerService.scheduleTask(task);

        // HTTP 201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    /**
     * GET /api/tasks
     * Get all tasks
     */
    @GetMapping
    public ResponseEntity<List<Task>> getAllTasks() {
        List<Task> tasks = taskRepository.findAll();
        return ResponseEntity.ok(tasks);
    }

    /**
     * GET /api/tasks/{id}
     * Returns 404 if task DNE
     */
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable UUID id) {
        Task task = taskRepository.findById(id).orElseThrow();
        return ResponseEntity.ok(task);
    }

    /**
     * DELETE /api/tasks/{id}
     * @Returns 204 No Content on success.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        taskRepository.findById(id).orElseThrow();
        taskRepository.deleteById(id);
        return ResponseEntity.noContent().build();   // HTTP 204 No Content
    }
}
