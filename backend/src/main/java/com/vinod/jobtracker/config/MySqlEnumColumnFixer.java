package com.vinod.jobtracker.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * On MySQL, Hibernate creates Java enum fields as native ENUM('A','B') columns and never updates
 * that list, so adding a value later (e.g. INDEED) would make inserts fail. This turns any such
 * column in our tables into VARCHAR(20) at startup. It does nothing once they're converted.
 */
@Component
@Profile("mysql")
public class MySqlEnumColumnFixer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MySqlEnumColumnFixer.class);
    private static final Set<String> OUR_TABLES = Set.of(
            "app_users", "job_applications", "activities", "gmail_connections", "email_records", "alert_jobs");

    private final JdbcTemplate jdbc;

    public MySqlEnumColumnFixer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> columns = jdbc.queryForList(
                "SELECT TABLE_NAME, COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND DATA_TYPE = 'enum'");
        for (Map<String, Object> column : columns) {
            String table = String.valueOf(column.get("TABLE_NAME"));
            String name = String.valueOf(column.get("COLUMN_NAME"));
            // Only our own tables, and only plain identifiers (they're put into the SQL below)
            if (!OUR_TABLES.contains(table.toLowerCase()) || !name.matches("[a-z_]+")) {
                continue;
            }
            String nullable = "YES".equals(column.get("IS_NULLABLE")) ? "NULL" : "NOT NULL";
            jdbc.execute("ALTER TABLE `" + table + "` MODIFY `" + name + "` VARCHAR(20) " + nullable);
            log.info("Converted {}.{} from ENUM to VARCHAR(20)", table, name);
        }
    }
}
