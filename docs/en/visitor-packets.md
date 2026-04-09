# Visitor Packets

---

## What are visitor packets

`VisitorNetPacket` is an extension of the base `NetPacket` class:

```java
public abstract class VisitorNetPacket<H> extends NetPacket {

    public abstract void handle(H handler);

    public Runnable createHandleTask(H handler) {
        return () -> this.handle(handler);
    }

}
```

A regular `NetPacket` only describes the data structure.<br>
The handler invocation is always located outside the packet.

`VisitorNetPacket` introduces the `handle` method,<br>
where the packet itself calls the appropriate handler method.

## Example

```java
public class MyPacket extends VisitorNetPacket<MyProtocol> {

    @Override
    public void handle(MyProtocol handler) {
        handler.handleMyPacket(this);
    }

}
```

Now the packet itself determines which handler method should be called.

---

## Handler

The handler type is defined via the generic `<H>`.

It can be an interface:

```java
public interface MyProtocol {

    void handleMyPacket(MyPacket packet);
    void handleAnotherPacket(AnotherPacket packet);

}
```

or a regular class:

```java
public class MyProtocol {

    void handleMyPacket(MyPacket packet) {
        System.out.println("Received MyPacket");
    }

    void handleAnotherPacket(AnotherPacket packet) {
        System.out.println("Received AnotherPacket");
    }

}
```

---

## Registration in PacketDispatcher

`PacketDispatcher` provides a dedicated method for visitor packets:

``` java
public final <H, P extends VisitorNetPacket<H>> PacketDispatcher register(
    Class<P> packetClass,
    Function<TCPConnection, H> handlerFunction
)
```

It defines how to obtain a handler from a connection.

Example:

``` java
dispatcher.register(
    MyPacket.class,
    connection -> connection.attachment()
);
```

After that, when processing a packet, the dispatcher will execute:

``` java
packet.handle(handlerFunction.apply(connection));
```

---

## Usage without PacketDispatcher

Visitor packets can also be used directly:

``` java
packet.handle(handler);
```

or asynchronously:

``` java
executor.execute(packet.createHandleTask(handler));
```

---

*[Main page](index.md)*