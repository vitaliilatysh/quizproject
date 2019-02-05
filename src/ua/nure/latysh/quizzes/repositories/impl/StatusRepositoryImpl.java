package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Status;
import ua.nure.latysh.quizzes.repositories.StatusRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StatusRepositoryImpl implements StatusRepository {

    private final static Logger logger = Logger.getLogger(StatusRepositoryImpl.class);
    private DbConnector dbConnector = DbConnector.getInstance();

    @Override
    public Status findById(int statusId) {
        Status status = new Status();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from statuses where id=?");
            statement.setInt(1, statusId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                status = extractStatus(resultSet);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return status;
    }


    @Override
    public void delete(Status answer) {
//        if (findById(subject.getId()) != null) {
//            try (Connection connection = dbConnector.getConnection()) {
//                PreparedStatement statement = connection.prepareStatement("DELETE FROM subjects WHERE id=?");
//                statement.setInt(1, subject.getId());
//                statement.executeUpdate();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }
//        }
    }

    @Override
    public boolean save(Status status) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO statuses (id, status) VALUES (?, ?)");
            statement.setInt(1, status.getId());
            statement.setString(2, status.getStatus());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(Status answer) {
//        try (Connection connection = dbConnector.getConnection()) {
//            PreparedStatement statement = connection.prepareStatement("UPDATE subjects SET name=? WHERE id=?");
//            statement.setString(1, subject.getName());
//            statement.setInt(2, subject.getId());
//            statement.executeUpdate();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
    }


    @Override
    public List<Status> findAll() {
        List<Status> statuses = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            stmt = con.createStatement();
            rs = stmt.executeQuery("SELECT * from statuses");
            while (rs.next()) {
                statuses.add(extractStatus(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return statuses;
    }

    private Status extractStatus(ResultSet rs)
            throws SQLException {
        Status status = new Status();
        status.setId(rs.getInt("id"));
        status.setStatus(rs.getString("name"));
        return status;
    }

}
