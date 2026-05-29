package kapur.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * @ControllerAdvice makes spring use this class if api throws an error
 * basically autowiring try-catch blocks in the background
 * and if something happens we'll have an
 * http code returned so that the frontend can see it later
 *
 * i think we're gonna need to handle RateLimitExceededException --> HTTP 429 Too Many Requests
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    // Task not found — when findById().orElseThrow() fails
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> handleNotFound(NoSuchElementException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "Task not found",
                        "status", 404,
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    // Catch-all for unexpected errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", e.getMessage() != null ? e.getMessage() : "Internal server error",
                        "status", 500,
                        "timestamp", LocalDateTime.now().toString()
                ));
    }
}
