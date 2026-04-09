# Visitor-пакеты

---

## Что такое visitor-пакеты

`VisitorNetPacket` это расширение базового класса NetPacket: 

```java
public abstract class VisitorNetPacket<H> extends NetPacket {

    public abstract void handle(H handler);

    public Runnable createHandleTask(H handler) {
        return () -> this.handle(handler);
    }

}
```

Обычный `NetPacket` описывает только структуру данных.<br>
Вызов обработчика всегда находится вне пакета.

`VisitorNetPacket` добавляет метод `handle`, <br>
в котором пакет сам вызывает нужный метод обработчика.

## Пример

```java
public class MyPacket extends VisitorNetPacket<MyProtocol> {

    @Override
    public void handle(MyProtocol handler) {
        handler.handleMyPacket(this);
    }

}
```

Теперь пакет сам определяет, какой метод должен быть вызван.

---

## Обработчик

Тип обработчика задаётся через дженерик `<H>`.

Это может быть интерфейс:

```java
public interface MyProtocol {

    void handleMyPacket(MyPacket packet);
    void handleAnotherPacket(AnotherPacket packet);

}
```

или обычный класс:

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

## Регистрация в PacketDispatcher

`PacketDispatcher` поддерживает отдельный метод для visitor-пакетов:

``` java
public final <H, P extends VisitorNetPacket<H>> PacketDispatcher register(
    Class<P> packetClass,
    Function<TCPConnection, H> handlerFunction
)
```

Он задаёт способ получения обработчика из соединения.

Пример:

``` java
dispatcher.register(
    MyPacket.class,
    connection -> connection.attachment()
);
```

После этого dispatcher при обработке пакета выполнит:

``` java
packet.handle(handlerFunction.apply(connection));
```

---

## Использование без PacketDispatcher

Visitor-пакеты можно использовать и напрямую:

``` java
packet.handle(handler);
```

или асинхронно:

``` java
executor.execute(packet.createHandleTask(handler));
```

---

*[Главная страница](index.md)*