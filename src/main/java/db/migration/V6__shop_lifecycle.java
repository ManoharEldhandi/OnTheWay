package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * V6 — Shop lifecycle and multi-shop ownership.
 *
 * <p>Adds the marketplace status columns and removes the one-shop-per-user uniqueness on
 * {@code merchants.user_id} so that a single owner can operate multiple shops. The uniqueness is
 * discovered from JDBC metadata (its backing constraint/index name is generated and differs
 * between H2 and MySQL) and dropped with the appropriate dialect syntax, so the same migration
 * runs cleanly on both the test/demo database (H2) and the production database (MySQL).
 */
public class V6__shop_lifecycle extends BaseJavaMigration {

    /** Runs outside a transaction so independent drop attempts cannot poison one another. */
    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE merchants ADD COLUMN status VARCHAR(20)");
            st.execute("ALTER TABLE merchants ADD COLUMN status_reason VARCHAR(500)");
            // Existing shops (e.g. seeded data) remain visible: treat them as already approved.
            st.execute("UPDATE merchants SET status = 'APPROVED' WHERE status IS NULL");
        }
        dropUserIdUniqueness(connection);
    }

    /**
     * Removes the uniqueness on {@code merchants.user_id}. On both H2 and MySQL the unique
     * constraint and the foreign key on {@code user_id} are served by the same (unique) index, so
     * simply dropping the unique constraint leaves a unique index behind. The portable sequence is:
     * drop the unique constraint, drop the foreign key, drop the now-orphaned unique index, then
     * recreate the foreign key (which builds a fresh, non-unique index).
     */
    private void dropUserIdUniqueness(Connection connection) {
        // 1. Drop the unique constraint (and, on MySQL, the unique index) by discovered name.
        for (String table : new String[]{"MERCHANTS", "merchants"}) {
            for (String name : uniqueConstraintNames(connection, table)) {
                tryExecute(connection, "ALTER TABLE merchants DROP CONSTRAINT " + name);
                tryExecute(connection, "ALTER TABLE merchants DROP INDEX " + name);
            }
        }

        // 2. Drop the foreign key on user_id (syntax differs by dialect).
        tryExecute(connection, "ALTER TABLE merchants DROP CONSTRAINT fk_merchants_user");   // H2
        tryExecute(connection, "ALTER TABLE merchants DROP FOREIGN KEY fk_merchants_user");  // MySQL

        // 3. Drop any remaining unique index on user_id now that nothing depends on it.
        for (String table : new String[]{"MERCHANTS", "merchants"}) {
            for (String name : uniqueIndexNamesOnUserId(connection, table)) {
                tryExecute(connection, "DROP INDEX " + name);                       // H2
                tryExecute(connection, "ALTER TABLE merchants DROP INDEX " + name); // MySQL
            }
        }

        // 4. Recreate the foreign key (creates a fresh, non-unique supporting index).
        tryExecute(connection, "ALTER TABLE merchants ADD CONSTRAINT fk_merchants_user "
                + "FOREIGN KEY (user_id) REFERENCES users (user_id)");
    }

    private Set<String> uniqueConstraintNames(Connection connection, String table) {
        Set<String> names = new LinkedHashSet<>();
        String sql = "SELECT constraint_name FROM information_schema.table_constraints "
                + "WHERE table_name = '" + table + "' AND constraint_type = 'UNIQUE'";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String n = rs.getString(1);
                if (n != null) {
                    names.add(n);
                }
            }
        } catch (Exception ignored) {
            // information_schema shape differs; the index-based path below still applies.
        }
        return names;
    }

    private Set<String> uniqueIndexNamesOnUserId(Connection connection, String table) {
        Set<String> names = new LinkedHashSet<>();
        try (ResultSet rs = connection.getMetaData()
                .getIndexInfo(connection.getCatalog(), null, table, true, false)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                String index = rs.getString("INDEX_NAME");
                if (index != null && column != null && column.equalsIgnoreCase("user_id")) {
                    names.add(index);
                }
            }
        } catch (Exception ignored) {
            // Metadata not available in this form; ignore.
        }
        return names;
    }

    private void tryExecute(Connection connection, String sql) {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        } catch (Exception ignored) {
            // The object may not exist under this name/dialect; that is acceptable.
        }
    }
}
