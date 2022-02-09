//[ksrpc](../index.md)/[com.monkopedia.ksrpc.channels](index.md)/[asWebsocketConnection](as-websocket-connection.md)



# asWebsocketConnection  
[common]  
Content  
suspend fun HttpClient.[asWebsocketConnection](as-websocket-connection.md)(baseUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), env: [KsrpcEnvironment](../com.monkopedia.ksrpc/-ksrpc-environment/index.md)): [Connection](-connection/index.md)  
More info  


Turn an HttpClient into a websocket based [Connection](-connection/index.md) for a specified baseUrl.



This is functionally equivalent to baseUrl.toKsrpcUri().connect(env).

  



