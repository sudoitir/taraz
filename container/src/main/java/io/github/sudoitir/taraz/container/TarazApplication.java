package io.github.sudoitir.taraz.container;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Composition root and application entry point (ADR-0006). */
@SpringBootApplication(scanBasePackages = "io.github.sudoitir.taraz")
public class TarazApplication {

    public static void main(String[] args) {
        SpringApplication.run(TarazApplication.class, args);
    }
}
