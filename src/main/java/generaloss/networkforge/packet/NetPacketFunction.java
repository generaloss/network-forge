package generaloss.networkforge.packet;

import java.util.function.Function;

public interface NetPacketFunction<R> extends Function<NetPacket<?>, R> { }
