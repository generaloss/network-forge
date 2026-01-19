## Что такое пакеты?

Вместо того чтобы работать с «сырыми байтами» при сетевом обмене, удобнее мыслить **пакетами**.
Пакет - это класс, который:
	
- имеет уникальный `ID` (можно задать вручную или он вычисляется автоматически по имени класса);
	
- умеет **записать себя** в поток (`write`);
	
- умеет **прочитать себя** из потока (`read`);
	
- умеет **обработать себя** (`handle`), когда дойдет до обработчика.


Таким образом, логика сетевого взаимодействия отделяется от низкоуровневого байтового протокола.

---
## Основные компоненты

### `NetPacket<H>`

Базовый абстрактный класс пакета.
	
- `write(BinaryOutputStream stream)`  -  сериализация в поток.
	
- `read(BinaryInputStream stream)`  -  десериализация из потока.
	
- `handle(H handler)`  -  логика обработки.
	
- `getPacketID()`  -  возвращает уникальный ID пакета.
	
- `toByteArray()`  -  превращает пакет в массив байт для отправки.

Пример минимального пакета:

``` java
@PacketID(1) // можно задать вручную, иначе посчитается по имени класса
public class ChatMessagePacket extends NetPacket<ChatHandler> {
	
    private String message;
	
    public ChatMessagePacket() {} // нужен конструктор без аргументов
	
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

Аннотация для задания ID пакета.
	
- Если не указана, ID вычисляется по хэшу имени класса.
    
- Лучше указывать явно для стабильности протокола.

---
### `NetPacketFactory<H>`

Фабрика для создания новых экземпляров пакета.

Обычно не нужна напрямую - `PacketDispatcher` сам генерирует через рефлексию, если зарегистрировать класс пакета.

---
### `PacketDispatcher`

Главный менеджер пакетов. Делает три вещи:
	
1. **Регистрирует пакеты** (`register(...)`).
    
2. **Читает пакет из массива байт** (`dispatch(...)`).

3. *С включенным `directHandling` пакет сразу же* **обрабатываeтся** *в `dispatch(...)`, иначе - добавляется в очередь для обработки.*

4. **Обрабатывает очередь пакетов (`handlePackets()`)**.

---
## Как это работает шаг за шагом
	
1. Сетевая библиотека принимает массив байт из TCP соединения.
    
2. Первый `short` в массиве - это `packetID`.
    
3. `PacketDispatcher` находит фабрику по ID.
    
4. Создается новый экземпляр пакета, вызывается `read()`.
    
5. Пакет попадает в очередь.
    
6. Когда вызывается `handlePackets()`, выполняется `packet.handle(handler)`.

---
## Пример использования

### 1. Обработчик

Интерфейс/класс, который будет принимать пакеты:

``` java
public interface ChatHandler {
    void onChatMessage(String message);
}
```

### 2. Пакет

См. `ChatMessagePacket` выше.

### 3. Регистрация пакета

``` java
PacketDispatcher dispatcher = new PacketDispatcher();

// регистрация фабрики вручную
dispatcher.register(ChatMessagePacket.class, ChatMessagePacket::new);
// регистрация с автогенерацией фабрики
dispatcher.register(ChatMessagePacket.class);
// регистрация группы пакетов с автогенерацией фабрики
dispatcher.registerAll(FirstPacket.class, SecondPacket.class, ...);
```

### 4. Отправка

``` java
ChatMessagePacket packet = new ChatMessagePacket("Hello, world!");
// отправляем через TCP-соединение
connection.send(packet);
```

### 5. Прием и обработка

``` java
// экземпляр обработчика
ChatHandler chatHandler = new ChatHandler() {
    @Override
    public void onChatMessage(String message) {
        System.out.println("Got: " + message);
    }
};

// допустим, пришли байты из TCP
dispatcher.dispatch(receivedBytes, chatHandler);
// затем обрабатываем очередь (если выключен directHandling)
dispatcher.handlePackets();
```

Вывод:
```
Got: Hello, world!
```

> [!TIP]
> Если вам нужно выбирать обработчик динамически в зависимости от содержимого пакета, передайте собственную функцию-селектор в метод dispatch:
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

## Обработка нескольких пакетов

Можно зарегистрировать несколько пакетов разом:

``` java
dispatcher.registerAll(
    ChatMessagePacket.class,
    PlayerJoinPacket.class,
    PlayerLeavePacket.class
);
```

## Советы по использованию

- **Стабильность протокола:** лучше всегда задавать `@PacketID` вручную, иначе при переименовании класса ID изменится.
    
- **Производительность:** пакеты кэшируются в очереди и обрабатываются асинхронно. Можно вызывать `dispatcher.handlePackets()` в игровом/главном цикле.
    
- **Потокобезопасность:** используется `ConcurrentHashMap` и `ConcurrentLinkedQueue`, можно обрабатывать пакеты из разных потоков приема.
    
---
## Итог

- `NetPacket` - единица данных + логика.
    
- `PacketDispatcher` - регистрация, чтение и обработка.
    
- Код получается читаемым и расширяемым.
