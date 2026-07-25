package client.ui;

public interface HomeScreenListener {
    void onPlayRequested();
    void onPlayCancelled();
    /** {@code desiredRoomId} may be blank - the server auto-generates an ID in that case. */
    void onRoomCreateRequested(String desiredRoomId);
    void onRoomJoinRequested(String roomId);
}
