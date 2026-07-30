package client.ui;

public interface HomeScreenListener {
    void onPlayRequested();
    void onPlayCancelled();

    void onRoomCreateRequested(String desiredRoomId);
    void onRoomJoinRequested(String roomId);
}
