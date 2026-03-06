package in.tracking.moneymanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Money Manager.
 *
 * MongoDB auto-configuration is excluded by default.
 * When MONGODB_URI is set, MongoConfig.java will manually configure MongoDB.
 * This prevents connection errors when running without MongoDB.
 */
@EnableScheduling
@SpringBootApplication(excludeName = {
    "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
    "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
    "org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration",
    "org.springframework.boot.mongodb.actuate.autoconfigure.MongoHealthContributorAutoConfiguration",
    "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration",
    "org.springframework.boot.mongodb.autoconfigure.MongoDataAutoConfiguration"
})
public class MoneymanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoneymanagerApplication.class, args);
	}

}
