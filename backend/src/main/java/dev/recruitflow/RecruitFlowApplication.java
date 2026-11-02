package dev.recruitflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RecruitFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecruitFlowApplication.class, args);
    }
}
