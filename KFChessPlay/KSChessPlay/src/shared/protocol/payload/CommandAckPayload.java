package shared.protocol.payload;

import lombok.AllArgsConstructor;

/**
 * Reply payload for a MOVE/JUMP {@code RoutedCommandPayload} sent via {@code MessageBus#request}
 * (see PLAN_split_6_services.md §3.1's risk note on preserving the handler's existing synchronous
 * accept/reject contract instead of making it fire-and-forget from day one).
 */
@AllArgsConstructor
public final class CommandAckPayload {
    public final boolean accepted;
}
