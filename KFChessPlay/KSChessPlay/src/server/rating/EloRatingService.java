package server.rating;

/** Textbook Elo rating formula, K-factor 32 (standard for non-master play). */
public final class EloRatingService implements RatingService {
    private static final int K_FACTOR = 32;

    @Override
    public EloUpdate computeUpdate(int whiteElo, int blackElo, String winnerColor) {
        double expectedWhite = 1.0 / (1.0 + Math.pow(10, (blackElo - whiteElo) / 400.0));
        double expectedBlack = 1.0 - expectedWhite;

        double actualWhite = "WHITE".equalsIgnoreCase(winnerColor) ? 1.0
                : "BLACK".equalsIgnoreCase(winnerColor) ? 0.0 : 0.5;
        double actualBlack = 1.0 - actualWhite;

        int whiteDelta = (int) Math.round(K_FACTOR * (actualWhite - expectedWhite));
        int blackDelta = (int) Math.round(K_FACTOR * (actualBlack - expectedBlack));

        return new EloUpdate(whiteDelta, blackDelta, whiteElo + whiteDelta, blackElo + blackDelta);
    }
}
