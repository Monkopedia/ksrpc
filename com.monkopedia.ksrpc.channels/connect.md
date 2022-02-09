//[ksrpc](../index.md)/[com.monkopedia.ksrpc.channels](index.md)/[connect](connect.md)



# connect  
[common]  
Content  
inline suspend fun <[T](connect.md) : [RpcService](../com.monkopedia.ksrpc/-rpc-service/index.md), [R](connect.md) : [RpcService](../com.monkopedia.ksrpc/-rpc-service/index.md)> [Connection](-connection/index.md).[connect](connect.md)(crossinline host: ([R](connect.md)) -> [T](connect.md))  
More info  


Connects both default channels for a connection (incoming and outgoing).



Provides the host lambda with a stub connected to the default outgoing channel for the connection, and then connects the returned service as the hosted channel for the connection.



This is equivalent to calling [registerDefault](register-default.md) for [T](connect.md) instance and using defaultChannel and [toStub](../com.monkopedia.ksrpc/to-stub.md) to create [R](connect.md).

  


[common]  
Content  
@[JvmName](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.jvm/-jvm-name/index.html)(name = "connectSerialized")  
  
suspend fun [Connection](-connection/index.md).[connect](connect.md)(host: ([SerializedService](-serialized-service/index.md)) -> [SerializedService](-serialized-service/index.md))  
More info  


Raw version of [connect](connect.md), performing the same functionality with [SerializedService](-serialized-service/index.md) directly.

  



