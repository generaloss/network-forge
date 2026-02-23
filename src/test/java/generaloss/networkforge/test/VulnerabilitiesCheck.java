package generaloss.networkforge.test;

import generaloss.chronokit.TimeUtils;
import generaloss.networkforge.tcp.TCPClient;
import generaloss.networkforge.tcp.TCPConnection;
import generaloss.networkforge.tcp.TCPServer;
import generaloss.networkforge.tcp.codec.CodecType;
import generaloss.networkforge.tcp.listener.CloseReason;
import generaloss.networkforge.tcp.listener.ErrorSource;
import generaloss.networkforge.tcp.options.TCPConnectionOptionsHolder;
import generaloss.networkforge.tcp.pipeline.EventHandlerLayer;
import generaloss.networkforge.tcp.pipeline.EventPipeline;
import generaloss.networkforge.tcp.pipeline.EventPipelineContext;
import generaloss.networkforge.tcp.pipeline.ListenersHolder;

import java.io.IOException;
import java.util.Arrays;

public class VulnerabilitiesCheck {

    public static void main(String[] args) throws Exception {
        memoryOverflow(); // 26.2.1 passed
    }

    private static void memoryOverflow() throws IOException {
        final TCPConnectionOptionsHolder options = new TCPConnectionOptionsHolder();
        options.setCloseOnFrameReadSizeExceed(false);

        final TCPServer server = new TCPServer();
        server.setInitialOptions(options);
        server.setCodecFactory(CodecType.FRAMED);
        server.run(5400);

        final TCPClient client = new TCPClient();
        client.setInitialOptions(options);
        client.setCodec(CodecType.STREAM); // to imitate CodecType.FRAMED
        client.connect("localhost", 5400);

        // send maximum frame size
        final byte[] maxSizeFrameHeader = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}; // int
        client.send(maxSizeFrameHeader);

        // trying to overflow the memory on the server
        final byte[] message = "DATA".repeat(10_000).getBytes();
        while(client.isOpen()) {
            client.send(message);
            TimeUtils.delayMillis(1);
        }

        TimeUtils.waitFor(server::isClosed, 60_000);
    }

    public static void pipelineTest() throws IOException {
        final ListenersHolder target = new ListenersHolder();
        target.registerOnConnect((connection) ->
                                     System.out.println("Target.handleConnect(" + connection + ")")
        );
        target.registerOnDisconnect((connection, reason, e) ->
                                        System.out.println("Target.handleDisconnect(" + connection + ")")
        );
        target.registerOnReceive((connection, data) ->
                                     System.out.println("Target.handleReceive(" + connection + ")")
        );
        target.unregisterOnError((connection, source, throwable) ->
                                     System.out.println("Target.handleError(" + connection + ")")
        );
        target.registerOnSend((connection, data) ->
                                  System.out.println("Target.handleSend(" + connection + ")")
        );

        final EventHandlerLayer layer1 = new EventHandlerLayer() {
            @Override
            public boolean handleConnect(EventPipelineContext context) {
                System.out.println("Layer_1.handleConnect(" + context.getConnection() + ")");
                return super.handleConnect(context);
            }
            @Override
            public boolean handleDisconnect(EventPipelineContext context, CloseReason reason, Exception e) {
                System.out.println("Layer_1.handleDisconnect(" + context.getConnection() + ", " + reason + ", " + e + ")");
                return super.handleDisconnect(context, reason, e);
            }
            @Override
            public boolean handleReceive(EventPipelineContext context, byte[] data) {
                System.out.println("Layer_1.handleReceive(" + context.getConnection() + ", " + Arrays.toString(data) + ")");
                return super.handleReceive(context, data);
            }
            @Override
            public boolean handleError(EventPipelineContext context, ErrorSource source, Throwable throwable) {
                System.out.println("Layer_1.handleError(" + context.getConnection() + ", " + source + ", " + throwable + ")");
                return super.handleError(context, source, throwable);
            }
            @Override
            public byte[] handleSend(EventPipelineContext context, byte[] data) {
                System.out.println("Layer_1.handleSend(" + context.getConnection() + ", " + Arrays.toString(data) + ")");
                return super.handleSend(context, data);
            }
        };

        final EventHandlerLayer layer2 = new EventHandlerLayer() {
            @Override
            public boolean handleConnect(EventPipelineContext context) {
                System.out.println("Layer_2.handleConnect(" + context.getConnection() + ")");
                return super.handleConnect(context);
            }
            @Override
            public boolean handleDisconnect(EventPipelineContext context, CloseReason reason, Exception e) {
                System.out.println("Layer_2.handleDisconnect(" + context.getConnection() + ", " + reason + ", " + e + ")");
                return super.handleDisconnect(context, reason, e);
            }
            @Override
            public boolean handleReceive(EventPipelineContext context, byte[] data) {
                System.out.println("Layer_2.handleReceive(" + context.getConnection() + ", " + Arrays.toString(data) + ")");
                return super.handleReceive(context, data);
            }
            @Override
            public boolean handleError(EventPipelineContext context, ErrorSource source, Throwable throwable) {
                System.out.println("Layer_2.handleError(" + context.getConnection() + ", " + source + ", " + throwable + ")");
                return super.handleError(context, source, throwable);
            }
            @Override
            public byte[] handleSend(EventPipelineContext context, byte[] data) {
                System.out.println("Layer_2.handleSend(" + context.getConnection() + ", " + Arrays.toString(data) + ")");
                return super.handleSend(context, data);
            }
        };

        final EventPipeline pipeline = new EventPipeline(target);
        pipeline.getHandlers().addLast(layer1);
        pipeline.getHandlers().addLast(layer2);

        final TCPServer server = new TCPServer();
        server.run(5403);
        final TCPClient client = new TCPClient();
        client.connect("localhost", 5403);
        TCPConnection connection = client.getConnection();
        server.close();

        pipeline.fireConnect(connection);
        pipeline.fireSend(connection, new byte[] { 54, 54 });
    }

}
