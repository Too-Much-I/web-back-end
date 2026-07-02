package web.tosunsaeng;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "web.tosunsaeng.domain.exams.domain.repository")
public class TosunsaengApplication {

	public static void main(String[] args) {
		SpringApplication.run(TosunsaengApplication.class, args);
	}

}
