package server.persistence;

import server.history.GameHistoryRepository;
import server.history.GameSummary;
import server.history.MoveEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class PostgresGameHistoryRepository implements GameHistoryRepository {
    private final PostgresConnectionProvider connectionProvider;

    public PostgresGameHistoryRepository(PostgresConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public void saveGame(String roomId, long whiteUserId, long blackUserId, String whiteUsername, String blackUsername,
                          String winnerColor, String reason, int whiteEloDelta, int blackEloDelta, long endedAt) {
        String sql = "INSERT INTO games (room_id, white_user_id, black_user_id, white_username, black_username, "
                + "winner_color, reason, white_elo_delta, black_elo_delta, ended_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roomId);
            statement.setLong(2, whiteUserId);
            statement.setLong(3, blackUserId);
            statement.setString(4, whiteUsername);
            statement.setString(5, blackUsername);
            statement.setString(6, winnerColor);
            statement.setString(7, reason);
            statement.setInt(8, whiteEloDelta);
            statement.setInt(9, blackEloDelta);
            statement.setLong(10, endedAt);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("saveGame failed", e);
        }
    }

    @Override
    public void saveMove(String roomId, String notation, long playedAt) {
        String sql = "INSERT INTO moves (room_id, notation, played_at) VALUES (?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roomId);
            statement.setString(2, notation);
            statement.setLong(3, playedAt);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("saveMove failed", e);
        }
    }

    @Override
    public List<GameSummary> findGamesByUserId(long userId) {
        String sql = "SELECT room_id, white_username, black_username, winner_color, reason, "
                + "white_elo_delta, black_elo_delta, ended_at FROM games "
                + "WHERE white_user_id = ? OR black_user_id = ? ORDER BY ended_at DESC";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, userId);
            try (ResultSet rs = statement.executeQuery()) {
                List<GameSummary> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new GameSummary(
                            rs.getString("room_id"), rs.getString("white_username"), rs.getString("black_username"),
                            rs.getString("winner_color"), rs.getString("reason"),
                            rs.getInt("white_elo_delta"), rs.getInt("black_elo_delta"), rs.getLong("ended_at")));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findGamesByUserId failed", e);
        }
    }

    @Override
    public List<MoveEntry> findMovesByRoomId(String roomId) {
        String sql = "SELECT notation, played_at FROM moves WHERE room_id = ? ORDER BY id ASC";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roomId);
            try (ResultSet rs = statement.executeQuery()) {
                List<MoveEntry> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new MoveEntry(rs.getString("notation"), rs.getLong("played_at")));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findMovesByRoomId failed", e);
        }
    }
}
