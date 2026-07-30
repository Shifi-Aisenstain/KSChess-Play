package server.history;

public final class MoveEntry {
    public final String notation;
    public final long playedAt;

    public MoveEntry(String notation, long playedAt) {
        this.notation = notation;
        this.playedAt = playedAt;
    }
}
