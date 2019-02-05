package ua.nure.latysh.quizzes.db.connector;

import org.apache.log4j.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DbConnector {

    private static final Logger logger = Logger.getLogger(DbConnector.class);
    private static DbConnector instance;
    private DataSource ds;

    private DbConnector() {
        try {
            Context initContext = new InitialContext();
            Context envContext = (Context) initContext.lookup("java:/comp/env");
            ds = (DataSource) envContext.lookup("jdbc/quizzes");
        } catch (NamingException ex) {
            logger.error(ex);
        }
    }

    public static synchronized DbConnector getInstance() {
        if (instance == null) {
            instance = new DbConnector();
        }
        return instance;
    }

    public Connection getConnection() {
        Connection con = null;
        try {
            con = ds.getConnection();
        } catch (SQLException ex) {
            logger.error(ex);
        }
        return con;
    }

    public void rollback(Connection con) {
        if (con != null) {
            try {
                con.rollback();
            } catch (SQLException ex) {
                logger.error(ex);
            }
        }
    }

    public void close(Connection con, Statement stmt, ResultSet rs) {
        close(rs);
        close(stmt);
        close(con);
    }

    private void close(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException ex) {
                logger.error(ex);
            }
        }
    }

    private void close(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException ex) {
                logger.error(ex);
            }
        }
    }

    private void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException ex) {
                logger.error(ex);
            }
        }
    }
}
