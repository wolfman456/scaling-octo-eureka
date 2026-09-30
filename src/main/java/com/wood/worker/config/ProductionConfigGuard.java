package com.wood.worker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Refuses to boot in the production profile when a deployment mistake would leave the
 * service wide open: the H2 console enabled, a non-PostgreSQL (ephemeral) datasource, or
 * the dev-only CORS origin left in place. Every problem found is reported at once so a
 * misconfigured deploy is diagnosed in one pass instead of one restart per mistake.
 */
@Component
@Profile("production")
public class ProductionConfigGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionConfigGuard.class);

    private static final String POSTGRES_URL_PREFIX = "jdbc:postgresql:";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1");

    private final boolean h2ConsoleEnabled;
    private final String datasourceUrl;
    private final String allowedOrigins;

    public ProductionConfigGuard(@Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled,
                                 @Value("${spring.datasource.url:}") String datasourceUrl,
                                 @Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        this.h2ConsoleEnabled = h2ConsoleEnabled;
        this.datasourceUrl = datasourceUrl == null ? "" : datasourceUrl.trim();
        this.allowedOrigins = allowedOrigins == null ? "" : allowedOrigins;
    }

    @Override
    public void run(ApplicationArguments args) {
        verify();
    }

    void verify() {
        List<String> problems = new ArrayList<>();

        if (h2ConsoleEnabled) {
            problems.add("spring.h2.console.enabled is true (an unauthenticated database console "
                    + "must never be reachable in production)");
        }
        if (!datasourceUrl.startsWith(POSTGRES_URL_PREFIX)) {
            problems.add("spring.datasource.url points at " + describeDatasource()
                    + " instead of PostgreSQL (an embedded database starts empty on every deploy)");
        }

        List<String> origins = WebConfig.parseAllowedOrigins(allowedOrigins);
        if (origins.isEmpty()) {
            problems.add("app.cors.allowed-origins is empty, so the admin UI cannot call this API");
        } else if (origins.stream().allMatch(ProductionConfigGuard::isLocalOrigin)) {
            problems.add("app.cors.allowed-origins only lists local origins " + origins
                    + "; set APP_CORS_ALLOWED_ORIGINS to the deployed frontend origin");
        }

        log.info("Production configuration check: h2ConsoleEnabled={}, datasource={}, corsOrigins={}",
                h2ConsoleEnabled, describeDatasource(), origins);

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start in production: "
                    + String.join("; ", problems));
        }
    }

    String describeDatasource() {
        if (datasourceUrl.isEmpty()) {
            return "unset";
        }
        if (datasourceUrl.startsWith(POSTGRES_URL_PREFIX)) {
            return "postgresql";
        }
        return datasourceUrl.replaceFirst("^(jdbc:[a-z0-9]+):.*$", "$1")
                .replaceAll("(?i)(//)[^/@]*@", "$1");
    }

    static boolean isLocalOrigin(String origin) {
        String host = origin.trim().toLowerCase(Locale.ROOT)
                .replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        if (host.startsWith("[")) {
            int close = host.indexOf(']');
            host = close < 0 ? host : host.substring(1, close);
        } else {
            host = host.split("[:/]", 2)[0];
        }
        return LOCAL_HOSTS.contains(host);
    }
}
