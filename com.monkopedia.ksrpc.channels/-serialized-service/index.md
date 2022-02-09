//[ksrpc](../../index.md)/[com.monkopedia.ksrpc.channels](../index.md)/[SerializedService](index.md)



# SerializedService  
 [common] interface [SerializedService](index.md) : [SuspendCloseableObservable](../../com.monkopedia.ksrpc/-suspend-closeable-observable/index.md), [ContextContainer](../-context-container/index.md), [KsrpcEnvironment.Element](../../com.monkopedia.ksrpc/-ksrpc-environment/-element/index.md)

Serialized version of a service. This can be transformed to and from a service using serialized and SerializedService.toStub.

   


## Functions  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc.channels/SerializedService/call/#kotlin.String#com.monkopedia.ksrpc.channels.CallData/PointingToDeclaration/"></a>[call](call.md)| <a name="com.monkopedia.ksrpc.channels/SerializedService/call/#kotlin.String#com.monkopedia.ksrpc.channels.CallData/PointingToDeclaration/"></a>[common]  <br>Content  <br>abstract suspend fun [call](call.md)(endpoint: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), input: [CallData](../-call-data/index.md)): [CallData](../-call-data/index.md)  <br><br><br>
| <a name="com.monkopedia.ksrpc/SuspendCloseable/close/#/PointingToDeclaration/"></a>[close](../../com.monkopedia.ksrpc/-suspend-closeable/close.md)| <a name="com.monkopedia.ksrpc/SuspendCloseable/close/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>abstract suspend fun [close](../../com.monkopedia.ksrpc/-suspend-closeable/close.md)()  <br>More info  <br>Called when the interaction with this object is done and its resources can be cleaned up.  <br><br><br>
| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[equals](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[common]  <br>Content  <br>open operator fun [equals](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(other: [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)?): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)  <br><br><br>
| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[hashCode](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open fun [hashCode](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)  <br><br><br>
| <a name="com.monkopedia.ksrpc/SuspendCloseableObservable/onClose/#kotlin.coroutines.SuspendFunction0[kotlin.Unit]/PointingToDeclaration/"></a>[onClose](../../com.monkopedia.ksrpc/-suspend-closeable-observable/on-close.md)| <a name="com.monkopedia.ksrpc/SuspendCloseableObservable/onClose/#kotlin.coroutines.SuspendFunction0[kotlin.Unit]/PointingToDeclaration/"></a>[common]  <br>Content  <br>abstract suspend fun [onClose](../../com.monkopedia.ksrpc/-suspend-closeable-observable/on-close.md)(onClose: suspend () -> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))  <br>More info  <br>Add a callback to be invoked when [SuspendCloseable.close](../../com.monkopedia.ksrpc/-suspend-closeable/close.md) is called.  <br><br><br>
| <a name="kotlin/Any/toString/#/PointingToDeclaration/"></a>[toString](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2FtoString%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/toString/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open fun [toString](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2FtoString%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)  <br><br><br>


## Properties  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc.channels/SerializedService/context/#/PointingToDeclaration/"></a>[context](context.md)| <a name="com.monkopedia.ksrpc.channels/SerializedService/context/#/PointingToDeclaration/"></a> [common] open val [context](context.md): [CoroutineContext](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.coroutines/-coroutine-context/index.html)   <br>
| <a name="com.monkopedia.ksrpc.channels/SerializedService/env/#/PointingToDeclaration/"></a>[env](env.md)| <a name="com.monkopedia.ksrpc.channels/SerializedService/env/#/PointingToDeclaration/"></a> [common] abstract val [env](env.md): [KsrpcEnvironment](../../com.monkopedia.ksrpc/-ksrpc-environment/index.md)   <br>


## Extensions  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc//toStub/com.monkopedia.ksrpc.channels.SerializedService#/PointingToDeclaration/"></a>[toStub](../../com.monkopedia.ksrpc/to-stub.md)| <a name="com.monkopedia.ksrpc//toStub/com.monkopedia.ksrpc.channels.SerializedService#/PointingToDeclaration/"></a>[common]  <br>Content  <br>inline fun <[T](../../com.monkopedia.ksrpc/to-stub.md) : [RpcService](../../com.monkopedia.ksrpc/-rpc-service/index.md)> [SerializedService](index.md).[toStub](../../com.monkopedia.ksrpc/to-stub.md)(): [T](../../com.monkopedia.ksrpc/to-stub.md)  <br>More info  <br>Convert a [SerializedService](index.md) to a [T](../../com.monkopedia.ksrpc/to-stub.md) for use as a client.  <br><br><br>

