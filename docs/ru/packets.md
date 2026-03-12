# Пакеты

---

## Что такое пакет

TCP передаёт **просто поток байтов**.

Для большинства приложений удобнее работать не с сырыми байтами,
а со структурированными сообщениями.

Для этого в библиотеке существует концепция **пакетов**.

Пакет - это объект, который:

* сериализуется в поток байтов
* восстанавливается из потока
* содержит структурированные данные.

Пакеты являются **дополнительным слоем поверх TCP**.

---

## Базовый класс пакета

Все пакеты наследуются от
[NetPacket](/src/main/java/generaloss/networkforge/packet/NetPacket.java).

Минимальный пакет выглядит так:

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

Каждый пакет определяет два метода:

* `write()` - для сериализации данных
* `read()` - для десериализации.

---

## Формат пакета

Каждый пакет в сети имеет следующий формат:

```
[ packetID ][ packet data... ]
```

* `packetID` - это `short`
* затем записываются данные пакета

Метод `toByteArray()` автоматически записывает ID и вызывает `write()`.

---

## ID пакетов

Каждый пакет имеет уникальный `packetID`.

Его можно задать явно:

``` java
@PacketID(5)
public class LoginPacket extends NetPacket { ... }
```

Если аннотация отсутствует, ID вычисляется автоматически
из имени класса.

Это удобно для:

* быстрых прототипов
* внутренних протоколов

Для стабильных протоколов рекомендуется задавать ID вручную.

---

# Чтение пакетов

Чтением пакетов занимается класс 
[PacketReader](/src/main/java/generaloss/networkforge/packet/PacketReader.java).

Он выполняет:

1. чтение `packetID`
2. создание объекта пакета
3. десериализацию данных

---

## Регистрация пакетов

Перед чтением пакеты необходимо зарегистрировать.

``` java
PacketReader reader = new PacketReader();

reader.register(ChatMessagePacket.class);
reader.register(LoginPacket.class);
```

Регистрация связывает:

```
packetID → factory
```

Factory используется для создания экземпляра пакета.
Его можно задавать вручную:

``` java
reader.register(ChatMessagePacket.class, ChatMessagePacket:new);
```

---

## Автоматическая регистрация

Можно автоматически зарегистрировать пакеты из package:

``` java
reader.registerAllFromPackageRecursive(
    Resource.classpath("myapp/network/packet")
);
```

Будут найдены все классы:

* наследующие `NetPacket`
* не являющиеся `abstract`.

---

## Чтение пакета

После регистрации пакеты можно читать:

``` java
NetPacket packet = reader.readOrNull(data);
```

Или подробно с исключениями:

``` java
try {
    NetPacket packet = reader.read(data);
} catch (IOException e) {
    ...
}
```

Или безопасно с помощью `Optional`:

``` java
reader.tryRead(data).ifPresent(packet -> {
    ...
});
```

---

# Обработка пакетов

После чтения пакет необходимо передать обработчику.

Для этого используется 
[PacketDispatcher](/src/main/java/generaloss/networkforge/packet/PacketDispatcher.java).

Dispatcher хранит таблицу:

```
packetClass → handler
```

---

## Регистрация обработчиков

``` java
PacketDispatcher dispatcher = new PacketDispatcher();

dispatcher.register(ChatMessagePacket.class,
    (connection, packet) -> {
        System.out.println(packet.getMessage());
    }
);
```

Handler получает соединение и пакет.

---

## Обработка пакета

После чтения пакет можно передать dispatcher:

``` java
NetPacket packet = reader.readOrNull(data);
if(packet != null)
    dispatcher.dispatch(connection, packet);
```

Dispatcher автоматически находит обработчик нужного типа и вызывает его.

---

## Асинхронная обработка с Executor

Событие `receive` вызывается из selector-потока, который нельзя нагружать.

Если обработка пакетов может быть дорогой, можно передавать обработку пакетов **в отдельный поток**.

``` java
dispatcher.async(Executors.newSingleThreadExecutor());
```

Теперь все вызовы `dispatch()` **публикуются в Executor**, а основной I/O поток не блокируется.

---

## Батчинг пакетов

Когда сервер начинает получать много маленьких пакетов, возникает проблема: 
каждый пакет вызывает отдельную обработку.

Прочитанные за раз пакеты можно собирать **в одну группу** и обрабатывать вместе:

``` java
List<NetPacket> packetsBatch = new ArrayList<>();

dispatcher.async(Executors.newFixedThreadPool(2));

// собираем пакеты
server.registerOnReceive((sender, data) -> {
    reader.tryRead(data)
        .ifPresent(packetsBatch::add);
});

// отправляем на обработку
server.registerOnReadComplete(connection -> {
    if(packetsBatch.isEmpty())
        return;

    dispatcher.dispatch(connection, packetsBatch);
    packetsBatch.clear();
});
```

Чтение произойдет из одного `connection`, а `onReadComplete` послужит сигналом завершения чтения.

Внутри `PacketDispatcher` каждый пакет из батча будет передан своему обработчику в правильном порядке.

Вся обработка Executor-ом пройдет за одно выполнение.

---

# Использование с TCPServer

Пример обработки пакетов на сервере:

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

# Отправка пакетов

Пакет можно отправить напрямую:

``` java
client.send(new ChatMessagePacket("Hello"));
```

Пакет автоматически сериализуется в формат:

```
packetID + packetData
```

---

# Когда использовать пакеты

Пакеты полезны если:

* используется собственный сетевой протокол
* сообщения имеют структуру
* требуется типобезопасная обработка

Если приложение работает с сырыми `byte[]`,
использование пакетов не требуется.

---

# Итог

Система пакетов состоит из трёх компонентов:

| Компонент          | Задача                    |
|--------------------|---------------------------|
| `NetPacket`        | Описание структуры пакета |
| `PacketReader`     | Десериализация            |
| `PacketDispatcher` | Маршрутизация пакетов     |

Это позволяет построить типобезопасный протокол поверх TCP.

---

*[Главная страница](index.md)*