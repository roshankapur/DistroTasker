package kapur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
//@EnableScheduling //commented out cause spring scheduling is not using ScheduledExecutorService and its threadpool
public class DistroTasker {

	public static void main(String[] args) {
		SpringApplication.run(DistroTasker.class, args);
	}

}
