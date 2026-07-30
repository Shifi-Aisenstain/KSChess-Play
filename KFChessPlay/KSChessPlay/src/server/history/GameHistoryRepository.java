package server.history;

import java.util.List;

public interface GameHistoryRepository {
    void saveGame(String roomId, long whiteUserId, long blackUserId, String whiteUsername, String blackUsername,
                  String winnerColor, String reason, int whiteEloDelta, int blackEloDelta, long endedAt);

    void saveMove(String roomId, String notation, long playedAt);

    /** Every game {@code userId} played as either color, most recently ended first - backs the
     *  {@code GET /history?userId=} REST endpoint (Server_Design.md's "REST/HTTP (login, rooms,
     *  history) -> API Gateway" row). */
    List<GameSummary> findGamesByUserId(long userId);

    /** Every move played in {@code roomId}, in the order they happened - backs
     *  {@code GET /history/moves?roomId=}. */
    List<MoveEntry> findMovesByRoomId(String roomId);
}
