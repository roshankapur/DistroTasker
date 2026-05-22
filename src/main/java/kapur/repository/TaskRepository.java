package kapur.repository;

import kapur.model.Task;
import kapur.model.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Task fields
 * private UUID id;
 * private String scriptPath;
 * private LocalDateTime scheduledTime;
 * private TaskStatus status;
 */
/** we need to be able to "trash" scripts after they're marked done
 * through some form of marked autodelete when the backend crawls through the database.
 * an entire database crawl could be computationally complex
 * --> there should be a periodic DELETE WHERE trash=true call from frontend to keep backend clean*/
public interface TaskRepository {
    void save(Task task);
    Task findByID(UUID id);
    List<Task> findAll();
    List<Task> findPending();
    void deleteById(UUID id);
    //add update functions
    void updateScriptPath(UUID id, String newScriptPath);
    void updateScheduledTime(UUID id, LocalDateTime newScheduledTime);
    void updateTaskStatus(UUID id, TaskStatus newTaskStatus);
}
