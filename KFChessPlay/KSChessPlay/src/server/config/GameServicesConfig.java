package server.config;

import server.auth.AuthService;
import server.auth.AuthServiceImpl;
import server.auth.PasswordHasher;
import server.auth.UserRepository;
import server.matchmaking.EloRangeMatchCriteria;
import server.matchmaking.MatchmakingService;
import server.matchmaking.MatchmakingServiceImpl;
import server.rating.EloRatingService;
import server.rating.RatingService;
import server.rooms.RoomManager;
import shared.bus.MessageBus;
import shared.eventbus.EventBus;

/**
 * Wires the gameplay-facing services (auth, rating, rooms, matchmaking) and the event-bus
 * subscribers that depend on them. Kept separate from {@code ServerController} for the same reason
 * as {@link PersistenceConfig}: the composition root should read as "assemble these parts", not
 * contain the wiring knowledge for every subsystem inline.
 *
 * <p>Per PLAN_split_6_services.md §3.1, {@code server.game.GameEventBroadcaster} is deliberately no
 * longer wired here: the real game engine (and the domain events it publishes - GameStartedEvent,
 * GameEndedEvent, MoveExecutedEvent, PieceJumpedEvent) now runs inside {@code server.shard.GameServerShardMain},
 * a separate process, so those events never reach *this* {@code eventBus} anymore - a
 * {@code GameEventBroadcaster} subscribed here would simply never fire. The equivalent spectator
 * {@code GAME_EVENT} push now happens Shard-side, via {@code server.shard.ShardGameEventRelay}
 * (same mapping logic, publishing over the bus instead of straight to a {@code Room}).
 * {@code GameEventBroadcaster} itself is left in place/tested as pure logic, just not part of the
 * monolith's live composition root anymore.
 */
public final class GameServicesConfig {
    private static final int ELO_MATCH_RANGE = 100;

    private GameServicesConfig() { }

    /** Bundle of the services {@code ServerController} needs handles to after wiring is done. */
    public record GameServices(AuthService authService, RoomManager roomManager, MatchmakingService matchmakingService) { }

    public static GameServices build(EventBus eventBus, UserRepository userRepository, MessageBus bus) {
        AuthService authService = new AuthServiceImpl(userRepository, new PasswordHasher());

        RatingService ratingService = new EloRatingService();
        RoomManager roomManager = new RoomManager(eventBus, ratingService, userRepository, bus);
        MatchmakingService matchmakingService = new MatchmakingServiceImpl(
                new EloRangeMatchCriteria(ELO_MATCH_RANGE), roomManager);

        return new GameServices(authService, roomManager, matchmakingService);
    }
}
