package server.rating;

public interface RatingService {
    /** Result of a finished game, used to compute both players' new ratings. */
    final class EloUpdate {
        public final int whiteDelta;
        public final int blackDelta;
        public final int whiteNewElo;
        public final int blackNewElo;

        public EloUpdate(int whiteDelta, int blackDelta, int whiteNewElo, int blackNewElo) {
            this.whiteDelta = whiteDelta;
            this.blackDelta = blackDelta;
            this.whiteNewElo = whiteNewElo;
            this.blackNewElo = blackNewElo;
        }
    }

    /** @param winnerColor "WHITE", "BLACK", or null for a draw. */
    EloUpdate computeUpdate(int whiteElo, int blackElo, String winnerColor);
}
