package ua.nure.latysh.quizzes.db.connector;

import org.junit.Test;
import org.mockito.MockedConstruction;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DbConnectorTest {

    @Test
    public void singletonLooksUpDataSourceAndReturnsConnection() throws Exception {
        resetSingleton();
        Context environment = mock(Context.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(environment.lookup("jdbc/quizzes")).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);

        try (MockedConstruction<InitialContext> ignored = mockConstruction(InitialContext.class,
                (initialContext, context) -> when(initialContext.lookup("java:/comp/env"))
                        .thenReturn(environment))) {
            DbConnector connector = DbConnector.getInstance();
            assertNotNull(connector);
            assertSame(connector, DbConnector.getInstance());
            assertSame(connection, connector.getConnection());
        }
    }

    @Test
    public void connectionRollbackAndCloseHandleSuccessNullsAndSqlErrors() throws Exception {
        DbConnector connector = DbConnector.getInstance();
        DataSource dataSource = mock(DataSource.class);
        setDataSource(connector, dataSource);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection"));
        assertNull(connector.getConnection());

        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        connector.rollback(null);
        connector.rollback(connection);
        verify(connection).rollback();
        connector.close(connection, statement, resultSet);
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();

        Connection brokenConnection = mock(Connection.class);
        Statement brokenStatement = mock(Statement.class);
        ResultSet brokenResultSet = mock(ResultSet.class);
        doThrow(new SQLException("rollback")).when(brokenConnection).rollback();
        doThrow(new SQLException("connection close")).when(brokenConnection).close();
        doThrow(new SQLException("statement close")).when(brokenStatement).close();
        doThrow(new SQLException("result set close")).when(brokenResultSet).close();
        connector.rollback(brokenConnection);
        connector.close(brokenConnection, brokenStatement, brokenResultSet);
        connector.close(null, null, null);
    }

    private static void resetSingleton() throws Exception {
        Field instance = DbConnector.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    private static void setDataSource(DbConnector connector, DataSource dataSource) throws Exception {
        Field field = DbConnector.class.getDeclaredField("ds");
        field.setAccessible(true);
        field.set(connector, dataSource);
    }
}
