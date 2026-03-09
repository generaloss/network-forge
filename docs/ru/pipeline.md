# Pipeline

## Что такое пайплайн

Пайплайн - это механизм расширения.

Новые возможности добавляются не через изменение ядра библиотеки,
а через подключение новых обработчиков.
Такие как:

* метрики
* шифрование
* авторизация
* и прочее..

Ядро остаётся неизменным, а поведение формируется конфигурацией цепочки.

[EventPipeline](/src/main/java/generaloss/networkforge/tcp/pipeline/EventPipeline.java)
это цепочка обработчиков
([EventHandler](/src/main/java/generaloss/networkforge/tcp/pipeline/EventHandler.java)),
через которую проходит каждое событие:

* подключение
* отключение
* входящие данные
* исходящие данные
* ошибки.

Событие не рассылается, а проходит шаг за шагом через цепочку, где каждый обработчик может:

* остановить дальнейшую обработку
* инициировать отправку
* изменить данные
* закрыть соединение.

---

## Направления движения событий

В системе существуют два направления распространения:

### Inbound (входящие события)

События идут **с начала цепочки к концу**:

```
Handler_0 → Handler_1 → Handler_2 → Target
```

Сюда относятся события:

* connect
* receive
* disconnect
* error

Каждый обработчик решает, продолжать ли цепочку.
Если метод возвращает `false`, распространение останавливается.

---

### Outbound (исходящие события)

Отправка данных (send) идёт **в обратном направлении**:

```
Handler_2 → Handler_1 → Handler_0 → Socket
```

Это позволяет:

* шифровать данные
* сжимать
* логировать отправку

Каждый обработчик может изменить данные перед передачей дальше.

---

## Контекст выполнения

Каждое событие обрабатывается в рамках контекста
[EventInvocationContext](/src/main/java/generaloss/networkforge/tcp/pipeline/EventInvocationContext.java).

Контекст хранит:

* текущую позицию в цепочке
* ссылку на соединение
* зафиксированный набор обработчиков

## Snapshot обработчиков

На время обработки события используется **снимок обработчиков**.

По этому изменения пайплайна (добавление/удаление обработчиков) не влияют на уже запущенные события.

При добавлении или удалении обработчиков создаётся новый массив.
Текущие события продолжают работать со старой версией.

Это даёт предсказуемую обработку:

* без блокировок и гонок
* стабильный порядок вызовов

Цена - копирование массива при модификации.
Так как изменения пайплайна происходят редко,
эта стоимость оправдана.

---

## Простейший обработчик

Чтобы создать обработчик, достаточно унаследоваться от `EventHandler`  
и переопределить нужные методы.

```java
public class LoggingHandler extends EventHandler {
    @Override
    public boolean handleConnect(EventInvocationContext context) {
        System.out.println("Connected: " + context.getConnection());
        return true;
    }

    @Override
    public boolean handleDisconnect(EventInvocationContext context, CloseReason reason, Exception e) {
        System.out.println("Disconnected: " + reason);
        return true;
    }

    @Override
    public boolean handleReceive(EventInvocationContext context, byte[] data) {
        System.out.println("Received " + data.length + " bytes");
        return true;
    }

    @Override
    public boolean handleError(EventInvocationContext context, ErrorSource source, Throwable throwable) {
        System.out.println("Error (" + source + "): " + throwable.getMessage());
        return true;
    }

    @Override
    public byte[] handleSend(EventInvocationContext context, byte[] data) {
        System.out.println("Sending " + data.length + " bytes");
        return true;
    }
}
```

Если метод возвращает `true`, событие передаётся дальше.  
Если вернуть `false`, обработка останавливается.

## Инициирование событий

Обработчик может сам инициировать событие (в рамках того же snapshot'a обработчиков), которое  

```java
@Override
public boolean handleReceive(EventInvocationContext context, byte[] data) {
    context.send("OK".getBytes());
    return true;
}
```
Откладывать событие до нужного момента (connect):
``` java
@Override
public boolean handleConnect(EventInvocationContext context) {
    return false;
}

@Override
public boolean handleReceive(EventInvocationContext context, byte[] data) {
    TCPConnection connection = context.getConnection();
    if(...){ // например, получение правильных данных для данного подключения
        context.connect(connection);
        return false;
    }
    return true;
}
```

## Модификация данных



*[Главная страница](index.md)*

*Следующая - [.]()*