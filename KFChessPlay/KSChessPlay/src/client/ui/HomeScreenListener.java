package client.ui;

public interface HomeScreenListener {
    void onPlayRequested();
    void onPlayCancelled();
    void onRoomCreateRequested();
    void onRoomJoinRequested(String roomId);
}
