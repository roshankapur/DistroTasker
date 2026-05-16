package kapur.repository;

import kapur.model.Task;
import kapur.model.TaskStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//used AI here a little for example on how to implement the repository

/**
 * TaskRepository methods
 * void save(Task task);
 * Task findByID(UUID uuid);
 * List<Task> findAll();
 * void deleteById(UUID id);
 * void updateScriptPath(UUID id, String newScriptPath);
 * void updateScheduledTime(UUID id, LocalDateTime newScheduledTime);
 * void updateTaskStatus(UUID id, TaskStatus newTaskStatus);
 */
@Repository
public class MockTaskRepository implements TaskRepository {
    private final ConcurrentHashMap<UUID, Task> db = new ConcurrentHashMap<>();

    @Override
    public void save(Task task){
        if(!db.containsKey(task.getId())){
            db.put(task.getId(),task);
            System.out.println("SUCCESS SAVE FOR TASK:" + task.toString());
        }else{
            System.out.println("FAILED SAVE TASK FOR TASK: "+ task.toString());
        }
    }

    @Override
    public Task findByID(UUID id) {
        if(db.containsKey(id)) {
            return db.get(id);
        }else{
            return new Task();
        }
    }

    @Override
    public List<Task> findAll() {
        return new ArrayList<Task>(db.values());
    }

    @Override
    public void deleteById(UUID id){
        if(db.containsKey(id)){
            db.remove(id);
            System.out.println("DELETED TASK WITH UUID: " + id);
        }else{
            System.out.println("CANNOT DELETE, DID NOT FIND TASK WITH UUID:" + id);
        }
    }

    @Override
    public void updateScriptPath(UUID id, String newScriptPath){
        if(db.containsKey(id)){
            db.get(id).setScriptPath(newScriptPath);
            System.out.println("SET scriptPath TO " + newScriptPath + " FOR TASK WITH UUID: " + id);
        }else {
            System.out.println("CANNOT UPDATE, DID NOT FIND TASK WITH UUID:" + id);
        }
    }

    @Override
    public void updateScheduledTime(UUID id, LocalDateTime newScheduledTime){
        if(db.containsKey(id)){
            db.get(id).setScheduledTime(newScheduledTime);
            System.out.println("SET scheduledTime TO " + newScheduledTime + " FOR TASK WITH UUID: " + id);
        }else {
            System.out.println("CANNOT UPDATE, DID NOT FIND TASK WITH UUID:" + id);
        }
    }

    @Override
    public void updateTaskStatus(UUID id, TaskStatus newTaskStatus){
        if(db.containsKey(id)){
            db.get(id).setStatus(newTaskStatus);
            System.out.println("SET status TO " + newTaskStatus + " FOR TASK WITH UUID: " + id);
        }else {
            System.out.println("CANNOT UPDATE, DID NOT FIND TASK WITH UUID:" + id);
        }
    }
}
