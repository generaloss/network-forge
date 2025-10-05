package generaloss.networkforge.packet;

public interface TestPacketHandler {

    void handleMessage(String message);
    void handleDisconnect(String reason);

}