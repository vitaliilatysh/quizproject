package ua.nure.latysh.quizzes.db.connector;

import org.junit.Test;
import org.mockito.MockedConstruction;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
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
    public void lookupConnectionAndRollbackFailuresAreExplicit() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection"));
        DbConnector connector = new DbConnector(dataSource);
        assertThrows(RepositoryException.class, connector::getConnection);

        Connection connection = mock(Connection.class);
        connector.rollback(null);
        connector.rollback(connection);
        verify(connection).rollback();
        doThrow(new SQLException("rollback")).when(connection).rollback();
        assertThrows(RepositoryException.class, () -> connector.rollback(connection));

        resetSingleton();
        try (MockedConstruction<InitialContext> ignored = mockConstruction(InitialContext.class,
                (initialContext, context) -> when(initialContext.lookup("java:/comp/env"))
                        .thenThrow(new NamingException("missing")))) {
            assertThrows(RepositoryException.class, DbConnector::getInstance);
        }
    }

    private static void resetSingleton() throws Exception {
        Field instance = DbConnector.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }
}
