package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Inner payload of a RESIGN {@code RoutedCommandPayload}. {@code dueToDisconnect} distinguishes a
 *  voluntary resignation ({@code GameSessionHandle#handleResign}) from the auto-resign fired by
 *  {@code DisconnectWatchdog} ({@code GameSessionHandle#forceResignDueToDisconnect}) - the two carry
 *  different game-over reasons ("resignation" vs "disconnect_timeout"). */
@AllArgsConstructor
public final class BusResignPayload {
    public final long userId;
    public final boolean dueToDisconnect;
}
