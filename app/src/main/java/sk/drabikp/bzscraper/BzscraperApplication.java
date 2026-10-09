package sk.drabikp.bzscraper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BzscraperApplication {

	public static void main(String[] args) {
		SpringApplication.run(BzscraperApplication.class, args);
	}

}
