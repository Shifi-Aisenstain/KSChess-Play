package server.matchmaking;

import java.util.List;

public interface WaitingPlayersQueue {

    void addIfAbsent(WaitingPlayer player);

    boolean remove(long userId);

    List<WaitingPlayer> snapshot();
}
