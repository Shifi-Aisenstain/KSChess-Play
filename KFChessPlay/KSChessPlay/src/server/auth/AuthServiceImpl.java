package server.auth;

/**
 * Spec calls this "login with username + password (save at SQLite db on
 * server side)" with no separate registration step mentioned, so the
 * natural read is auto-register-on-first-login: an unknown username creates
 * an account (starting ELO 1200 per spec), a known username must match its
 * stored password.
 */
public final class AuthServiceImpl implements AuthService {
    private static final int STARTING_ELO = 1200;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AuthServiceImpl(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public Result login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return Result.fail("Username and password are required.");
        }

        var existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            UserRepository.StoredUser stored = existing.get();
            if (!passwordHasher.matches(password, stored.salt, stored.passwordHash)) {
                return Result.fail("Incorrect password.");
            }
            return Result.ok(new User(stored.id, stored.username, stored.elo));
        }

        String salt = passwordHasher.generateSalt();
        String hash = passwordHasher.hash(password, salt);
        UserRepository.StoredUser created = userRepository.create(username, hash, salt, STARTING_ELO);
        return Result.ok(new User(created.id, created.username, created.elo));
    }
}
