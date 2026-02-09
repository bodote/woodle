package io.github.bodote.woodle;

import io.github.bodote.woodle.config.ThymeleafRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(ThymeleafRuntimeHints.class)
public class WoodleApplication {

    public static void main(String[] args) {
        SpringApplication.run(WoodleApplication.class, args);
    }
}
