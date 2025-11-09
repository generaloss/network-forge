package generaloss.networkforge.packet;

class PacketHandleTask<H> {

    private final NetPacket<H> packet;
    private final H handler;

    public PacketHandleTask(NetPacket<H> packet, H handler) {
        this.packet = packet;
        this.handler = handler;
    }

    public boolean executePacketHandle() {
        return executePacketHandle(packet, handler);
    }

    @SuppressWarnings("CallToPrintStackTrace")
    public static <H> boolean executePacketHandle(NetPacket<H> packet, H handler) {
        try {
            packet.handle(handler);
            return true;

        } catch (Throwable t) {
            System.err.println("[" + PacketDispatcher.class.getSimpleName() + "] Error handling NetPacket '" + packet.getClass().getSimpleName() + "':");
            t.printStackTrace();
            System.err.println();
            return false;
        }
    }

}