package ua.nure.latysh.quizzes.db.connector;

import ua.nure.latysh.quizzes.exceptions.RepositoryException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DbConnector {

    private static DbConnector instance;
    private final DataSource dataSource;

    private DbConnector() {
        this(lookupDataSource());
    }

    public DbConnector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static DataSource lookupDataSource() {
        try {
            Context initContext = new InitialContext();
            Context envContext = (Context) initContext.lookup("java:/comp/env");
            return (DataSource) envContext.lookup("jdbc/quizzes");
        } catch (NamingException ex) {
            throw new RepositoryException("Could not locate the quizzes data source", ex);
        }
    }

    public static synchronized DbConnector getInstance() {
        if (instance == null) {
            instance = new DbConnector();
        }
        return instance;
    }

    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException ex) {
            throw new RepositoryException("Could not open a database connection", ex);
        }
    }

    public void rollback(Connection con) {
        if (con != null) {
            try {
                con.rollback();
            } catch (SQLException ex) {
                throw new RepositoryException("Could not roll back the database transaction", ex);
            }
        }
    }
}
