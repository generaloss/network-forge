# Пайплайн

---

## Что такое пайплайн

Пайплайн - это механизм расширения.

Новые возможности добавляются не через изменение ядра библиотеки,
а через подключение новых обработчиков, такие как:

* метрики
* шифрование
* авторизация
* и прочее.

**Ядро остаётся неизменным. Поведение формируется конфигурацией цепочки обработчиков.**

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

* **Inbound** события идут сверху вниз по цепочке.
* **Outbound** события идут снизу вверх.

### Inbound (входящие события)

События идут **с начала цепочки к концу**:

```

Handler_0 → Handler_1 → Handler_2 → Target

```

Сюда относятся события:

* `connect`
* `receive`
* `disconnect`
* `error`

Каждый обработчик решает, продолжать ли цепочку.
Если метод возвращает `false`, распространение останавливается.

---

### Outbound (исходящие события)

Событие `send` идёт **в обратном направлении**:

```

Handler_2 → Handler_1 → Handler_0 → Socket

```

Это позволяет:

* шифровать данные
* сжимать
* логировать отправку

---

## Контекст выполнения

Каждое событие обрабатывается в рамках контекста
[EventInvocationContext](/src/main/java/generaloss/networkforge/tcp/pipeline/EventInvocationContext.java).

Контекст хранит:

* текущую позицию в цепочке
* ссылку на соединение
* зафиксированный набор обработчиков

---

## Snapshot обработчиков

На время обработки события используется **снимок обработчиков**.

По этому изменения пайплайна (добавление/удаление обработчиков) не влияют на уже запущенные события.

**Каждое событие использует тот snapshot обработчиков,
который существовал в момент его запуска.**

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
    public boolean handleSend(EventInvocationContext context, byte[] data) {
        System.out.println("Sending " + data.length + " bytes");
        return true;
    }
}
```

Если метод возвращает `true`, событие передаётся дальше.  
Если вернуть `false`, обработка останавливается.

Методы можно переопределять выборочно.
Необязательно реализовывать все события.

---

## Поток обработки

События `receive` вызываются из selector-потока.

Этот поток отвечает за:

* обработку сетевых событий
* чтение данных из сокетов
* запись данных.

Поэтому обработчики pipeline должны быть **максимально лёгкими.**

Не рекомендуется выполнять в них:

* блокирующие операции
* длительные вычисления.

---

## Инициирование событий

Обработчик может инициировать новые события, которое пойдет через следующие обработчики:

```java
@Override
public boolean handleReceive(EventInvocationContext context, byte[] data) {
    context.send(message_to_send);
    context.receive(message_to_receive);
    context.connect(connection);
    context.disconnect(connection, CloseReason.INTERNAL_ERROR, exception);
    context.error(ErrorSource.RECEIVE_HANDLER, throwable);
    return true;
}
```
Событие, инициированное из обработчика, **продолжает работу в том же snapshot'е обработчиков.**
Это гарантирует, что порядок обработчиков остаётся стабильным
на протяжении всего события.


### Это позволяет:

* Модифицировать данные событий - для этого нужно прервать старое событие и инициировать новое:

```java
public class SendPrefixHandler extends EventHandler {

    @Override
    public boolean handleSend(EventInvocationContext context, byte[] data) {
        byte[] prefix = "[Prefix] ".getBytes();
        byte[] modified = this.addPrefix(data, prefix);
        context.send(modified); // Инициируем событие с измененными данными
        return false; // Прерываем текущее
    }

    private byte[] addPrefix(byte[] input, byte[] prefix) {
        byte[] result = new byte[prefix.length + input.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(input, 0, result, prefix.length, input.length);
        return result;
    }

}
```

* Откладывать событие до нужного момента:

``` java
public class PrefixHandler extends EventHandler {

    private final List<TCPConnection> postponeList = Collections.synchronizedList(new ArrayList<>());

    @Override
    public boolean handleConnect(EventInvocationContext context) {
        postponeList.add(context.getConnection()); // Сохраняем соединения, чтобы позже инициировать `connect` для них
        return false; // Прерываем `connect` события на этом обработчике.
    }

    @Override
    public boolean handleReceive(EventInvocationContext context, byte[] data) {
        TCPConnection connection = context.getConnection();

        boolean isRightConnection = postponeList.contains(connection);
        boolean isRightData = ...; // Например, если получены определенные данные для данного подключения

        if(isRightConnection && isRightData) {
            postponeList.remove(connection);
            context.connect(connection); // Инициируем `connect` к следующим обработчикам
            return false; // Не передаем дальше данные, предназначенные для этого обработчика
        }
        return true;
    }

}
```

---

*[Главная страница](index.md)*

*Следующая - [Пакеты](packets.md)*