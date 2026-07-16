package io.github.adeyinka7789.wunmi.jdbc;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the wunmi tables if they don't already exist, from the bundled portable
 * {@code schema.sql} ({@code CREATE TABLE IF NOT EXISTS}). Call once at startup — idempotent.
 *
 * <pre>{@code
 * WunmiSchema.initialize(dataSource);
 * FlagStore store = new JdbcFlagStore(dataSource);
 * }</pre>
 */
public final class WunmiSchema {

    private static final String SCHEMA_RESOURCE = "io/github/adeyinka7789/wunmi/jdbc/schema.sql";

    private WunmiSchema() {
    }

    /** Execute the bundled schema against {@code dataSource}. Idempotent. */
    public static void initialize(DataSource dataSource) {
        for (String statement : readStatements()) {
            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute(statement);
            } catch (SQLException e) {
                throw new FlagStoreException("Failed to initialize wunmi schema", e);
            }
        }
    }

    private static List<String> readStatements() {
        try (InputStream in = WunmiSchema.class.getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new FlagStoreException("Bundled schema not found: " + SCHEMA_RESOURCE, null);
            }
            StringBuilder sql = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                        continue;
                    }
                    sql.append(line).append('\n');
                }
            }
            List<String> statements = new ArrayList<>();
            for (String part : sql.toString().split(";")) {
                if (!part.isBlank()) {
                    statements.add(part.strip());
                }
            }
            return statements;
        } catch (IOException e) {
            throw new FlagStoreException("Failed to read bundled schema", e);
        }
    }
}
