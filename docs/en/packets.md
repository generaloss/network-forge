What are packets?

Instead of working with **raw bytes** in network communication, it’s more convenient to think in terms of **packets**.
	
- has a unique `ID` (can be set manually or automatically computed from the class name);
	
- can **write itself** to a stream (`write`);
	
- can **read itself** from a stream (`read`);
	
- can **handle itself** (`handle`), once delivered to a handler.


This way, the logic of network interaction is separated from the low-level byte protocol.

---
## Core Components

### `NetPacket<H>`

The base abstract packet class.
	
- `write(BinaryOutputStream stream)`  -  serialization to a stream.
	
- `read(BinaryInputStream stream)`  -  deserialization from a stream.
	
- `handle(H handler)`  -  processing logic.
	
- `getPacketID()`  -  returns the unique packet ID.
	
- `toByteArray()`  -  converts the packet into a byte array for sending.

Minimal example:

``` java
@PacketID(1) // can be set manually, otherwise computed from class name
public class ChatMessagePacket extends NetPacket<ChatHandler> {
	
    private String message;
	
    public ChatMessagePacket() {} // no-arg constructor required
	
    public ChatMessagePacket(String message) {
        this.message = message;
    }
	
    @Override
    public void write(BinaryOutputStream stream) throws IOException {
        stream.writeStringUTF(message);
    }
	
    @Override
    public void read(BinaryInputStream stream) throws IOException {
        this.message = stream.readStringUTF();
    }
	
    @Override
    public void handle(ChatHandler handler) {
        handler.onChatMessage(message);
    }
}
```

### `PacketID`

Annotation for assigning a packet ID.
	
- If not specified, the ID is computed from the class name hash.
    
- It’s recommended to specify it explicitly for protocol stability.

---
### `NetPacketFactory<H>`

Factory for creating new packet instances.

Usually not needed directly — `PacketDispatcher` generates it automatically via reflection when registering a packet class.

---
### `PacketDispatcher`

The main packet manager. It does three things:
	
1. **Registers packets** (`register(...)`).
    
2. **Reads a packet from a byte array** (`dispatch(...)`).

3. *With enabled `directHandling` packet is immediately* **processed** *in `dispatch(...)`, otherwise it is queued for processing.*
    
4. **Processes the packet queue** (`handlePackets()`).

---
## Step-by-step workflow
	
1. The networking layer receives a byte array from a TCP connection.
    
2. The first `short` in the array is the `packetID`.
    
3. `PacketDispatcher` finds the factory for that ID.
    
4. A new packet instance is created, and `read()` is called.
    
5. The packet is queued.
    
6. When `handlePackets()` is invoked, `packet.handle(handler)` is executed.

---
## Usage Example

### 1. Handler

Interface/class that will receive packets:

``` java
public interface ChatHandler {
    void onChatMessage(String message);
}
```

### 2. Packet

See `ChatMessagePacket` above.

### 3. Packet Registration

``` java
PacketDispatcher dispatcher = new PacketDispatcher();

// register with a manual factory
dispatcher.register(ChatMessagePacket.class, ChatMessagePacket::new);
// register with auto-generated factory
dispatcher.register(ChatMessagePacket.class);
// register a group of packets with auto-generated factories
dispatcher.registerAll(FirstPacket.class, SecondPacket.class, ...);
```

### 4. Sending

``` java
ChatMessagePacket packet = new ChatMessagePacket("Hello, world!");
// send through TCP connection
connection.send(packet);
```

### 5. Receiving & Handling

``` java
// handler instance
ChatHandler chatHandler = new ChatHandler() {
    @Override
    public void onChatMessage(String message) {
        System.out.println("Got: " + message);
    }
};

// assume bytes came from TCP
dispatcher.dispatch(receivedBytes, chatHandler);
// then process the queue (if directHandling disabled)
dispatcher.handlePackets();
```

Output:
```
Got: Hello, world!
```

> [!TIP]
> If you need to select a packet handler dynamically based on the packet's contents, you can provide a custom handler-selector function to the dispatch method:
``` java
NetPacketFunction<Object> handlerFunction = (packet) -> {
    switch(packet.getPacketID()) {
        case 1: return specialHandler;
        case 2: return secondaryHandler;
        default: return defaultHandler;
    }
};

dispatcher.dispatch(receivedBytes, handlerFunction);
```

## Handling Multiple Packets

You can register multiple packets at once:

``` java
dispatcher.registerAll(
    ChatMessagePacket.class,
    PlayerJoinPacket.class,
    PlayerLeavePacket.class
);
```

## Usage Tips

- **Protocol stability:** always specify `@PacketID` manually; otherwise, renaming a class changes its ID.
    
- **Performance:** packets are queued and processed asynchronously. You can call `dispatcher.handlePackets()` inside your game/main loop.
    
- **Thread safety:** uses `ConcurrentHashMap` and `ConcurrentLinkedQueue`, so packets can be processed across multiple receive threads.

---
## Summary

- `NetPacket` = data unit + logic.
    
- `PacketDispatcher` = registration, reading, and processing
    
- The code becomes cleaner, more maintainable, and extensible.
