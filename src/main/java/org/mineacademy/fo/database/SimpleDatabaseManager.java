package org.mineacademy.fo.database;

import lombok.*;
import org.mineacademy.fo.*;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.collection.StrictMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.model.ConfigSerializable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a simple MySQL database
 * <p>
 * Before running queries make sure to call connect() methods.
 * <p>
 * You can also override onConnected() to run your code after the
 * connection has been established.
 * <p>
 * To use this class you must know the MySQL command syntax!
 *
 * @author kangarko
 * @author Rubix327
 * @since 6.2.5.6
 */
@SuppressWarnings({"unused", "SameParameterValue"})
public class SimpleDatabaseManager {

    @Getter
    private SimpleDatabaseConnector connector;
    private Connection connection;

    /**
     * Map of variables you can use with the {} syntax in SQL
     */
    private final StrictMap<String, String> sqlVariables = new StrictMap<>();

    void setConnector(SimpleDatabaseConnector connector){
        this.connector = connector;
        this.connection = connector.getConnection();
    }

    protected void onConnected(){};

    private Connection getConnection(){
        return connector.getConnection();
    }

    private String getUrl(){
        return connector.getUrl();
    }

    // --------------------------------------------------------------------
    // Querying
    // --------------------------------------------------------------------

    /**
     * Creates a database table, to be used in onConnected
     */
    protected final void createTable(TableCreator creator) {
        StringBuilder columns = new StringBuilder();

        for (final TableRow column : creator.getColumns()) {
            columns.append((columns.length() == 0) ? "" : ", ").append("`").append(column.getName()).append("` ").append(column.getDataType());

            if (column.getAutoIncrement() != null && column.getAutoIncrement())
                columns.append(" NOT NULL AUTO_INCREMENT");

            else if (column.getNotNull() != null && column.getNotNull())
                columns.append(" NOT NULL");

            if (column.getDefaultValue() != null)
                columns.append(" DEFAULT ").append(column.getDefaultValue());
        }

        if (creator.getPrimaryColumn() != null)
            columns.append(", PRIMARY KEY (`").append(creator.getPrimaryColumn()).append("`)");

        try {
            final boolean isSQLite = getUrl() != null && getUrl().startsWith("jdbc:sqlite");

            this.update("CREATE TABLE IF NOT EXISTS `" + creator.getName() + "` (" + columns + ")" + (isSQLite ? "" : " DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci") + ";");

        } catch (final Throwable t) {
            if (t.toString().contains("Unknown collation")) {
                Common.log("You need to update your database driver to support utf8mb4_unicode_520_ci collation. We switched to support unicode using 4 bits length because the previous system only supported 3 bits.");
                Common.log("Some characters such as smiley or Chinese are stored in 4 bits so they would crash the 3-bit database leading to more problems. Most hosting providers have now widely adopted the utf8mb4_unicode_520_ci encoding you seem lacking. Disable database connection or update your driver to fix this.");
            }

            else
                throw t;
        }
    }

    /**
     * Insert the given serializable object as its column-value pairs into the given table
     */
    protected final <T extends ConfigSerializable> void insert(String table, @NonNull T serializableObject) {
        this.insert(table, serializableObject.serialize());
    }

    /**
     * Insert the given column-values pairs into the given table
     */
    protected final void insert(String table, @NonNull SerializedMap columnsAndValues) {
        final String columns = Common.join(columnsAndValues.keySet());
        final String values = Common.join(columnsAndValues.values(), ", ", value -> value == null || value.equals("NULL") ? "NULL" : "'" + value + "'");
        final String duplicateUpdate = Common.join(columnsAndValues.entrySet(), ", ", entry -> entry.getKey() + "=VALUES(" + entry.getKey() + ")");

        this.update("INSERT INTO " + this.replaceVariables(table) + " (" + columns + ") VALUES (" + values + ") ON DUPLICATE KEY UPDATE " + duplicateUpdate + ";");
    }

    /**
     * Insert the batch map into the database
     */
    protected final void insertBatch(String table, @NonNull List<SerializedMap> maps) {
        final List<String> sqls = new ArrayList<>();

        for (final SerializedMap map : maps) {
            final String columns = Common.join(map.keySet());
            final String values = Common.join(map.values(), ", ", this::parseValue);
            final String duplicateUpdate = Common.join(map.entrySet(), ", ", entry -> entry.getKey() + "=VALUES(" + entry.getKey() + ")");

            sqls.add("INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ") ON DUPLICATE KEY UPDATE " + duplicateUpdate + ";");
        }

        this.batchUpdate(sqls);
    }

    /*
     * A helper method to insert compatible value to db
     */
    private String parseValue(Object value) {
        return value == null || value.equals("NULL") ? "NULL" : "'" + SerializeUtil.serialize(SerializeUtil.Mode.YAML, value).toString() + "'";
    }

    /**
     * Attempts to execute a new update query
     * <p>
     * Make sure you called connect() first otherwise an error will be thrown
     */
    protected final void update(String sql) {
        if (!this.connector.isConnecting()){
            Valid.checkAsync("Updating database must be done async! Call: " + sql);
        }

        this.connector.checkEstablished();

        if (!this.connector.isConnected()){
            this.connector.connectUsingLastCredentials();
        }

        sql = this.replaceVariables(sql);
        Debugger.debug("mysql", "Updating database with: " + sql);

        try (Statement statement = this.connection.createStatement()) {
            statement.executeUpdate(sql);

        } catch (final SQLException e) {
            this.handleError(e, "Error on updating database with: " + sql);
        }
    }

    /**
     * Lists all rows in the given table with the "*" parameter, listing all rows
     */
    protected final void selectAll(String table, ResultReader consumer) {
        this.select(table, "*", consumer);
    }

    /**
     * Lists all rows in the given table with the given parameter.
     * Do not forget to close the connection when done in your consumer.
     */
    protected final void select(String table, String param, ResultReader consumer) {
        if (!this.connector.isLoaded()){
            return;
        }

        try (ResultSet resultSet = this.query("SELECT " + param + " FROM " + table)) {
            while (resultSet.next()){
                try {
                    consumer.accept(resultSet);

                } catch (final Throwable t) {
                    Common.log("Error reading a row from table " + table + " with param '" + param + "', aborting...");

                    t.printStackTrace();
                    break;
                }
            }

        } catch (final Throwable t) {
            Common.error(t, "Error selecting rows from table " + table + " with param '" + param + "'");
        }
    }

    /**
     * Get the amount of rows from the given table per the key-value conditions.
     * <br><br>
     * Example conditions: count("MyTable", "Player", "kangarko", "Status", "PENDING")
     * This example will return all rows where column Player is equal to kangarko and Status column equals PENDING.
     */
    protected final int count(String table, Object... array) {
        return this.count(table, SerializedMap.ofArray(array));
    }

    /**
     * Get the amount of rows from the given table per the conditions,
     * <br><br>
     * Example conditions: SerializedMap.ofArray("Player", "kangarko", "Status", "PENDING")
     * This example will return all rows where column Player is equal to kangarko and Status column equals PENDING.
     */
    protected final int count(String table, SerializedMap conditions) {

        // Convert conditions into SQL syntax
        final Set<String> conditionsList = Common.convertSet(conditions.entrySet(), entry -> entry.getKey() + " = '" + SerializeUtil.serialize(SerializeUtil.Mode.YAML, entry.getValue()) + "'");

        // Run the query
        final String sql = "SELECT * FROM " + table + (conditionsList.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditionsList)) + ";";

        try (ResultSet resultSet = this.query(sql)) {
            int count = 0;

            while (resultSet.next()){
                count++;
            }

            return count;
        } catch (final SQLException ex) {
            Common.throwError(ex,
                    "Unable to count rows!",
                    "Table: " + this.replaceVariables(table),
                    "Conditions: " + conditions,
                    "Query: " + sql);
        }

        return 0;
    }

    /**
     * Attempts to execute a new query
     * <p>
     * Make sure you called connect() first otherwise an error will be thrown
     */
    protected final ResultSet query(String sql) {
        Valid.checkAsync("Sending database query must be called async, command: " + sql);

        this.connector.checkEstablished();

        if (!this.connector.isConnected()){
            this.connector.connectUsingLastCredentials();
        }

        sql = this.replaceVariables(sql);

        Debugger.debug("mysql", "Querying database with: " + sql);

        try {
            final Statement statement = this.connection.createStatement();
            final ResultSet resultSet = statement.executeQuery(sql);

            return resultSet;

        } catch (final SQLException ex) {
            if (ex instanceof SQLSyntaxErrorException && ex.getMessage().startsWith("Table") && ex.getMessage().endsWith("doesn't exist"))
                return new DummyResultSet();

            this.handleError(ex, "Error on querying database with: " + sql);
        }

        return null;
    }

    /**
     * Executes a massive batch update
     *
     * @param sqls
     */
    protected final void batchUpdate(@NonNull List<String> sqls) {
        if (sqls.isEmpty())
            return;

        this.connector.checkEstablished();

        if (!this.connector.isConnected()){
            this.connector.connectUsingLastCredentials();
        }

        try (Statement batchStatement = this.getConnection().createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
            final int processedCount = sqls.size();

            for (final String sql : sqls)
                batchStatement.addBatch(this.replaceVariables(sql));

            if (processedCount > 10_000)
                Common.log("Updating your database (" + processedCount + " entries)... PLEASE BE PATIENT THIS WILL TAKE "
                        + (processedCount > 50_000 ? "10-20 MINUTES" : "5-10 MINUTES") + " - If server will print a crash report, ignore it, update will proceed.");

            // Prevent automatically sending db instructions
            this.getConnection().setAutoCommit(false);

            try {
                // Execute
                batchStatement.executeBatch();

                // This will block the thread
                this.getConnection().commit();

            } catch (final Throwable t) {
                // Cancel the task but handle the error upstream
                throw t;
            }

        } catch (final Throwable t) {
            final List<String> errorLog = new ArrayList<>();

            errorLog.add(Common.consoleLine());
            errorLog.add(" [" + TimeUtil.getFormattedDateShort() + "] Failed to save batch sql, please contact the plugin author with this file content: " + t);
            errorLog.add(Common.consoleLine());

            for (final String statement : sqls)
                errorLog.add(this.replaceVariables(statement));

            FileUtil.write("sql-error.log", sqls);

            t.printStackTrace();

        } finally {
            try {
                this.connection.setAutoCommit(true);

            } catch (final SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Attempts to return a prepared statement
     * <p>
     * Make sure you called connect() first otherwise an error will be thrown
     */
    protected final java.sql.PreparedStatement prepareStatement(String sql) throws SQLException {
        this.connector.checkEstablished();

        if (!this.connector.isConnected()){
            this.connector.connectUsingLastCredentials();
        }

        sql = this.replaceVariables(sql);

        Debugger.debug("mysql", "Preparing statement: " + sql);
        return this.connection.prepareStatement(sql);
    }

    /**
     * Attempts to return a prepared statement
     * <p>
     * Make sure you called connect() first otherwise an error will be thrown
     */
    protected final java.sql.PreparedStatement prepareStatement(String sql, int type, int concurrency) throws SQLException {
        this.connector.checkEstablished();

        if (!this.connector.isConnected()){
            this.connector.connectUsingLastCredentials();
        }

        sql = this.replaceVariables(sql);

        Debugger.debug("mysql", "Preparing statement: " + sql);
        return this.connection.prepareStatement(sql, type, concurrency);
    }

    // --------------------------------------------------------------------
    // Variables
    // --------------------------------------------------------------------

    /**
     * Check if the developer called {@link #addVariable(String, String)} early enough
     * to be registered
     *
     * @param key the key of the variable
     * @return true if called
     */
    final boolean hasVariable(String key) {
        return this.sqlVariables.containsKey(key);
    }

    /**
     * Adds a new variable you can then use in your queries.
     * The variable name will be added {} brackets automatically.
     *
     * @param name the name of the variable
     * @param value the value
     */
    protected final void addVariable(final String name, final String value) {
        this.sqlVariables.put(name, value);
    }

    /**
     * Replace the {@link #sqlVariables} in the sql query
     *
     * @param sql the query
     * @return the variables-replaced query
     */
    protected final String replaceVariables(String sql) {

        for (final Map.Entry<String, String> entry : this.sqlVariables.entrySet()){
            sql = sql.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return sql;
    }

    // --------------------------------------------------------------------
    // Other
    // --------------------------------------------------------------------

    /**
     * Check if there's a collation-related error and prints warning message for the user to
     * update his database.
     */
    private void handleError(Throwable t, String fallbackMessage) {
        if (t.toString().contains("Unknown collation")) {
            Common.log("You need to update your database provider driver. We switched to support unicode using 4 bits length because the previous system only supported 3 bits.");
            Common.log("Some characters such as smiley or Chinese are stored in 4 bits so they would crash the 3-bit database leading to more problems. Most hosting providers have now widely adopted the utf8mb4_unicode_520_ci encoding you seem lacking. Disable database connection or update your driver to fix this.");
        }

        else if (t.toString().contains("Incorrect string value")) {
            Common.log("Attempted to save unicode letters (e.g. coors) to your database with invalid encoding, see https://stackoverflow.com/a/10959780 and adjust it. MariaDB may cause issues, use MySQL 8.0 for best results.");

            t.printStackTrace();

        } else {
            Common.throwError(t, fallbackMessage);
        }
    }

    // --------------------------------------------------------------------
    // Classes
    // --------------------------------------------------------------------

    /**
     * Helps to create new database tables preventing SQL syntax errors
     */
    @Getter
    @RequiredArgsConstructor
    protected final static class TableCreator {

        /**
         * The table name
         */
        private final String name;

        /**
         * The table columns
         */
        private final List<TableRow> columns = new ArrayList<>();

        /**
         * The primary column
         */
        private String primaryColumn;

        /**
         * Add a new column of the given name and data type
         */
        public TableCreator add(String name, String dataType) {
            this.columns.add(TableRow.builder().name(name).dataType(dataType).build());

            return this;
        }

        /**
         * Add a new column of the given name and data type that is "NOT NULL"
         */
        public TableCreator addNotNull(String name, String dataType) {
            this.columns.add(TableRow.builder().name(name).dataType(dataType).notNull(true).build());

            return this;
        }

        /**
         * Add a new column of the given name and data type that is "NOT NULL AUTO_INCREMENT"
         */
        public TableCreator addAutoIncrement(String name, String dataType) {
            this.columns.add(TableRow.builder().name(name).dataType(dataType).autoIncrement(true).build());

            return this;
        }

        /**
         * Add a new column of the given name and data type that has a default value
         */
        public TableCreator addDefault(String name, String dataType, String def) {
            this.columns.add(TableRow.builder().name(name).dataType(dataType).defaultValue(def).build());

            return this;
        }

        /**
         * Marks which column is the primary key
         */
        public TableCreator setPrimaryColumn(String primaryColumn) {
            this.primaryColumn = primaryColumn;

            return this;
        }

        /**
         * Create a new table
         */
        public static TableCreator of(String name) {
            return new TableCreator(name);
        }
    }

    /*
     * Internal helper to create table rows
     */
    @Data
    @Builder
    private final static class TableRow {

        /**
         * The table row name
         */
        private final String name;

        /**
         * The data type
         */
        private final String dataType;

        /**
         * Is this row NOT NULL?
         */
        private final Boolean notNull;

        /**
         * Does this row have a default value?
         */
        private final String defaultValue;

        /**
         * Is this row NOT NULL AUTO_INCREMENT?
         */
        private final Boolean autoIncrement;
    }

    /**
     * A helper class to read results set. (We cannot use a simple Consumer since it does not
     * catch exceptions automatically.)
     */
    protected interface ResultReader {

        /**
         * Reads and process the given results set, we handle exceptions for you
         *
         * @param set
         * @throws SQLException
         */
        void accept(ResultSet set) throws SQLException;
    }
}
