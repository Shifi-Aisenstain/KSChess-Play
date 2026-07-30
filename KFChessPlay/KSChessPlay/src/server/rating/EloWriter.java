package server.rating;

public interface EloWriter {
    void updateElo(long userId, int newElo);
}
