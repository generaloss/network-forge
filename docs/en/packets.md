# Packets

---

## What is a packet

TCP transmits **just a stream of bytes**.

For most applications it is more convenient to work not with raw bytes,
but with structured messages.

For this purpose the library provides the concept of **packets**.

A packet is an object that:

* serializes into a byte stream
* can be reconstructed from the stream
* contains structured data.

Packets are an **additional layer on top of TCP**.

---

## Base packet class

All packets inherit from  
[NetPacket](/src/main/java/generaloss/networkforge/packet/NetPacket.java).

A minimal packet looks like this:

```java
@PacketID(0)
public class ChatMessagePacket extends NetPacket {

    private String message;

    public ChatMessagePacket() { }

    public ChatMessagePacket(String message) {
        this.message = message;
    }

    @Override
    protected void write(BinaryOutputStream stream) throws IOException {
        stream.writeByteString(message);
    }

    @Override
    protected void read(BinaryInputStream stream) throws IOException {
        message = stream.readByteString();
    }

    public String getMessage() {
        return message;
    }

}
```

Each packet defines two methods:

* `write()` - for serialization
* `read()` - for deserialization.

---

## Packet format

Each packet sent over the network has the following format:

```
[ packetID ][ packet data... ]
```

* `packetID` is a `short`
* then the packet data is written

The `toByteArray()` method automatically writes the ID and calls `write()`.

---

## Packet IDs

Each packet has a unique `packetID`.

It can be specified explicitly:

``` java
@PacketID(5)
public class LoginPacket extends NetPacket { ... }
```

If the annotation is absent, the ID is automatically calculated
from the class name.

This is convenient for:

* quick prototypes
* internal protocols

For stable protocols it is recommended to assign IDs manually.

---

# Reading packets

Packet reading is handled by the class
[PacketReader](/src/main/java/generaloss/networkforge/packet/PacketReader.java).

It performs:

1. reading the `packetID`
2. creating the packet object
3. deserializing the data

---

## Packet registration

Before reading packets, they must be registered.

``` java
PacketReader reader = new PacketReader();

reader.register(ChatMessagePacket.class);
reader.register(LoginPacket.class);
```

Registration creates a mapping:

```
packetID → factory
```

The factory is used to create packet instances.
It can also be specified manually:

``` java
reader.register(ChatMessagePacket.class, ChatMessagePacket::new);
```

---

## Automatic registration

Packets can be automatically registered from a package:

``` java
reader.registerAllFromPackageRecursive(
    Resource.classpath("myapp/network/packet")
);
```

All classes will be discovered that:

* inherit from `NetPacket`
* are not `abstract`.

---

## Reading a packet

After registration, packets can be read:

```java
NetPacket packet = reader.readOrNull(data);
```

Or with exceptions:

``` java
try {
    NetPacket packet = reader.read(data);
} catch (IOException e) {
    ...
}
```

Or safely using `Optional`:

``` java
reader.tryRead(data).ifPresent(packet -> {
    ...
});
```

---

# Packet handling

After reading, the packet must be passed to a handler.

For this purpose there is
[PacketDispatcher](/src/main/java/generaloss/networkforge/packet/PacketDispatcher.java).

The dispatcher maintains a table:

```
packetClass → handler
```

---

## Registering handlers

``` java
PacketDispatcher dispatcher = new PacketDispatcher();

dispatcher.register(ChatMessagePacket.class,
    (connection, packet) -> {
        System.out.println(packet.getMessage());
    }
);
```

The handler receives the connection and the packet.

---

## Dispatching a packet

After reading, the packet can be passed to the dispatcher:

``` java
NetPacket packet = reader.readOrNull(data);
if(packet != null)
    dispatcher.dispatch(connection, packet);
```

The dispatcher automatically finds the correct handler and invokes it.

---

## Asynchronous processing with Executor

The `receive` event is executed from the selector thread, which should not be heavily loaded.

If packet processing may be expensive, you can delegate packet handling **to another thread**.

``` java
dispatcher.async(Executors.newSingleThreadExecutor());
```

Now every `dispatch()` call is **published to the Executor**, and the main I/O thread is not blocked.

---

## Packet batching

When a server starts receiving many small packets, a problem appears:
each packet triggers a separate processing call.

Packets read during the same read cycle can be collected **into a batch** and processed together:

``` java
List<NetPacket> packetsBatch = new ArrayList<>();

dispatcher.async(Executors.newFixedThreadPool(2));

// collect packets
server.registerOnReceive((sender, data) -> {
    reader.tryRead(data)
        .ifPresent(packetsBatch::add);
});

// dispatch them
server.registerOnReadComplete(connection -> {
    if(packetsBatch.isEmpty())
        return;

    dispatcher.dispatch(connection, packetsBatch);
    packetsBatch.clear();
});
```

Reading happens from a single `connection`, and `onReadComplete` acts as the signal that reading is finished.

Inside `PacketDispatcher`, each packet in the batch will be routed to its handler in the correct order.

All processing performed by the Executor happens within a single task execution.

---

# Using with TCPServer

Example of packet handling on a server:

``` java
PacketReader reader = new PacketReader();
PacketDispatcher dispatcher = new PacketDispatcher();

reader.register(ChatMessagePacket.class);

dispatcher.register(ChatMessagePacket.class,
    (connection, packet) -> {
        System.out.println(packet.getMessage());
    }
);

TCPServer server = new TCPServer();

server.registerOnReceive((connection, data) -> {
    NetPacket packet = reader.readOrNull(data);
    if(packet != null)
        dispatcher.dispatch(connection, packet);
});

server.run(5410);
```

---

# Sending packets

A packet can be sent directly:

``` java
client.send(new ChatMessagePacket("Hello"));
```

The packet is automatically serialized into the format:

```
packetID + packetData
```

---

# When to use packets

Packets are useful if:

* a custom network protocol is used
* messages have structure
* type-safe handling is required

If the application works directly with raw `byte[]`,
using packets is not required.

---

# Summary

The packet system consists of three components:

| Component          | Responsibility              |
|--------------------|-----------------------------|
| `NetPacket`        | Packet structure definition |
| `PacketReader`     | Deserialization             |
| `PacketDispatcher` | Packet routing              |

This allows building a type-safe protocol on top of TCP.

---

*[Main Page](index.md)*