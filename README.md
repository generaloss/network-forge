# [Network Forge](https://github.com/generaloss/network-forge)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.generaloss/network-forge.svg)](https://mvnrepository.com/artifact/io.github.generaloss/network-forge)

A lightweight and flexible Java networking library for building custom networked applications.

It provides **packet-oriented TCP connections**, **fine-grained socket option control**, and **optional encryption** with JCE ciphers.

---

## Installation

Add the dependency from Maven Central:

``` xml
<dependency>
    <groupId>io.github.generaloss</groupId>
    <artifactId>network-forge</artifactId>
    <version>25.10.2</version>
</dependency>
```

---

## Quick Start

### TCP Server

``` java
TCPServer server = new TCPServer();
server.setOnConnect(connection -> {
    connection.send("Hello, client!");
});
server.setOnReceive((senderConnection, byteArray) -> {
    String received = new String(byteArray);
    System.out.println(received); // Output: Hello, server!
});
server.setOnDisconnect((connection, reason, e) -> {
    server.close(); // close server
});
server.run(5555);
```

### TCP Client

``` java
TCPClient client = new TCPClient();
client.setOnReceive((connection, byteArray) -> {
    String received = new String(byteArray);
    System.out.println(received); // Output: Hello, client!
    client.close(); // disconnect client
});
client.connect("localhost", 5555);
client.send("Hello, server!");
```

---

## Documentation

See the [**Wiki**](https://github.com/generaloss/network-forge/wiki) for full guides and API documentation.

---

## Related Projects

* [Resource Flow](https://github.com/generaloss/resource-flow) - utility library used by Network Forge

