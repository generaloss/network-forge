
# Пример

Что делает следующий код:
1) клиент отсылает сообщение «*ping*» серверу;
2) сервер получает «*ping*» и отсылает «*pong*» клиенту;
3) клиент получает «*pong*» и закрывает соединение.

``` java
// создаем сервер
TCPServer server = new TCPServer()  
    .setOnConnect((connection) -> System.out.println("[Server] New connection"))  
    .setOnDisconnect((connection, reason, e) -> System.out.println("[Server] Lost connection: " + reason))  
    .setOnReceive((sender, bytes) -> {  
        // 2) получаем «ping» и сразу отправляем «pong»  
        System.out.println("[Server] Received '" + new String(bytes) + "'");  
        sender.send("pong");  
    });  
server.run(65000);  
  
// создаем клиент  
TCPClient client = new TCPClient()  
    .setOnConnect((connection) -> System.out.println("[Сlient] Connected"))  
    .setOnDisconnect((connection, reason, e) -> System.out.println("[Сlient] Disconnected: " + reason))  
    .setOnReceive((connection, bytes) -> {  
        // 3) принимаем «pong» и закрываем соединение  
        System.out.println("[Client] Received '" + new String(bytes) + "'");  
        connection.close();  
    });  
client.connect("localhost", 65000);  
  
// 1) отправляем сообщение «ping»  
client.send("ping");  
  
// ждем отключения клиента  
while(!client.isClosed())  
    Thread.onSpinWait();  
  
server.close();
```

---
# `TCPConnection`

Абстрактный класс [`TCPConnection`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/TCPConnection.java) инкапсулирует низкоуровневое соединение и предоставляет API для работы с ним:
``` java
SocketChannel channel()
Socket socket()
SelectionKey selectionKey()
TCPConnectionOptions options()
CipherPair ciphers()

<O> O attachment()
void attach(Object attachment) // ассоциирование с пользовательским объектом

String getName()
void setName(String name)
String toString() // возвращает то же, что и getName()

int getPort()
int getLocalPort()
InetAddress getAddress()
InetAddress getLocalAddress()

boolean isConnected()
boolean isClosed()
void close()

boolean send(byte[] byteArray)
boolean send(ByteBuffer buffer)
boolean send(String string)
boolean send(BinaryStreamWriter streamWriter)
boolean send(NetPacket<?> packet)

static void registerFactory(Class<?> connectionClass, TCPConnectionFactory factory)
static TCPConnectionFactory getFactory(Class<?> connectionClass)
static TCPConnectionFactory getFactory(TCPConnectionType connectionType)
```

## Реализации `TCPConnection`

| Класс                                                                                                                                               | TCPConnectionType | Описание                                                                                                                        |
| --------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| [`PacketTCPConnection`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/PacketTCPConnection.java) | **PACKET**        | Читает данные целиком; при отправке добавляется размер пакета.                                                                  |
| [`StreamTCPConnection`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/StreamTCPConnection.java) | **STREAM**        | Сообщение может быть прочитано по частям; данные идут напрямую.<br>Подходит для протоколов со своим фреймингом (например, HTTP) |

Также возможны пользовательские реализации этого класса.

По умолчанию и TCPServer, и TCPClient используют тип **PACKET**.

---
### [`TCPConnectionFactory`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/TCPConnectionFactory.java)

Интерфейс для создания соединений:
``` java
TCPConnection create(  
    SocketChannel channel,  
    SelectionKey selectionKey,  
    TCPCloseable onClose  
);
```

---
# Listener-Интерфейсы
Используются для обработки событий соединения.

## [`TCPReceiver`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPReceiver.java)
``` java
void receive(TCPConnection connection, byte[] byteArray)
```
Получает данные в виде массива байтов.

---
## [`TCPReceiverStream`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPReceiverStream.java)
``` java
void receive(TCPConnection connection, BinaryInputStream inStream)
```
Предоставляет поток [BinaryInputStream](https://github.com/generaloss/resource-flow/tree/master/src/main/java/generaloss/resourceflow/stream/BinaryInputStream.java) для чтения (из библиотеки [Resource-Flow](https://github.com/generaloss/resource-flow.git)).

---
## [`TCPCloseable`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPCloseable.java)
``` java
void close(TCPConnection connection, TCPCloseReason reason, Exception e)
```
Вызывается при закрытии соединения.

---
## [`TCPErrorHandler`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPErrorHandler.java)
``` java
void error(TCPConnection connection, TCPErrorSource source, Throwable throwable)
```
Обрабатывает ошибки в пользовательских коллбеках.

---
## [`TCPCloseReason`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPCloseReason.java)
Перечисление с причинами закрытия соединения:

| Значение                   | Сообщение                           | Исключение |
| -------------------------- | ----------------------------------- | :--------: |
| CLOSE_CONNECTION           | Connection closed                   |     -      |
| CLOSE_CLIENT               | Client closed                       |     -      |
| CLOSE_SERVER               | Server closed                       |     -      |
| CLOSE_BY_OTHER_SIDE        | Connection closed by the other side |     -      |
| PACKET_SIZE_LIMIT_EXCEEDED | Packet size limit exceeded          |     -      |
| INVALID_PACKET_SIZE        | Invalid packet size                 |     -      |
| INTERNAL_ERROR             | Internal error occurred             |     +      |

---
## [`TCPErrorSource`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/listener/TCPErrorSource.java)
Перечисление с источниками пользовательских ошибок:

| Значение             |
| -------------------- |
| CONNECT_CALLBACK<br> |
| DISCONNECT_CALLBACK  |
| RECEIVE_CALLBACK     |
| ERROR_CALLBACK       |

---
# `TCPClient`

Класс [`TCPClient`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/TCPClient.java) - для инициации подключения:
``` java
TCPClient()
TCPClient(TCPConnectionOptionsHolder initialOptions)

TCPConnection connection()
TCPConnectionOptions options()
CipherPair ciphers()

// установленный тип применится при следующем подключении
TCPClient setConnectionType(Class<?> tcpConnectionClass)
TCPClient setConnectionType(TCPConnectionType connectionType)

TCPConnectionOptionsHolder getInitialOptions()
TCPClient setInitialOptions(TCPConnectionOptionsHolder initialOptions)

TCPClient setOnConnect(Consumer<TCPConnection> onConnect)
TCPClient setOnDisconnect(TCPCloseable onClose)
TCPClient setOnReceive(TCPReceiver onReceive)
TCPClient setOnReceiveStream(TCPReceiverStream onReceive)
TCPClient setOnError(TCPErrorHandler onError)

TCPClient connect(SocketAddress socketAddress, long timeoutMillis) throws IOException, TimeoutException
TCPClient connect(SocketAddress socketAddress) throws IOException, TimeoutException
TCPClient connect(String hostname, int port, long timeoutMillis) throws IOException, TimeoutException
TCPClient connect(String hostname, int port) throws IOException, TimeoutException

boolean isConnected()
boolean isClosed()
TCPClient close()

TCPClient setEncryptCipher(Cipher encryptCipher)
TCPClient setDecryptCipher(Cipher decryptCipher)
TCPClient setCiphers(Cipher encryptCipher, Cipher decryptCipher)

boolean send(byte[] byteArray)
boolean send(ByteBuffer buffer)
boolean send(String string)
boolean send(BinaryStreamWriter streamWriter)
boolean send(NetPacket<?> packet)
```

---
# `TCPServer`

Класс [`TCPServer`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/TCPServer.java) - для ожидания входящих подключений:
``` java
TCPServer()
TCPServer(TCPConnectionOptionsHolder initialOptions)

TCPServer setConnectionType(Class<?> tcpConnectionClass)
TCPServer setConnectionType(TCPConnectionType connectionType)

// установленный тип применится к следующим подключениям
TCPServer setConnectionType(Class<?> tcpConnectionClass)
TCPServer setConnectionType(TCPConnectionType connectionType)

TCPServer setOnConnect(Consumer<TCPConnection> onConnect)
TCPServer setOnDisconnect(TCPCloseable onClose)
TCPServer setOnReceive(TCPReceiver onReceive)
TCPServer setOnReceiveStream(TCPReceiverStream onReceive)
TCPServer setOnError(TCPErrorHandler onError)

TCPServer run(InetAddress address, int... ports) throws IOException
TCPServer run(String hostname, int... ports) throws IOException
TCPServer run(int... ports) throws IOException

Collection<TCPConnection> getConnections()

boolean isRunning()
boolean isClosed()
TCPServer close()

// возвращает количество неудачных отправок
int broadcast(byte[] byteArray)
int broadcast(TCPConnection except, byte[] byteArray)

int broadcast(ByteBuffer buffer)
int broadcast(TCPConnection except, ByteBuffer buffer)

int broadcast(String string)
int broadcast(TCPConnection except, String string)

int broadcast(BinaryStreamWriter streamWriter)
int broadcast(TCPConnection except, BinaryStreamWriter streamWriter)

int broadcast(NetPacket<?> packet)
int broadcast(TCPConnection except, NetPacket<?> packet)
```

---
# [`CipherPair`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/CipherPair.java)

Обертка над двумя `Cipher` для шифрования/дешифрования:
``` java
// сеттеры и геттеры для Cipher'ов
Cipher getEncryptCipher()
void setEncryptCipher(Cipher encryptCipher)

Cipher getDecryptCipher()
void setDecryptCipher(Cipher decryptCipher)

void setCiphers(Cipher encryptCipher, Cipher decryptCipher)

// возвращает те же данные если соотв. Cipher является null 
byte[] encrypt(byte[] byteArray)
byte[] decrypt(byte[] byteArray)

int getEncryptedSize(int inputSize)
int getDecryptedSize(int inputSize)
```

---
# Опции соединений

## [`TCPConnectionOptionsHolder`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/options/TCPConnectionOptionsHolder.java)

Хранит конфигурацию TCP-соединений, применяется при создании нового соединения. 
Поля, с значениями `null` игнорируются.
``` java
TCPConnectionOptionsHolder(Socket socket)

// максимальный размер пакета данных при чтении
int getMaxPacketSizeRead()
TCPConnectionOptionsHolder setMaxPacketSizeRead(int maxPacketSizeRead)

// максимальный размер пакета данных при отправлении
int getMaxPacketSizeWrite()
TCPConnectionOptionsHolder setMaxPacketSizeWrite(int maxPacketSizeWrite)

// устанавливает максимальный размер пакета для read и для write одновременно
TCPConnectionOptionsHolder setMaxPacketSize(int maxPacketSize)

// закрыватие соединения при получении слишком большого пакета, иначе - отбросить лишние данные
boolean isCloseOnPacketLimit()
TCPConnectionOptionsHolder setCloseOnPacketLimit(boolean closeOnPacketLimit)

// скопировать опции, указанные выше, в TCPConnectionOptions (метод используется автоматически)
void copyTo(TCPConnectionOptions options)

String toString()

// другие опции из SocketOptionsHolder...
```
Наследует [`SocketOptionsHolder`](#SocketOptionsHolder).

---
## [`SocketOptionsHolder`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/options/SocketOptionsHolder.java)

Содержит настройки уровня `Socket`:
``` java
SocketOptionsHolder(Socket socket)

Socket getSocket()

Boolean isTcpNoDelay()
SocketOptionsHolder setTcpNoDelay(Boolean tcpNoDelay) // отключить/включить алгоритм Нагла

Boolean isTcpQuickAck()
SocketOptionsHolder setTcpQuickAck(Boolean tcpQuickAck)

Integer getTcpKeepIdle()
SocketOptionsHolder setTcpKeepIdle(Integer tcpKeepIdle)

Integer getTcpKeepInterval()
SocketOptionsHolder setTcpKeepInterval(Integer tcpKeepInterval)

Integer getTcpKeepCount()
SocketOptionsHolder setTcpKeepCount(Integer tcpKeepCount)

Integer getTrafficClass()
SocketOptionsHolder setTrafficClass(Integer trafficClass) // малая_стоимость (0x02) | надежность (0x04) | пропускная_способность (0x08) | низкая_задержка (0x10)

Boolean isOOBInline()
SocketOptionsHolder setOOBInline(Boolean oobInline) // включение/отключение поточного приема срочных данных TCP

Boolean isKeepAlive() 
SocketOptionsHolder setKeepAlive(Boolean keepAlive) // включенине/отключенине функции поддержки соединения

Boolean isReuseAddress()
SocketOptionsHolder setReuseAddress(Boolean reuseAddress) // включить повторное использование адреса для сокета

Boolean isReusePort()
SocketOptionsHolder setReusePort(Boolean reusePort)

Integer getReceiveBufferSize()
SocketOptionsHolder setReceiveBufferSize(Integer receiveBufferSize) // задать размер буфера, фактически используемого платформой при приеме данных на этот сокет

Integer getSendBufferSize()
SocketOptionsHolder setSendBufferSize(Integer sendBufferSize) // задать ориентир размера базовых буферов для исходящего сетевого ввода/вывода

Integer getLinger()
SocketOptionsHolder setSoLinger(Integer linger) // задержать закрытие сокета, если присутствуют данные 

// применить non-null опции к channel (методы используются автоматически) 
void applyPreConnect(SocketChannel channel)
void applyPostConnect(SocketChannel channel)
void applyServerPreBind(ServerSocketChannel channel)

String toString()
```

---
## [`TCPConnectionOptions`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/options/TCPConnectionOptions.java)

Рабочие (примененные) настройки соединения.
``` java
TCPConnectionOptions(Socket socket)

int getMaxPacketSizeRead()
TCPConnectionOptions setMaxPacketSizeRead(int maxPacketSizeRead)

int getMaxPacketSizeWrite()
TCPConnectionOptions setMaxPacketSizeWrite(int maxPacketSizeWrite)

TCPConnectionOptions setMaxPacketSize(int maxPacketSize)

boolean isCloseOnPacketLimit()
TCPConnectionOptions setCloseOnPacketLimit(boolean closeOnPacketLimit)

String toString()

// другие методы из SocketOptions...
```
Наследует [`SocketOptions`](#SocketOptions).

---
## [`SocketOptions`](https://github.com/generaloss/network-forge/tree/master/src/main/java/generaloss/networkforge/tcp/options/SocketOptions.java)

Рабочие настройки для `Socket`:
``` java
SocketOptions(Socket socket)

Socket getSocket()

Boolean isTcpNoDelay()
SocketOptions setTcpNoDelay(Boolean tcpNoDelay)

Boolean isTcpQuickAck()
SocketOptions setTcpQuickAck(Boolean tcpQuickAck)

Integer getTcpKeepIdle()
SocketOptions setTcpKeepIdle(Integer tcpKeepIdle)

Integer getTcpKeepInterval()
SocketOptions setTcpKeepInterval(Integer tcpKeepInterval)

Integer getTcpKeepCount()
SocketOptions setTcpKeepCount(Integer tcpKeepCount)

Integer getTrafficClass()
SocketOptions setTrafficClass(Integer trafficClass)

Boolean isOOBInline()
SocketOptions setOOBInline(Boolean oobInline)

Boolean isKeepAlive() 
SocketOptions setKeepAlive(Boolean keepAlive)

Boolean isReuseAddress()
SocketOptions setReuseAddress(Boolean reuseAddress)

Boolean isReusePort()
SocketOptions setReusePort(Boolean reusePort)

Integer getReceiveBufferSize()
SocketOptions setReceiveBufferSize(Integer receiveBufferSize)

Integer getSendBufferSize()
SocketOptions setSendBufferSize(Integer sendBufferSize)

Integer getLinger()
SocketOptions setSoLinger(boolean on, int linger)

String toString()
```

---
