package generaloss.networkforge.tcp.listener;

import generaloss.networkforge.tcp.TCPConnection;

import java.util.function.Consumer;

@FunctionalInterface
public interface TCPConnectionConsumer extends Consumer<TCPConnection> { }
