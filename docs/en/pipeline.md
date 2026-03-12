# Pipeline

---

## What is a pipeline

A pipeline is an extension mechanism.

New functionality is added not by modifying the core of the library,
but by attaching additional handlers such as:

* metrics
* encryption
* authorization
* and more.

**The core remains unchanged. Behavior is defined by the configuration of the handler chain.**

[EventPipeline](/src/main/java/generaloss/networkforge/tcp/pipeline/EventPipeline.java)
is a chain of handlers
([EventHandler](/src/main/java/generaloss/networkforge/tcp/pipeline/EventHandler.java))
through which every event passes:

* connection
* disconnection
* incoming data
* outgoing data
* errors.

An event is not broadcast. Instead, it travels step by step through the chain, where each handler can:

* stop further processing
* initiate sending
* modify the data
* close the connection.

---

## Event propagation directions

There are two directions of event propagation in the system:

* **Inbound** events move from the top of the chain to the bottom.
* **Outbound** events move from the bottom upward.

### Inbound (incoming events)

Events travel **from the beginning of the chain to the end**:

```

Handler_0 → Handler_1 → Handler_2 → Target

```

These include:

* `connect`
* `receive`
* `disconnect`
* `error`

Each handler decides whether the chain should continue.  
If a method returns `false`, propagation stops.

---

### Outbound (outgoing events)

The `send` event travels **in the opposite direction**:

```

Handler_2 → Handler_1 → Handler_0 → Socket

```

This allows handlers to:

* encrypt data
* compress data
* log outgoing messages

---

## Execution context

Each event is processed within the context
[EventInvocationContext](/src/main/java/generaloss/networkforge/tcp/pipeline/EventInvocationContext.java).

The context stores:

* the current position in the chain
* a reference to the connection
* a snapshot of the handler list

---

## Handler snapshot

During event processing, a **snapshot of the handlers** is used.

Because of this, changes to the pipeline (adding or removing handlers)
do not affect events that are already running.

**Each event uses the snapshot of handlers that existed at the moment the event started.**

When handlers are added or removed, a new array is created.  
Running events continue using the previous version.

This provides predictable processing:

* no locks or race conditions
* stable invocation order

The trade-off is copying the handler array during modification.  
Since pipeline modifications are rare,
this cost is acceptable.

---

## A simple handler

To create a handler, simply extend `EventHandler`  
and override the methods you need.

```java
public class LoggingHandler extends EventHandler {
    @Override
    public boolean handleConnect(EventInvocationContext context) {
        System.out.println("Connected: " + context.getConnection());
        return true;
    }

    @Override
    public boolean handleDisconnect(EventInvocationContext context, CloseReason reason, Exception e) {
        System.out.println("Disconnected: " + reason);
        return true;
    }

    @Override
    public boolean handleReceive(EventInvocationContext context, byte[] data) {
        System.out.println("Received " + data.length + " bytes");
        return true;
    }

    @Override
    public boolean handleError(EventInvocationContext context, ErrorSource source, Throwable throwable) {
        System.out.println("Error (" + source + "): " + throwable.getMessage());
        return true;
    }

    @Override
    public boolean handleSend(EventInvocationContext context, byte[] data) {
        System.out.println("Sending " + data.length + " bytes");
        return true;
    }
}
```

If a method returns `true`, the event continues through the chain.
If it returns `false`, processing stops.

Methods can be overridden selectively.
You do not need to implement every event.

---

## Processing thread

`receive` events are invoked from the selector thread.

This thread is responsible for:

* handling network events
* reading data from sockets
* writing data.

Because of this, pipeline handlers should be **as lightweight as possible.**

It is not recommended to perform:

* blocking operations
* long computations.

---

## Initiating events

A handler can initiate new events that will continue through the remaining handlers:

```java
@Override
public boolean handleReceive(EventInvocationContext context, byte[] data) {
    context.send(message_to_send);
    context.receive(message_to_receive);
    context.connect(connection);
    context.disconnect(connection, CloseReason.INTERNAL_ERROR, exception);
    context.error(ErrorSource.RECEIVE_HANDLER, throwable);
    return true;
}
```

An event initiated from a handler **continues within the same handler snapshot.**
This guarantees that the handler order remains stable
throughout the entire event.

### This allows:

* Modifying event data by interrupting the current event and initiating a new one:

```java
public class SendPrefixHandler extends EventHandler {

    @Override
    public boolean handleSend(EventInvocationContext context, byte[] data) {
        byte[] prefix = "[Prefix] ".getBytes();
        byte[] modified = this.addPrefix(data, prefix);
        context.send(modified); // Initiate a new event with modified data
        return false; // Stop the current event
    }

    private byte[] addPrefix(byte[] input, byte[] prefix) {
        byte[] result = new byte[prefix.length + input.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(input, 0, result, prefix.length, input.length);
        return result;
    }

}
```

* Delaying an event until the required moment:

``` java
public class PrefixHandler extends EventHandler {

    private final List<TCPConnection> postponeList = Collections.synchronizedList(new ArrayList<>());

    @Override
    public boolean handleConnect(EventInvocationContext context) {
        postponeList.add(context.getConnection()); // Store connections to trigger `connect` later
        return false; // Stop `connect` events at this handler
    }

    @Override
    public boolean handleReceive(EventInvocationContext context, byte[] data) {
        TCPConnection connection = context.getConnection();

        boolean isRightConnection = postponeList.contains(connection);
        boolean isRightData = ...; // For example, if specific data was received for this connection

        if(isRightConnection && isRightData) {
            postponeList.remove(connection);
            context.connect(connection); // Initiate `connect` for the remaining handlers
            return false; // Do not pass this data further
        }
        return true;
    }

}
```

---

*[Main Page](index.md)*

*Next - [Packets](packets.md)*