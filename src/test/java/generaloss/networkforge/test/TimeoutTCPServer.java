package generaloss.networkforge.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TimeoutTCPServer {

    private final int port;
    private final long acceptDelayMillis;

    private volatile boolean running;
    private ServerSocketChannel serverChannel;

    public TimeoutTCPServer(int port, long acceptDelayMillis) {
        this.port = port;
        this.acceptDelayMillis = acceptDelayMillis;
    }

    public void start() throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(true);

        running = true;
        final Thread thread = new Thread(this::runLoop, "TimeoutTCPServer-" + port);
        thread.setDaemon(true);
        thread.start();
    }

    @SuppressWarnings("BusyWait")
    private void runLoop() {
        try {
            while (running) {
                final SocketChannel client = serverChannel.accept();
                if(client != null) {
                    // imit connecting
                    Thread.sleep(acceptDelayMillis);
                }
            }
        } catch (Exception ignored) { }
    }

    public void stop() {
        running = false;
        try {
            if(serverChannel != null)
                serverChannel.close();
        } catch (IOException ignored) { }
    }
}