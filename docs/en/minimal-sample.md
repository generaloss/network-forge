# Minimal Example

### Server
``` java
TCPServer server = new TCPServer();

// Listen for data from connected clients
server.registerOnReceive((sender_connection, bytes) -> {
    String message = new String(bytes);
    System.out.println(message); // Prints 'Hello'

    server.close();
});

server.run(5555);
````

### Client

``` java
TCPClient client = new TCPClient();
client.connect("localhost", 5555);

client.send("Hello");

client.awaitWriteDrain(1000L); // Wait so the client doesn't disconnect too early
client.close();
```

---

*[Main Page](index.md)*

*Next - [Utilities](utilities.md)*
