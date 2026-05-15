package kapur.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Task {
    private UUID id;
    private String scriptPath;
    LocalDateTime scheduledTime;
    TaskStatus status;

}
