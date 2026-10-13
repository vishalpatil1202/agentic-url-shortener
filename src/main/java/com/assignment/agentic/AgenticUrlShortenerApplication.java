package com.assignment.agentic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class AgenticUrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticUrlShortenerApplication.class, args);
    }
}
