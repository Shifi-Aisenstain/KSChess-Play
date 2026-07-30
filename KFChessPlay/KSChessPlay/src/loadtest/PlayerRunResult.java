package loadtest;

public final class PlayerRunResult {
    public final int playerIndex;
    public final boolean loginSucceeded;
    public final long loginLatencyMs;
    public final boolean matchFound;
    public final long matchmakingLatencyMs;
    public final boolean moveRoundTripCompleted;
    public final long moveRoundTripMs;
    public final boolean gameEndedCleanly;
    public final String failureReason;

    private PlayerRunResult(int playerIndex, boolean loginSucceeded, long loginLatencyMs, boolean matchFound,
                             long matchmakingLatencyMs, boolean moveRoundTripCompleted, long moveRoundTripMs,
                             boolean gameEndedCleanly, String failureReason) {
        this.playerIndex = playerIndex;
        this.loginSucceeded = loginSucceeded;
        this.loginLatencyMs = loginLatencyMs;
        this.matchFound = matchFound;
        this.matchmakingLatencyMs = matchmakingLatencyMs;
        this.moveRoundTripCompleted = moveRoundTripCompleted;
        this.moveRoundTripMs = moveRoundTripMs;
        this.gameEndedCleanly = gameEndedCleanly;
        this.failureReason = failureReason;
    }

    public static final class Builder {
        private final int playerIndex;
        private boolean loginSucceeded;
        private long loginLatencyMs;
        private boolean matchFound;
        private long matchmakingLatencyMs;
        private boolean moveRoundTripCompleted;
        private long moveRoundTripMs;
        private boolean gameEndedCleanly;
        private String failureReason;

        public Builder(int playerIndex) {
            this.playerIndex = playerIndex;
        }

        public Builder login(boolean succeeded, long latencyMs) {
            this.loginSucceeded = succeeded;
            this.loginLatencyMs = latencyMs;
            return this;
        }

        public Builder matchmaking(boolean found, long latencyMs) {
            this.matchFound = found;
            this.matchmakingLatencyMs = latencyMs;
            return this;
        }

        public Builder moveRoundTrip(boolean completed, long latencyMs) {
            this.moveRoundTripCompleted = completed;
            this.moveRoundTripMs = latencyMs;
            return this;
        }

        public Builder gameEndedCleanly(boolean value) {
            this.gameEndedCleanly = value;
            return this;
        }

        public Builder failureReason(String reason) {
            this.failureReason = reason;
            return this;
        }

        public PlayerRunResult build() {
            return new PlayerRunResult(playerIndex, loginSucceeded, loginLatencyMs, matchFound, matchmakingLatencyMs,
                    moveRoundTripCompleted, moveRoundTripMs, gameEndedCleanly, failureReason);
        }
    }
}
