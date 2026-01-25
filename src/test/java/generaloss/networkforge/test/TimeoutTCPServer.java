package generaloss.networkforge.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TimeoutTCPServer {

    private final int port;
    private final int serverTimeoutMillis;
    private volatile boolean running;

    public TimeoutTCPServer(int port, int serverTimeoutMillis) {
        this.port = port;
        this.serverTimeoutMillis = serverTimeoutMillis;
        this.running = true;
    }

    public void start() {
        final Thread thread = new Thread(() -> {
            try(final ServerSocket serverSocket = new ServerSocket(port)) {
                serverSocket.setSoTimeout(0);
                System.out.println("Timeout TCP server started on port " + port);

                while(running) {
                    final Socket clientSocket = serverSocket.accept();
                    System.out.println("New connection from: " + clientSocket.getInetAddress());

                    clientSocket.setSoTimeout(serverTimeoutMillis);

                    new Thread(() -> handleWithTimeout(clientSocket)).start();
                }
            } catch(IOException e) {
                if(running) {
                    e.printStackTrace();
                }
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    private void handleWithTimeout(Socket clientSocket) {
        try {
            final byte[] buffer = new byte[1024];

            final int bytesRead = clientSocket.getInputStream().read(buffer);

            if(bytesRead > 0)
                System.out.println("Received " + bytesRead + " bytes, but ignoring them...");

            Thread.sleep(serverTimeoutMillis + 1000);

        } catch(java.net.SocketTimeoutException e) {
            System.out.println("Socket timeout for: " + clientSocket.getInetAddress());
        } catch(Exception ignored) {
        } finally {
            closeQuietly(clientSocket);
        }
    }

    private void closeQuietly(Socket socket) {
        try {
            if(socket != null && !socket.isClosed())
                socket.close();
        } catch(IOException ignored) { }
    }

    public void stop() {
        running = false;
    }

}