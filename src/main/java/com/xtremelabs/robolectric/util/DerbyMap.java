package com.xtremelabs.robolectric.util;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DerbyMap implements DatabaseConfig.DatabaseMap {
    @Override
    public String getDriverClassName() {
        return "org.apache.derby.jdbc.EmbeddedDriver";
    }

    @Override
    public String getConnectionString() {
        return "jdbc:derby:memory:roboDB;create=true";
    }

    @Override
    public String getScrubSQL(String sql) throws SQLException {
        String scrubbedSQL = sql.replaceAll("(?i:autoincrement)", "GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)");
        // Map 'text' (sqlite) to 'varchar' (derby).
        scrubbedSQL = scrubbedSQL.replaceAll("(?i:text)", "VARCHAR(256)");
        scrubbedSQL = scrubbedSQL.replaceAll("(?i:_ID)", "ID");
        scrubbedSQL = scrubbedSQL.replaceAll("(?i:\\);)", ")");

        return scrubbedSQL;
    }

    @Override
    public String getSelectLastInsertIdentity() {
        return "IDENTITY_VAL_LOCAL()";
    }

    @Override
    public int getResultSetType() {
        return ResultSet.TYPE_SCROLL_INSENSITIVE;
    }
}
