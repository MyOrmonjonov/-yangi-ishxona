package org.example.yangi_ishxona;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class YangiIshxonaApplication {

    public static void main(String[] args) {
        loadDotEnvIntoSystemProperties();
        SpringApplication.run(YangiIshxonaApplication.class, args);
    }

    /**
     * Loads KEY=VALUE pairs from a local .env file (gitignored - see README) into system
     * properties before the Spring context starts, so application.properties placeholders
     * like ${TELEGRAM_BOT_TOKEN} resolve without needing real env vars during local dev.
     * Real environment variables (as set on Railway) always take precedence.
     */
    private static void loadDotEnvIntoSystemProperties() {
        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(envFile);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Не удалось прочитать .env: " + e.getMessage());
        }
    }

}
