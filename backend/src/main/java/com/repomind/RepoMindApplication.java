package com.repomind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RepoMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepoMindApplication.class, args);
    }
}
