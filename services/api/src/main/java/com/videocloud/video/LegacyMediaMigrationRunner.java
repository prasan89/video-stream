package com.videocloud.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Explicit opt-in entry point for the one-time legacy media migration.
 * Disabled unless videocloud.legacy-media-migration.enabled=true is supplied.
 */
@Component
@ConditionalOnProperty(name = "videocloud.legacy-media-migration.enabled", havingValue = "true")
public class LegacyMediaMigrationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyMediaMigrationRunner.class);
    private final LegacyMediaMigrationService migration;
    private final Environment environment;

    public LegacyMediaMigrationRunner(LegacyMediaMigrationService migration, Environment environment) {
        this.migration = migration;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean dryRun = environment.getProperty("videocloud.legacy-media-migration.dry-run", Boolean.class, true);
        log.warn("Legacy media migration explicitly enabled; dryRun={}", dryRun);
        LegacyMediaMigrationService.MigrationReport report = migration.migrate(dryRun);
        log.warn("Legacy media migration finished: {}", report);
        if (report.failures() > 0) {
            throw new IllegalStateException("Legacy media migration reported " + report.failures() + " failures");
        }
    }
}
