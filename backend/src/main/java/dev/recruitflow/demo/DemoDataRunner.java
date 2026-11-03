package dev.recruitflow.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Loads the synthetic demo dataset at startup when {@code recruitflow.demo.enabled=true} (off by default). */
@Component
@ConditionalOnProperty(name = "recruitflow.demo.enabled", havingValue = "true")
class DemoDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataRunner.class);

    private final DemoDataSeeder seeder;

    DemoDataRunner(DemoDataSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        DemoDataSeeder.Summary summary = seeder.seed();
        if (summary == null) {
            log.info("Demo data not loaded: clients already exist.");
        } else {
            log.info("Demo data loaded: {}", summary);
        }
    }
}
