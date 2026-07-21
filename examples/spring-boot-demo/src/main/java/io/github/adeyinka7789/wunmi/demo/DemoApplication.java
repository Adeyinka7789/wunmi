package io.github.adeyinka7789.wunmi.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A minimal Spring Boot app wired with wunmi. Run it and:
 *
 * <ul>
 *   <li>{@code GET /api/dashboard} — reads {@code NEW_DASHBOARD} through {@link org.springframework
 *       .web.bind.annotation.RestController} code.</li>
 *   <li>{@code GET /api/export?userId=alice} — gated by {@code @RequiresFlag} with a SpEL subject;
 *       returns 404 when the flag is off for that user.</li>
 *   <li>{@code GET /wunmi/admin} — the bundled admin console to flip flags and add overrides.</li>
 * </ul>
 *
 * Storage is the bundled JDBC store over in-memory H2 (schema auto-created; see
 * {@code application.yml}). Two flags are seeded at startup by {@link DemoConfig}.
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
