# Полезные функции

---

## Attachment

Каждое `TCPConnection` может хранить **произвольный объект**, привязанный к соединению.

Это удобно для хранения:

* информации о пользователе
* состояния сессии
* контекста протокола

### Использование

``` java
connection.attach(new UserSession("Steve"));
```

Получение объекта:

```java
UserSession session = connection.attachment();
```

### Пример

``` java
server.registerOnConnect(connection -> {
    connection.attach(new UserSession());
});

server.registerOnReceive((connection, data) -> {
    UserSession session = connection.attachment();
    session.process(data);
});
```

Это избавляет от необходимости держать `Map<TCPConnection, Session>`.

---

## Await Write Drain

`TCPConnection` поддерживает **очередь отправки**.

Иногда необходимо дождаться, пока **все данные будут отправлены**.

Для этого используется:

``` java
connection.awaitWriteDrain(timeoutMillis);
```

### Пример

``` java
connection.send(bigPacket);
connection.awaitWriteDrain(5000);
connection.close();
```

Это гарантирует, что:

* все данные действительно отправлены
* соединение не закроется раньше времени

---

## Broadcast (TCPServer)

`TCPServer` умеет **отправлять сообщение всем соединениям сразу**.

### Простая рассылка

``` java
server.broadcast("Server restarting soon");
```

### Отправка всем кроме одного клиента

``` java
server.broadcast(senderConnection, message);
```

### Поддерживаемые типы

Broadcast, как и `send()`, работает с:

* `byte[]`
* `ByteBuffer`
* `String`
* `BinaryStreamWriter`
* `NetPacket`

Пример:

``` java
server.broadcast(new ChatMessagePacket("Message"));
```

Метод возвращает количество соединений, которым **не удалось отправить сообщение**.

---

## Получение списка соединений

Можно получить все активные соединения сервера:

``` java
Collection<TCPConnection> connections = server.getConnections();
```

---

## Проверка состояния соединения

``` java
connection.isConnected();
connection.isClosed();
```

Получение сетевой информации:

``` java
connection.getAddress();
connection.getPort();

connection.getLocalAddress();
connection.getLocalPort();

connection.getSocket();
```

---

*[Главная страница](index.md)*

*Следующая - [Кодеки](codecs.md)*
