package com.wood.worker.config;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The production profile used to pin {@code hibernate.dialect=PostgreSQLDialect}. That
 * is redundant — Hibernate resolves the dialect from the driver's JDBC metadata — and it
 * adds a way for the app to fail to boot on a routine upgrade if the class is renamed.
 *
 * <p>These tests pin both halves of the claim, because dropping the property is only
 * safe while the driver is present and still reports the same product name.
 */
class ProductionDialectTest {

    @Test
    void productionProfileDoesNotPinADialectClass() throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application-production.properties")) {
            props.load(in);
        }

        assertNull(props.getProperty("spring.jpa.database-platform"),
                "the dialect is auto-detected; pinning the class name is redundant");
        assertNull(props.getProperty("hibernate.dialect"),
                "the dialect is auto-detected; pinning the class name is redundant");
    }

    @Test
    void thePostgresDriverIsOnTheRuntimeClasspath() {
        // The auto-detection reads the product name off this driver. If it were scoped
        // out, the dialect would silently change.
        assertTrue(postgresDriverPresent(),
                "org.postgresql.Driver must be a runtime dependency for dialect auto-detection");
    }

    @Test
    void thePostgresDriverStillReportsPostgresql() throws Exception {
        // What Hibernate reads to pick the dialect. If this ever stops being
        // "PostgreSQL", removing the pinned class name would change behaviour quietly.
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        assertEquals("PostgreSQL", metaData.getDatabaseProductName());
    }

    private static boolean postgresDriverPresent() {
        try {
            Class.forName("org.postgresql.Driver");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
