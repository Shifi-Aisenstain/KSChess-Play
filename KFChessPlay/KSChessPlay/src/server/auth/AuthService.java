package server.auth;

public interface AuthService {
    Result login(String username, String password);

    final class Result {
        public final boolean success;
        public final User user;
        public final String errorMessage;

        private Result(boolean success, User user, String errorMessage) {
            this.success = success;
            this.user = user;
            this.errorMessage = errorMessage;
        }

        public static Result ok(User user) { return new Result(true, user, null); }
        public static Result fail(String reason) { return new Result(false, null, reason); }
    }
}
