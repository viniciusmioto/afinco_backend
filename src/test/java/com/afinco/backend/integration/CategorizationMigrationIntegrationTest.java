package com.afinco.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

class CategorizationMigrationIntegrationTest {

    @TempDir
    Path databaseDirectory;

    @Test
    void upgradesV1CategoriesWithoutBreakingExistingTransactions() {
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.enforceForeignKeys(true);
        SQLiteDataSource dataSource = new SQLiteDataSource(sqliteConfig);
        dataSource.setUrl("jdbc:sqlite:" + databaseDirectory.resolve("category-upgrade.db"));

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO accounts (bank_name, account_number_last4, currency)
                VALUES ('TD Bank', '1234', 'CAD')
                """);
        jdbc.update("""
                INSERT INTO transactions (
                    account_id, category_id, date, amount, type, description,
                    hash_signature, status
                )
                SELECT 1, id, '2026-09-11', 18.50, 'DEBIT', 'Transit pass',
                       ?, 'CONFIRMED'
                FROM categories
                WHERE name = 'Transportation'
                """, "a".repeat(64));

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Map<String, Object> migrated = jdbc.queryForMap("""
                SELECT categories.name, categories.expense_type, transactions.amount
                FROM transactions
                JOIN categories ON categories.id = transactions.category_id
                """);

        assertThat(migrated.get("name")).isEqualTo("Transport");
        assertThat(migrated.get("expense_type")).isEqualTo("FIXED");
        assertThat(migrated.get("amount").toString()).isEqualTo("18.5");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE name IN ('Education', 'Income')",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE statement_id IS NULL", Integer.class))
                .as("pre-statement transactions survive V4 without a statement")
                .isEqualTo(1);
    }

    @Test
    void renamesPhoneInternetInPlaceSoItsTransactionsFollow() {
        SQLiteDataSource dataSource = dataSource("phone-rename.db");
        migrate(dataSource, "4");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM categories WHERE name = 'Phone / Internet'", Long.class);
        jdbc.update("""
                INSERT INTO accounts (bank_name, account_number_last4, currency)
                VALUES ('TD Bank', '1234', 'CAD')
                """);
        jdbc.update("""
                INSERT INTO transactions (
                    account_id, category_id, date, amount, type, description,
                    hash_signature, status
                )
                VALUES (1, ?, '2026-07-02', 40.24, 'CREDIT', 'VESTA *CHATR', ?, 'CONFIRMED')
                """, categoryId, "b".repeat(64));

        migrate(dataSource, null);

        assertThat(jdbc.queryForObject("""
                SELECT categories.name
                FROM transactions
                JOIN categories ON categories.id = transactions.category_id
                """, String.class)).isEqualTo("Phone & Internet");
        assertThat(jdbc.queryForObject(
                "SELECT id FROM categories WHERE name = 'Phone & Internet'", Long.class)).isEqualTo(categoryId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE name = 'Phone / Internet'", Integer.class)).isZero();
    }

    private SQLiteDataSource dataSource(String fileName) {
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.enforceForeignKeys(true);
        SQLiteDataSource dataSource = new SQLiteDataSource(sqliteConfig);
        dataSource.setUrl("jdbc:sqlite:" + databaseDirectory.resolve(fileName));
        return dataSource;
    }

    /** Migrates up to {@code targetVersion}, or to the latest migration when it is null. */
    private void migrate(SQLiteDataSource dataSource, String targetVersion) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration");
        if (targetVersion != null) {
            configuration.target(MigrationVersion.fromVersion(targetVersion));
        }
        configuration.load().migrate();
    }
}
