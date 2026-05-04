package com.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.app")
public class Application {

    public static void main(String[] args) {
        System.setProperty("user.timezone", "Asia/Ho_Chi_Minh");

        SpringApplication.run(Application.class, args);
    }
}
