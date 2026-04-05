# Полезные функции

---

## Запуск `TCPServer`

### Несколько портов одновременно

Сервер умеет слушать сразу несколько адресов:

``` java
server.run(InetSocketAddress... addresses);
server.run(String hostname, int... ports);
server.run(int... ports);
```

Примеры:

``` java
server.run(8080, 8081, 8082);
server.run("localhost", 9000, 9001);
```

---

### Автоматический выбор порта

Если указать `0`, операционная система сама выберет свободный порт:

``` java
server.run(0);
// или
server.run();
```

Получить выбранный порт:

```java
final int port = server.getPorts()[0];
```

Несколько случайных портов:

``` java
server.run(0, 0);

final int[] ports = server.getPorts();
final int port1 = ports[0];
final int port2 = ports[1];
```

---

## Подключение клиента

### Блокирующее подключение

```j ava
client.connect(SocketAddress address, long timeoutMs);
client.connect(SocketAddress address);

client.connect(String hostname, int port, long timeoutMs);
client.connect(String hostname, int port);
```

---

### Асинхронное подключение

``` java
CompletableFuture<TCPConnection> future;

future = client.connectAsync(SocketAddress address, long timeoutMs);
future = client.connectAsync(SocketAddress address);

future = client.connectAsync(String hostname, int port, long timeoutMs);
future = client.connectAsync(String hostname, int port);
```

---

### Подключение к самому быстрому серверу

Если есть несколько адресов и нужно выбрать тот, кто ответит первым:

``` java
CompletableFuture<TCPConnection> future;

future = client.connectFastest(SocketAddress[] addresses, long timeoutMs);
future = client.connectFastest(SocketAddress[] addresses);
```

---

## Опции соединения

``` java
TCPConnectionOptionsHolder options = new TCPConnectionOptionsHolder();

// Socket options
options.setLinger(1);
options.setTcpNoDelay(true);

// Library options
options.setMaxReadFrameSize(8 * 1024 * 1024); // 8 MB
options.setFrameBufferSizeUpperBound(...);
```

**Важно про `setFrameBufferSizeUpperBound()`**

* Используется только в `Framed` кодеке (`FramedTCPConnectionCodec`).
* Ограничивает рост буфера чтения:
* Если задано, буфер может сжиматься после получения больших пакетов данных;
* Если значение = `0`, буфер никогда не уменьшается.

### Применение опций

#### Для сервера

``` java
TCPServer server = new TCPServer();
server.setInitialOptions(options);
server.run(5555);
```

Все новые соединения будут создаваться с этими настройками.

#### Для клиента

``` java
TCPClient client = new TCPClient();
client.setInitialOptions(options);
client.connect("localhost", 5555);
```

Опции применяются к следующему подключению.

---

## Отладка и диагностика

Минимальный способ включить логирование ошибок:

``` java
TCPClient client = new TCPClient();
client.registerOnError(ErrorListener::printError);

TCPServer server = new TCPServer();
server.registerOnError(ErrorListener::printError);
```

Это позволит увидеть ошибки в пайплайне и обработчиках.

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
connection.awaitWriteDrain(timeoutMs);
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
