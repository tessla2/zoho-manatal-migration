package com.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // Enable scanning for @ConfigurationProperties classes in the specified package
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
