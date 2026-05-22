package kapur.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Lombok autowires getters, setters, toString and other utils with annotations*/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Task {
    private UUID id;
    private String scriptPath;
    private LocalDateTime scheduledTime;
    private TaskStatus status;
    //might have to write run function through class Task implements Runnable to use in thread pool
}
