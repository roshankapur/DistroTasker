package kapur.repository;

import kapur.model.Task;
import kapur.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Task fields
 * private UUID id;
 * private String scriptPath;
 * private LocalDateTime scheduledTime;
 * private TaskStatus status;
 */

/* we need to be able to "trash" scripts after they're marked done
 * through some form of marked autodelete when the backend crawls through the database.
 * an entire database crawl could be computationally complex
 * --> there should be a periodic DELETE WHERE trash=true call from frontend to keep backend clean
 * --> i think we're gonna be able to do this in rate limiting 201
 * */

/** Repo now works like this:
 * JpaRepository<Task, UUID> autowires save(), findById(), findAll(), deleteById(), count()

 * save(Task task) --> JpaRepository.save()
 * findByID(UUID id) --> JpaRepository.findById() returns Optional<Task>
 * findAll() --> JpaRepository.findAll()
 * deleteById(UUID id) --> JpaRepository.deleteById()

 * updateScriptPath, updateScheduledTime, updateTaskStatus are gonna work as findById --> modify --> save because of SQL
 * findPending() --> findByStatus(TaskStatus.PENDING)
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByStatus(TaskStatus status);
}
