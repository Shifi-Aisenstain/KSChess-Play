package shared.bus;

public final class RoomBusSubjects {

    public static final String ROOM_ALLOCATED = "room.allocated";

    public static final String ROOM_CREATE = "room.create";

    public static final String ROOM_JOIN = "room.join";

    public static final String ROOM_CANCEL = "room.cancel";

    public static final String MATCHMAKING_REQUESTS = "matchmaking.requests";

    public static final String MATCHMAKING_CANCEL = "matchmaking.cancel";

    public static final String MATCHMAKING_RESULTS = "matchmaking.results";

    public static final String ACTIVE_ROOM_LOOKUP = "user.active-room";

    public static final String ELO_UPDATES = "persistence.elo-updates";

    public static final String GAME_HISTORY = "persistence.game-history";

    public static final String MOVE_HISTORY = "persistence.move-history";

    private RoomBusSubjects() { }

    public static String commands(String roomId) {
        return "room." + roomId + ".commands";
    }

    public static String updates(String roomId) {
        return "room." + roomId + ".updates";
    }
}
