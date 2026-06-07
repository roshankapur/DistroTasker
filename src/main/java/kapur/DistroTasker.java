package kapur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

//@EnableAsync makes Spring create a managed thread pool for async method calls
@SpringBootApplication
@EnableAsync
//@EnableScheduling //commented out cause spring scheduling is not using ScheduledExecutorService and its threadpool
public class DistroTasker {

	public static void main(String[] args) {
		SpringApplication.run(DistroTasker.class, args);
	}

}
