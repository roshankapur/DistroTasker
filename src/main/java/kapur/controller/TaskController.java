package kapur.controller;

import kapur.model.Task;
import kapur.model.TaskStatus;
import kapur.repository.TaskRepository;
import kapur.service.TaskSchedulerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * All endpoints are under /api/tasks --> submit tasks via HTTP.
 *
 * @CrossOrigin allows the frontend on port 5500 to call API on port 8080
 * without being blocked by the browser's same-origin policy
 */
@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskSchedulerService schedulerService;
    private final TaskRepository taskRepository;

    // Spring injects both beans via constructor injection
    public TaskController(TaskSchedulerService schedulerService, TaskRepository taskRepository) {
        this.schedulerService = schedulerService;
        this.taskRepository = taskRepository;
    }

    /**
     * POST /api/tasks
     *
     * Client sends JSON body with scriptPath and scheduledTime.
     * Server generates the UUID and delegates to the scheduler.
     *
     * Example request AIGEN CMD:
     * Invoke-RestMethod -Uri "http://localhost:8080/api/tasks" -Method POST -ContentType "application/json" -Body '{"scriptPath": "/scripts/my_test_job.sh", "scheduledTime": "2026-05-29T15:00:00"}'
     */
    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
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
     * Returns 404 if task DNE using @GlobalExceptionHandler
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
        // Verify task exists before deleting (throws 404 via GlobalExceptionHandler if not)
        taskRepository.findById(id).orElseThrow();
        taskRepository.deleteById(id);

        return ResponseEntity.noContent().build();   // HTTP 204 No Content
    }
}
