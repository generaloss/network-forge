# Utilities

---

## Starting `TCPServer`

### Multiple ports simultaneously

The server can listen on multiple addresses at once:

``` java
server.run(InetSocketAddress... addresses);
server.run(String hostname, int... ports);
server.run(int... ports);
```

Examples:

``` java
server.run(8080, 8081, 8082);
server.run("localhost", 9000, 9001);
```

---

### Automatic port selection

If `0` is specified, the operating system will choose a free port:

``` java
server.run(0);
// or
server.run();
```

Get the selected port:

``` java
final int port = server.getPorts()[0];
```

Multiple random ports:

``` java
server.run(0, 0);

final int[] ports = server.getPorts();
final int port1 = ports[0];
final int port2 = ports[1];
```

---

## Client connection

### Blocking connection

``` java
client.connect(SocketAddress address, long timeoutMs);
client.connect(SocketAddress address);

client.connect(String hostname, int port, long timeoutMs);
client.connect(String hostname, int port);
```

---

### Asynchronous connection

``` java
CompletableFuture<TCPConnection> future;

future = client.connectAsync(SocketAddress address, long timeoutMs);
future = client.connectAsync(SocketAddress address);

future = client.connectAsync(String hostname, int port, long timeoutMs);
future = client.connectAsync(String hostname, int port);
```

---

### Connecting to the fastest server

If multiple addresses are available and you want to pick the one that responds first:

``` java
CompletableFuture<TCPConnection> future;

future = client.connectFastest(SocketAddress[] addresses, long timeoutMs);
future = client.connectFastest(SocketAddress[] addresses);
```

---

## Connection options

``` java
TCPConnectionOptionsHolder options = new TCPConnectionOptionsHolder();

// Socket options
options.setLinger(1);
options.setTcpNoDelay(true);

// Library options
options.setMaxReadFrameSize(8 * 1024 * 1024); // 8 MB
options.setFrameBufferSizeUpperBound(...);
```

**Important about `setFrameBufferSizeUpperBound()`**

* Used only in the `Framed` codec (`FramedTCPConnectionCodec`).
* Limits the growth of the read buffer:
* If set, the buffer may shrink after receiving large data packets;
* If the value = `0`, the buffer never shrinks.

### Applying options

#### For server

``` java
TCPServer server = new TCPServer();
server.setInitialOptions(options);
server.run(5555);
```

All new connections will be created with these settings.

#### For client

``` java
TCPClient client = new TCPClient();
client.setInitialOptions(options);
client.connect("localhost", 5555);
```

Options are applied to the next connection.

---

## Debugging and diagnostics

Minimal way to enable error logging:

``` java
TCPClient client = new TCPClient();
client.registerOnError(ErrorListener::printError);

TCPServer server = new TCPServer();
server.registerOnError(ErrorListener::printError);
```

This allows you to see errors in the pipeline and handlers.

---

## Attachment

Each `TCPConnection` can store **an arbitrary object** attached to the connection.

This is useful for storing:

* user information
* session state
* protocol context

### Usage

``` java
connection.attach(new UserSession("Steve"));
```

Getting the object:

```java
UserSession session = connection.attachment();
```

### Example

``` java
server.registerOnConnect(connection -> {
    connection.attach(new UserSession());
});

server.registerOnReceive((connection, data) -> {
    UserSession session = connection.attachment();
    session.process(data);
});
```

This eliminates the need to maintain a `Map<TCPConnection, Session>`.

---

## Await Write Drain

`TCPConnection` maintains an **outgoing send queue**.

Sometimes it is necessary to wait until **all data has been sent**.

For this purpose:

``` java
connection.awaitWriteDrain(timeoutMs);
```

### Example

``` java
connection.send(bigPacket);
connection.awaitWriteDrain(5000);
connection.close();
```

This guarantees that:

* all data is actually sent
* the connection does not close too early

---

## Broadcast (TCPServer)

`TCPServer` can **send a message to all connections at once**.

### Simple broadcast

``` java
server.broadcast("Server restarting soon");
```

### Send to everyone except one client

``` java
server.broadcast(senderConnection, message);
```

### Supported types

Broadcast, like `send()`, supports:

* `byte[]`
* `ByteBuffer`
* `String`
* `BinaryStreamWriter`
* `NetPacket`

Example:

``` java
server.broadcast(new ChatMessagePacket("Message"));
```

The method returns the number of connections that **failed to receive the message**.

---

## Getting the list of connections

You can obtain all active server connections:

``` java
Collection<TCPConnection> connections = server.getConnections();
```

---

## Connection state checks

``` java
connection.isConnected();
connection.isClosed();
```

Getting network information:

``` java
connection.getAddress();
connection.getPort();

connection.getLocalAddress();
connection.getLocalPort();

connection.getSocket();
```

---

*[Main Page](index.md)*

*Next - [Codecs](codecs.md)*
