package shared.protocol.payload;

import lombok.AllArgsConstructor;

/**
 * Inner payload of a {@code RoutedCommandPayload} whose {@code innerType} is
 * {@code MessageType.ROOM_ROSTER}. Lets a Shard, which only learns about white/black at
 * {@code room.allocated} time, find out about members who join *after* the game has already
 * started (spectators) - see {@code GameSessionHandle#onMemberJoined}. {@code role} is
 * {@code Room.Role#name()} rather than the enum itself, so this protocol class doesn't need to
 * depend on {@code server.rooms.Room}.
 */
@AllArgsConstructor
public final class RoomRosterPayload {
    public final long userId;
    public final String role;
}
