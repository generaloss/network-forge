package generaloss.networkforge.tcp;

@FunctionalInterface
public interface TCPReceiver {
    
    void receive(TCPConnection sender, byte[] bytes);

}
