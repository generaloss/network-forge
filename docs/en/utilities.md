# Utilities

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
connection.awaitWriteDrain(timeoutMillis);
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
