package com.marketbridge.config;

import com.marketbridge.model.User;
import com.marketbridge.repository.UserRepository;
import com.marketbridge.util.PasswordUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

// Runs once, automatically, every time the app starts.
// It makes sure there is always one Admin account so someone can log in
// and approve sellers, even on a fresh database.
@Configuration
public class DataInitializer {

    // ---- Fixes leftover-schema problems on databases created by older
    // versions of this project ----
    //
    // spring.jpa.hibernate.ddl-auto=update only ever ADDS columns/tables that
    // match the current entity classes - it never renames, drops, or widens
    // a column that already exists. Two known cases of that biting us:
    //
    // 1) The "messages" table had a NOT NULL "body" column early on; it got
    //    renamed to "content" in Message.java, but any database that was
    //    already running keeps the old "body" column, still NOT NULL with no
    //    default - so every insert into "messages" then fails with:
    //    "Field 'body' doesn't have a default value".
    //
    // 2) Review.java's "comment" field was missing @Column(length = 1000),
    //    so on any database created before that annotation was added,
    //    Hibernate made the column the default VARCHAR(255) - a review
    //    comment longer than 255 characters then fails with "Data too long
    //    for column 'comment'".
    //
    // This runner detects both leftovers (safe/idempotent - does nothing on
    // a fresh database, where columns are already correct) and fixes them
    // automatically, so nobody has to run a manual SQL fix by hand.
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CommandLineRunner fixLegacySchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            try (var conn = jdbcTemplate.getDataSource().getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();

                try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, "messages", "body")) {
                    if (rs.next()) {
                        jdbcTemplate.execute("ALTER TABLE messages DROP COLUMN body");
                        System.out.println("[Tuki-Taki] Removed leftover 'body' column from messages table (legacy schema fix).");
                    }
                }

                try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, "reviews", "comment")) {
                    if (rs.next() && rs.getInt("COLUMN_SIZE") < 1000) {
                        jdbcTemplate.execute("ALTER TABLE reviews MODIFY COLUMN comment VARCHAR(1000)");
                        System.out.println("[Tuki-Taki] Widened reviews.comment to VARCHAR(1000) (legacy schema fix).");
                    }
                }
            } catch (Exception e) {
                // Never block app startup over this - worst case, the old
                // error can still show up and needs a manual look.
                System.err.println("[Tuki-Taki] Legacy schema check failed: " + e.getMessage());
            }
        };
    }

    @Bean
    public CommandLineRunner createDefaultAdmin(UserRepository userRepository) {
        return args -> {
            boolean adminExists = userRepository.findByEmail("admin@marketbridge.com").isPresent();
            if (!adminExists) {
                User admin = new User();
                admin.setName("Admin");
                admin.setEmail("admin@marketbridge.com");
                admin.setPassword(PasswordUtil.hash("admin123"));
                admin.setRole("ADMIN");
                admin.setStatus("APPROVED");
                userRepository.save(admin);
                System.out.println("Default admin created -> email: admin@marketbridge.com | password: admin123");
            }
        };
    }
}
