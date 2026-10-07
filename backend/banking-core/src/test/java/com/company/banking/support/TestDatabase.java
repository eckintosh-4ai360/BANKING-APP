package com.company.banking.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One real PostgreSQL 18 server per test JVM, provisioned exactly like production: a schema-owner role for Flyway
 * and a restricted runtime role (not owner, not superuser, no BYPASSRLS) for the application, so row-level security
 * is genuinely exercised.
 *
 * <p>Uses Testcontainers when Docker is available (CI) and embedded PostgreSQL binaries otherwise. Force a mode
 * with {@code -Dbanking.test.database=docker|embedded}.
 */
public final class TestDatabase {

    public static final String DATABASE = "banking";
    public static final String MIGRATOR_USER = "banking_migrator";
    public static final String MIGRATOR_PASSWORD = "migrator_test";
    public static final String APP_USER = "banking_app";
    public static final String APP_PASSWORD = "app_test";

    private static Server server;

    private TestDatabase() {
    }

    public static synchronized Server get() {
        if (server == null) {
            server = start();
            provision(server);
        }
        return server;
    }

    private static Server start() {
        String mode = System.getProperty("banking.test.database", "auto");
        boolean docker = switch (mode) {
            case "docker" -> true;
            case "embedded" -> false;
            default -> isDockerAvailable();
        };
        return docker ? startContainer() : startEmbedded();
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @SuppressWarnings("resource")
    private static Server startContainer() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:18-alpine")
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword("postgres");
        container.start();
        String base = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/";
        return new Server(base, "postgres", "postgres");
    }

    private static Server startEmbedded() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // JVM is exiting
                }
            }));
            return new Server("jdbc:postgresql://localhost:" + postgres.getPort() + "/", "postgres", "postgres");
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not start embedded PostgreSQL", ex);
        }
    }

    /**
     * Mirrors infrastructure/postgres/init/01-roles.sh.
     */
    private static void provision(Server server) {
        try (Connection connection = DriverManager.getConnection(server.baseUrl() + "postgres",
                server.superUser(), server.superPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + MIGRATOR_USER + " LOGIN PASSWORD '" + MIGRATOR_PASSWORD + "'");
            statement.execute("CREATE ROLE " + APP_USER + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
            statement.execute("CREATE DATABASE " + DATABASE + " OWNER " + MIGRATOR_USER);
            statement.execute("REVOKE ALL ON DATABASE " + DATABASE + " FROM PUBLIC");
            statement.execute("GRANT CONNECT ON DATABASE " + DATABASE + " TO " + APP_USER);
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not provision test database roles", ex);
        }
        try (Connection connection = DriverManager.getConnection(server.baseUrl() + DATABASE,
                server.superUser(), server.superPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            statement.execute("CREATE SCHEMA core AUTHORIZATION " + MIGRATOR_USER);
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not provision test schema", ex);
        }
    }

    public record Server(String baseUrl, String superUser, String superPassword) {

        public String bankingUrl() {
            return baseUrl + DATABASE;
        }
    }
}
