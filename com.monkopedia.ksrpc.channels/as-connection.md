//[ksrpc](../index.md)/[com.monkopedia.ksrpc.channels](index.md)/[asConnection](as-connection.md)



# asConnection  
[common]  
Content  
fun HttpClient.[asConnection](as-connection.md)(baseUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), env: [KsrpcEnvironment](../com.monkopedia.ksrpc/-ksrpc-environment/index.md)): [ChannelClient](-channel-client/index.md)  
More info  


Turn an HttpClient into a [ChannelClient](-channel-client/index.md) for a specified baseUrl.



This is functionally equivalent to baseUrl.toKsrpcUri().connect(env).

  


[common]  
Content  
suspend fun [Pair](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-pair/index.html)<ByteReadChannel, ByteWriteChannel>.[asConnection](as-connection.md)(env: [KsrpcEnvironment](../com.monkopedia.ksrpc/-ksrpc-environment/index.md)): [Connection](-connection/index.md)  
More info  


Create a [Connection](-connection/index.md) for the given input/output channel.

  


[jvm]  
Content  
suspend fun [Pair](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-pair/index.html)<[InputStream](https://docs.oracle.com/javase/8/docs/api/java/io/InputStream.html), [OutputStream](https://docs.oracle.com/javase/8/docs/api/java/io/OutputStream.html)>.[asConnection](index.md#%5Bcom.monkopedia.ksrpc.channels%2F%2FasConnection%2Fkotlin.Pair%5Bjava.io.InputStream%2Cjava.io.OutputStream%5D%23com.monkopedia.ksrpc.KsrpcEnvironment%2FPointingToDeclaration%2F%5D%2FFunctions%2F987004091)(env: [KsrpcEnvironment](../com.monkopedia.ksrpc/-ksrpc-environment/index.md)): [Connection](-connection/index.md)  


[jvm]  
Content  
suspend fun [ProcessBuilder](https://docs.oracle.com/javase/8/docs/api/java/lang/ProcessBuilder.html).[asConnection](index.md#%5Bcom.monkopedia.ksrpc.channels%2F%2FasConnection%2Fjava.lang.ProcessBuilder%23com.monkopedia.ksrpc.KsrpcEnvironment%2FPointingToDeclaration%2F%5D%2FFunctions%2F987004091)(env: [KsrpcEnvironment](../com.monkopedia.ksrpc/-ksrpc-environment/index.md)): [Connection](-connection/index.md)  
More info  


Create a [Connection](-connection/index.md) that starts the process and uses the [Process.getInputStream](https://docs.oracle.com/javase/8/docs/api/java/lang/Process.html#getInputStream--) and [Process.getOutputStream](https://docs.oracle.com/javase/8/docs/api/java/lang/Process.html#getOutputStream--) as the streams for communication

  



