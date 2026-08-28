package dts.com.examination;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class ExaminationApplication {

	@Bean
	public NewTopic learningResultsTopic() {
		return TopicBuilder.name("learning-results")
				.partitions(3)
				.replicas(1)
				.build();
	}

	public static void main(String[] args) {
		SpringApplication.run(ExaminationApplication.class, args);
	}

}
