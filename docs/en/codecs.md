# Codecs

---

## What is a codec

**Codecs** are classes responsible for the format in which the server and client exchange data.

Every `TCPConnection` uses a **codec**.

---

## Usage

For predictable behavior, the same codec type must be used:

- for the client connection
- and for the corresponding connection on the server.

Otherwise both sides will stop understanding each other, as in the example below:

![](/docs/connection-codecs.png)

To set a codec for `TCPClient`, use:

``` java
client.setCodec(CodecType); // Enum
client.setCodec(ConnectionCodec connectionCodec); // Existing or custom implementations
```

The same applies to individual `TCPConnection` instances.

For `TCPServer`:

``` java
server.setCodecFactory(CodecType codecType); // Enum
server.setCodecFactory(ConnectionCodecFactory codecFactory); // Factory for existing or custom implementations
```

---

## Codec types

The library provides the following implementations:

| CodecType               | ConnectionCodec Class                                                                                  | Description                                                                                                                             |
|-------------------------|--------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| **FRAMED**<br>(default) | [`FramedConnectionCodec`](/src/main/java/generaloss/networkforge/tcp/codec/FramedConnectionCodec.java) | A frame length is written before each message, creating clear message boundaries. <br>Convenient for packet-based protocols.            |
| **STREAM**              | [`StreamConnectionCodec`](/src/main/java/generaloss/networkforge/tcp/codec/StreamConnectionCodec.java) | Reads everything that arrives; a message may be received in parts. <br>Useful for streaming protocols or existing text-based protocols. |

> [!NOTE]
>
> The FRAMED codec allows sending zero-length packets.

---

*[Main Page](index.md)*

*Next - [Pipeline](pipeline.md)*
