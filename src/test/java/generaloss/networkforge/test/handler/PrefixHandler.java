package generaloss.networkforge.test.handler;

import generaloss.networkforge.tcp.pipeline.EventHandler;
import generaloss.networkforge.tcp.pipeline.EventInvocationContext;

public class PrefixHandler extends EventHandler {

    @Override
    public boolean handleSend(EventInvocationContext context, byte[] data) {
        byte[] prefix = "[SERVER] ".getBytes();
        byte[] result = this.addPrefix(data, prefix);

        context.send(result);
        return false;
    }

    private byte[] addPrefix(byte[] input, byte[] prefix) {
        byte[] result = new byte[prefix.length + input.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(input, 0, result, prefix.length, input.length);
        return result;
    }

}