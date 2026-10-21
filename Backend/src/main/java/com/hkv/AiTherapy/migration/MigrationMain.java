package com.hkv.AiTherapy.migration;

import org.flywaydb.core.Flyway;

/** Runs only Flyway migrations; no Spring context, HTTP listener, or background jobs. */
public final class MigrationMain {

    private MigrationMain() { }

    public static void main(String[] args) {
        if (args.length != 0) {
            System.err.println("Migration command accepts no arguments");
            System.exit(2);
        }
        String url = required("DATABASE_URL");
        String username = required("DATABASE_USER");
        String password = required("DATABASE_PASSWORD");
        try {
            var result = Flyway.configure()
                    .dataSource(url, username, password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();
            System.out.println("Flyway migration complete: " + result.migrationsExecuted + " migration(s) applied");
        } catch (Exception exception) {
            System.err.println("Flyway migration failed; inspect database connectivity and migration history");
            System.exit(1);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            System.err.println("Missing required migration setting: " + name);
            System.exit(2);
        }
        return value;
    }
}
