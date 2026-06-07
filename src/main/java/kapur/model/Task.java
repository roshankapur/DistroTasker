package kapur.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Lombok autowires getters, setters, toString and other utils with annotations
 *  JPA annotations map this class to the "tasks" table in PostgreSQL
 *  Hibernate auto-generates DDL (CREATE TABLE, ALTER TABLE) from these annotations
 *
 *  JPA == jakarta persistence api
 *  Entity tag specifies Task class as a database entity/table row
 *  and obv Table tag is naming the table in postgres
 *  */

@Data
@NoArgsConstructor
@Entity
@Table(name = "tasks")
public class Task {
    @Id //mark as primary key
    private UUID id;
    private String scriptPath;
    private LocalDateTime scheduledTime;
    //jpa needs to know the datatype of enum before it wires in SQL
    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    //rate limiter use
    private int retryCount = 0;
    private int maxRetries = 3;
}
