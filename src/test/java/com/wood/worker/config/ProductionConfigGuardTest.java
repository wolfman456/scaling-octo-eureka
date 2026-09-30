package com.wood.worker.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigGuardTest {

    private static final String PG_URL = "jdbc:postgresql://db.internal:5432/gallery";

    private ProductionConfigGuard guard(boolean h2Console, String datasourceUrl, String origins) {
        return new ProductionConfigGuard(h2Console, datasourceUrl, origins);
    }

    private ProductionConfigGuard healthyGuard() {
        return guard(false, PG_URL, "https://sixkidscrafts.up.railway.app");
    }

    @Test
    void passesWhenProductionConfigurationIsComplete() {
        assertDoesNotThrow(healthyGuard()::verify);
    }

    @Test
    void passesWhenSeveralRealOriginsAreConfigured() {
        assertDoesNotThrow(() ->
                guard(false, PG_URL, "https://a.example, https://b.example").verify());
    }

    @Test
    void refusesH2ConsoleEnabled() {
        var error = assertThrows(IllegalStateException.class,
                () -> guard(true, PG_URL, "https://sixkidscrafts.up.railway.app").verify());
        assertTrue(error.getMessage().contains("spring.h2.console.enabled is true"), error.getMessage());
    }

    @Test
    void refusesNonPostgresDatasource() {
        var error = assertThrows(IllegalStateException.class,
                () -> guard(false, "jdbc:h2:file:./data/gallery", "https://sixkidscrafts.up.railway.app").verify());
        assertTrue(error.getMessage().contains("spring.datasource.url points at jdbc:h2"), error.getMessage());
        assertTrue(error.getMessage().contains("instead of PostgreSQL"), error.getMessage());
    }

    @Test
    void refusesUnsetDatasource() {
        var error = assertThrows(IllegalStateException.class,
                () -> guard(false, null, "https://sixkidscrafts.up.railway.app").verify());
        assertTrue(error.getMessage().contains("spring.datasource.url points at unset"), error.getMessage());
    }

    @Test
    void redactsCredentialsFromReportedDatasource() {
        var error = assertThrows(IllegalStateException.class,
                () -> guard(false, "jdbc:h2:mem:gallery;USER=sa;PASSWORD=hunter2", "https://a.example").verify());
        assertFalse(error.getMessage().contains("hunter2"), error.getMessage());
    }

    @Test
    void describesDatasourceWithoutLeakingHostOrCredentials() {
        assertEquals("postgresql",
                healthyGuard().describeDatasource());
        assertEquals("postgresql",
                guard(false, "jdbc:postgresql://user:hunter2@db.internal:5432/gallery", "https://a.example")
                        .describeDatasource());
        assertEquals("jdbc:h2",
                guard(false, "jdbc:h2:file:./data/gallery", "https://a.example").describeDatasource());
        assertEquals("jdbc:mysql",
                guard(false, "jdbc:mysql://user:hunter2@db.internal:3306/gallery", "https://a.example")
                        .describeDatasource());
        assertEquals("unset", guard(false, null, "https://a.example").describeDatasource());
        assertEquals("unset", guard(false, "   ", "https://a.example").describeDatasource());
    }

    @Test
    void refusesEmptyOrigins() {
        var error = assertThrows(IllegalStateException.class, () -> guard(false, PG_URL, " , ").verify());
        assertTrue(error.getMessage().contains("app.cors.allowed-origins is empty"), error.getMessage());
    }

    @Test
    void refusesLocalhostOnlyOrigins() {
        var error = assertThrows(IllegalStateException.class,
                () -> guard(false, PG_URL, "http://localhost:5173, http://127.0.0.1:4173").verify());
        assertTrue(error.getMessage().contains("only lists local origins"), error.getMessage());
    }

    @Test
    void refusesNullOrigins() {
        var error = assertThrows(IllegalStateException.class, () -> guard(false, PG_URL, null).verify());
        assertTrue(error.getMessage().contains("app.cors.allowed-origins is empty"), error.getMessage());
    }

    @Test
    void reportsEveryProblemAtOnce() {
        var error = assertThrows(IllegalStateException.class, () -> guard(true, "jdbc:h2:mem:x", "").verify());
        assertTrue(error.getMessage().contains("Refusing to start in production"), error.getMessage());
        assertEquals(3, error.getMessage().split("; ").length, error.getMessage());
    }

    @Test
    void acceptsOneRealOriginAmongLocalOnes() {
        assertDoesNotThrow(() ->
                guard(false, PG_URL, "http://localhost:5173, https://sixkidscrafts.up.railway.app").verify());
    }

    @Test
    void localOriginDetectionCoversSchemesPortsAndIpv6() {
        assertEquals(List.of(true, true, true, true, true, false, false, false, false),
                List.of(
                        ProductionConfigGuard.isLocalOrigin("http://localhost:5173"),
                        ProductionConfigGuard.isLocalOrigin("https://LOCALHOST"),
                        ProductionConfigGuard.isLocalOrigin("http://127.0.0.1"),
                        ProductionConfigGuard.isLocalOrigin("http://[::1]:8080"),
                        ProductionConfigGuard.isLocalOrigin("  http://0.0.0.0:5173  "),
                        ProductionConfigGuard.isLocalOrigin("https://sixkidscrafts.up.railway.app"),
                        ProductionConfigGuard.isLocalOrigin("https://localhost.evil.example"),
                        ProductionConfigGuard.isLocalOrigin("https://my-localhost.dev"),
                        ProductionConfigGuard.isLocalOrigin("https://example.com/redirect?to=localhost")));
    }

    @Test
    void runDelegatesToVerify() {
        assertThrows(IllegalStateException.class, () -> guard(true, PG_URL, "https://a.example").run(null));
    }
}
