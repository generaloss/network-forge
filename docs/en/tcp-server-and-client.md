
# Example

What the following code does:
1) The client sends the message “_ping_” to the server;
2) The server receives “_ping_” and responds with “_pong_”;
3) The client receives “_pong_” and closes the connection.

``` java
// create server
TCPServer server = new TCPServer()  
    .setOnConnect((connection) -> System.out.println("[Server] New connection"))  
    .setOnDisconnect((connection, reason, e) -> System.out.println("[Server] Lost connection: " + reason))  
    .setOnReceive((sender, bytes) -> {  
        // 2) receive "ping" and immediately send back "pong"
        System.out.println("[Server] Received '" + new String(bytes) + "'");  
        sender.send("pong");  
    });  
server.run(65000);  
  
// create client
TCPClient client = new TCPClient()  
    .setOnConnect((connection) -> System.out.println("[Сlient] Connected"))  
    .setOnDisconnect((connection, reason, e) -> System.out.println("[Сlient] Disconnected: " + reason))  
    .setOnReceive((connection, bytes) -> {  
        // 3) receive "pong" and close the connection  
        System.out.println("[Client] Received '" + new String(bytes) + "'");  
        connection.close();  
    });  
client.connect("localhost", 65000);  
  
// 1) send "ping"
client.send("ping");  
  
// wait until client disconnects
while(!client.isClosed())  
    Thread.onSpinWait();  
  
server.close();
```

---
# `TCPConnection`

The abstract class [`TCPConnection`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/TCPConnection.java) encapsulates a low-level connection and provides an API for interacting with it:
``` java
SocketChannel channel()
Socket socket()
SelectionKey selectionKey()
TCPConnectionOptions options()
CipherPair ciphers()

<O> O attachment()
void attach(Object attachment) // associate with a user object

String getName()
void setName(String name)
String toString() // returns the same as getName()

int getPort()
int getLocalPort()
InetAddress getAddress()
InetAddress getLocalAddress()

boolean isConnected()
boolean isClosed()
void close()

boolean send(byte[] byteArray)
boolean send(ByteBuffer buffer)
boolean send(String string)
boolean send(BinaryStreamWriter streamWriter)
boolean send(NetPacket<?> packet)

static void registerFactory(Class<?> connectionClass, TCPConnectionFactory factory)
static TCPConnectionFactory getFactory(Class<?> connectionClass)
static TCPConnectionFactory getFactory(TCPConnectionType connectionType)
```

## `TCPConnection` implementations

| Class                                                                                                                                               | TCPConnectionType | Description                                                                                                            |
| --------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------- | ---------------------------------------------------------------------------------------------------------------------- |
| [`PacketTCPConnection`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/PacketTCPConnection.java) | **PACKET**        | Reads the entire message; automatically prepends message size when sending.                                            |
| [`StreamTCPConnection`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/StreamTCPConnection.java) | **STREAM**        | Message can be read in parts; data is passed directly.  <br>Suitable for protocols with their own framing (e.g. HTTP). |

Custom implementations of this class are also possible.

By default, both `TCPServer` and `TCPClient` use the **PACKET** type.

---
### [`TCPConnectionFactory`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/TCPConnectionFactory.java)

Factory interface for creating connections:
``` java
TCPConnection create(  
    SocketChannel channel,  
    SelectionKey selectionKey,  
    TCPCloseable onClose  
);
```

---
# Listener-Interfaces
Used to handle connection events.

## [`TCPReceiver`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPReceiver.java)
``` java
void receive(TCPConnection connection, byte[] byteArray)
```
Receives data as a byte array.

---
## [`TCPReceiverStream`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPReceiverStream.java)
``` java
void receive(TCPConnection connection, BinaryInputStream inStream)
```
Provides a [BinaryInputStream](https://github.com/generaloss/resource-flow/tree/master/src/main/java/generaloss/resourceflow/stream/BinaryInputStream.java) for reading (from the [Resource-Flow](https://github.com/generaloss/resource-flow.git)).

---
## [`TCPCloseable`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPCloseable.java)
``` java
void close(TCPConnection connection, TCPCloseReason reason, Exception e)
```
Called when the connection is closed.

---
## [`TCPErrorHandler`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPErrorHandler.java)
``` java
void error(TCPConnection connection, TCPErrorSource source, Throwable throwable)
```
Handles errors in user callbacks.

---
## [`TCPCloseReason`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPCloseReason.java)
Enumeration of reasons for connection closure:

| Value                      | Message                             | Exception |
| -------------------------- | ----------------------------------- | :-------: |
| CLOSE_CONNECTION           | Connection closed                   |     -     |
| CLOSE_CLIENT               | Client closed                       |     -     |
| CLOSE_SERVER               | Server closed                       |     -     |
| CLOSE_BY_OTHER_SIDE        | Connection closed by the other side |     -     |
| PACKET_SIZE_LIMIT_EXCEEDED | Packet size limit exceeded          |     -     |
| INVALID_PACKET_SIZE        | Invalid packet size                 |     -     |
| INTERNAL_ERROR             | Internal error occurred             |     +     |

---
## [`TCPErrorSource`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPErrorSource.java)
Enumeration of sources for user errors:

| Value                |
| -------------------- |
| CONNECT_CALLBACK<br> |
| DISCONNECT_CALLBACK  |
| RECEIVE_CALLBACK     |
| ERROR_CALLBACK       |

---
# `TCPClient`

The [`TCPClient`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/TCPClient.java) class is used to initiate a connection:
``` java
TCPClient()
TCPClient(TCPConnectionOptionsHolder initialOptions)

TCPConnection connection()
TCPConnectionOptions options()
CipherPair ciphers()

// the selected type applies on the next connection
TCPClient setConnectionType(Class<?> tcpConnectionClass)
TCPClient setConnectionType(TCPConnectionType connectionType)

TCPConnectionOptionsHolder getInitialOptions()
TCPClient setInitialOptions(TCPConnectionOptionsHolder initialOptions)

TCPClient setOnConnect(Consumer<TCPConnection> onConnect)
TCPClient setOnDisconnect(TCPCloseable onClose)
TCPClient setOnReceive(TCPReceiver onReceive)
TCPClient setOnReceiveStream(TCPReceiverStream onReceive)
TCPClient setOnError(TCPErrorHandler onError)

TCPClient connect(SocketAddress socketAddress, long timeoutMillis) throws IOException, TimeoutException
TCPClient connect(SocketAddress socketAddress) throws IOException, TimeoutException
TCPClient connect(String hostname, int port, long timeoutMillis) throws IOException, TimeoutException
TCPClient connect(String hostname, int port) throws IOException, TimeoutException

boolean isConnected()
boolean isClosed()
TCPClient close()

TCPClient setEncryptCipher(Cipher encryptCipher)
TCPClient setDecryptCipher(Cipher decryptCipher)
TCPClient setCiphers(Cipher encryptCipher, Cipher decryptCipher)

boolean send(byte[] byteArray)
boolean send(ByteBuffer buffer)
boolean send(String string)
boolean send(BinaryStreamWriter streamWriter)
boolean send(NetPacket<?> packet)
```

---
# `TCPServer`

The [`TCPServer`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/TCPServer.java) class is used to wait for incoming connections:
``` java
TCPServer()
TCPServer(TCPConnectionOptionsHolder initialOptions)

TCPServer setConnectionType(Class<?> tcpConnectionClass)
TCPServer setConnectionType(TCPConnectionType connectionType)

// the selected type applies to subsequent connections
TCPServer setConnectionType(Class<?> tcpConnectionClass)
TCPServer setConnectionType(TCPConnectionType connectionType)

TCPServer setOnConnect(Consumer<TCPConnection> onConnect)
TCPServer setOnDisconnect(TCPCloseable onClose)
TCPServer setOnReceive(TCPReceiver onReceive)
TCPServer setOnReceiveStream(TCPReceiverStream onReceive)
TCPServer setOnError(TCPErrorHandler onError)

TCPServer run(InetAddress address, int... ports) throws IOException
TCPServer run(String hostname, int... ports) throws IOException
TCPServer run(int... ports) throws IOException

Collection<TCPConnection> getConnections()

boolean isRunning()
boolean isClosed()
TCPServer close()

// returns the number of failed sends
int broadcast(byte[] byteArray)
int broadcast(TCPConnection except, byte[] byteArray)

int broadcast(ByteBuffer buffer)
int broadcast(TCPConnection except, ByteBuffer buffer)

int broadcast(String string)
int broadcast(TCPConnection except, String string)

int broadcast(BinaryStreamWriter streamWriter)
int broadcast(TCPConnection except, BinaryStreamWriter streamWriter)

int broadcast(NetPacket<?> packet)
int broadcast(TCPConnection except, NetPacket<?> packet)
```

---
# [`CipherPair`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/CipherPair.java)

Wrapper around two `Cipher` instances for encryption/decryption:
``` java
// getters and setters for Ciphers
Cipher getEncryptCipher()
void setEncryptCipher(Cipher encryptCipher)

Cipher getDecryptCipher()
void setDecryptCipher(Cipher decryptCipher)

void setCiphers(Cipher encryptCipher, Cipher decryptCipher)

// returns the same data if the corresponding Cipher is null
byte[] encrypt(byte[] byteArray)
byte[] decrypt(byte[] byteArray)

int getEncryptedSize(int inputSize)
int getDecryptedSize(int inputSize)
```

---
# Connection Options

## [`TCPConnectionOptionsHolder`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/options/TCPConnectionOptionsHolder.java)

Holds TCP connection configuration and is applied when a new connection is created.  
Fields with `null` values are ignored.
``` java
TCPConnectionOptionsHolder(Socket socket)

// maximum packet size when reading
int getMaxPacketSizeRead()
TCPConnectionOptionsHolder setMaxPacketSizeRead(int maxPacketSizeRead)

// maximum packet size when writing
int getMaxPacketSizeWrite()
TCPConnectionOptionsHolder setMaxPacketSizeWrite(int maxPacketSizeWrite)

// sets both read and write packet size limits
TCPConnectionOptionsHolder setMaxPacketSize(int maxPacketSize)

// close the connection if a packet exceeds the limit; otherwise, discard extra data
boolean isCloseOnPacketLimit()
TCPConnectionOptionsHolder setCloseOnPacketLimit(boolean closeOnPacketLimit)

// copy these options into TCPConnectionOptions (used automatically)
void copyTo(TCPConnectionOptions options)

String toString()

// other options from SocketOptionsHolder...
```
Inherits from [`SocketOptionsHolder`](#SocketOptionsHolder).

---
## [`SocketOptionsHolder`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/options/SocketOptionsHolder.java)

Contains `Socket`-level settings:
``` java
SocketOptionsHolder(Socket socket)

Socket getSocket()

Boolean isTcpNoDelay()
SocketOptionsHolder setTcpNoDelay(Boolean tcpNoDelay) // disable/enable Nagle’s algorithm

Boolean isTcpQuickAck()
SocketOptionsHolder setTcpQuickAck(Boolean tcpQuickAck)

Integer getTcpKeepIdle()
SocketOptionsHolder setTcpKeepIdle(Integer tcpKeepIdle)

Integer getTcpKeepInterval()
SocketOptionsHolder setTcpKeepInterval(Integer tcpKeepInterval)

Integer getTcpKeepCount()
SocketOptionsHolder setTcpKeepCount(Integer tcpKeepCount)

Integer getTrafficClass()
SocketOptionsHolder setTrafficClass(Integer trafficClass) // low_cost (0x02) | reliability (0x04) | throughput (0x08) | low_latency (0x10)

Boolean isOOBInline()
SocketOptionsHolder setOOBInline(Boolean oobInline) // enable/disable in-band reception of TCP urgent data

Boolean isKeepAlive() 
SocketOptionsHolder setKeepAlive(Boolean keepAlive) // enable/disable keep-alive feature

Boolean isReuseAddress()
SocketOptionsHolder setReuseAddress(Boolean reuseAddress) // enable address reuse for this socket

Boolean isReusePort()
SocketOptionsHolder setReusePort(Boolean reusePort)

Integer getReceiveBufferSize()
SocketOptionsHolder setReceiveBufferSize(Integer receiveBufferSize) // size of receive buffer actually used by the platform

Integer getSendBufferSize()
SocketOptionsHolder setSendBufferSize(Integer sendBufferSize) // hint for size of send buffer

Integer getLinger()
SocketOptionsHolder setSoLinger(Integer linger) // delay closing if data remains unsent

// apply non-null options to channel (used automatically)
void applyPreConnect(SocketChannel channel)
void applyPostConnect(SocketChannel channel)
void applyServerPreBind(ServerSocketChannel channel)

String toString()
```

---
## [`TCPConnectionOptions`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/options/TCPConnectionOptions.java)

Runtime (applied) connection settings.
``` java
TCPConnectionOptions(Socket socket)

int getMaxPacketSizeRead()
TCPConnectionOptions setMaxPacketSizeRead(int maxPacketSizeRead)

int getMaxPacketSizeWrite()
TCPConnectionOptions setMaxPacketSizeWrite(int maxPacketSizeWrite)

TCPConnectionOptions setMaxPacketSize(int maxPacketSize)

boolean isCloseOnPacketLimit()
TCPConnectionOptions setCloseOnPacketLimit(boolean closeOnPacketLimit)

String toString()

// other methods from SocketOptions...
```
Inherits from [`SocketOptions`](#SocketOptions).

---
## [`SocketOptions`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/options/SocketOptions.java)

Runtime settings for a `Socket`:
``` java
SocketOptions(Socket socket)

Socket getSocket()

Boolean isTcpNoDelay()
SocketOptions setTcpNoDelay(Boolean tcpNoDelay)

Boolean isTcpQuickAck()
SocketOptions setTcpQuickAck(Boolean tcpQuickAck)

Integer getTcpKeepIdle()
SocketOptions setTcpKeepIdle(Integer tcpKeepIdle)

Integer getTcpKeepInterval()
SocketOptions setTcpKeepInterval(Integer tcpKeepInterval)

Integer getTcpKeepCount()
SocketOptions setTcpKeepCount(Integer tcpKeepCount)

Integer getTrafficClass()
SocketOptions setTrafficClass(Integer trafficClass)

Boolean isOOBInline()
SocketOptions setOOBInline(Boolean oobInline)

Boolean isKeepAlive() 
SocketOptions setKeepAlive(Boolean keepAlive)

Boolean isReuseAddress()
SocketOptions setReuseAddress(Boolean reuseAddress)

Boolean isReusePort()
SocketOptions setReusePort(Boolean reusePort)

Integer getReceiveBufferSize()
SocketOptions setReceiveBufferSize(Integer receiveBufferSize)

Integer getSendBufferSize()
SocketOptions setSendBufferSize(Integer sendBufferSize)

Integer getLinger()
SocketOptions setSoLinger(boolean on, int linger)

String toString()
```

---
