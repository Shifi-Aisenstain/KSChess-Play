package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class RoomAllocatedPayload {
    public final String roomId;
    public final String shardId;
    public final long whiteId;
    public final String whiteUsername;
    public final int whiteElo;
    public final long blackId;
    public final String blackUsername;
    public final int blackElo;
}
