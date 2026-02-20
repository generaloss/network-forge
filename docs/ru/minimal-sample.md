# Минимальный пример

### Сервер
``` java
TCPServer server = new TCPServer();

// Слушаем данные от подключенных клиентов
server.registerOnReceive((sender_connection, bytes) -> {
    String message = new String(bytes);
    System.out.println(message); // Вывод 'Hello'

    server.close();
});

server.run(5555);
```

### Клиент

``` java
TCPClient client = new TCPClient();
client.connect("localhost", 5555);

client.send("Hello");

client.awaitWriteDrain(1000L); // Ждем чтобы клиент не отключился слишком рано
client.close();
```

---

*[Главная страница](index.md)*

*Следующая - [Кодеки](codecs.md)*