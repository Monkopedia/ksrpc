//[ksrpc](../../index.md)/[com.monkopedia.ksrpc.channels](../index.md)/[ChannelClient](index.md)



# ChannelClient  
 [common] interface [ChannelClient](index.md) : [SerializedChannel](../-serialized-channel/index.md), [KsrpcEnvironment.Element](../../com.monkopedia.ksrpc/-ksrpc-environment/-element/index.md)

A [SerializedChannel](../-serialized-channel/index.md) that can call into sub-services.



This could be a bidirectional conduit like a [Connection](../-connection/index.md), or it could be a client only service such as http client.

   


## Types  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc.channels/ChannelClient.Companion///PointingToDeclaration/"></a>[Companion](-companion/index.md)| <a name="com.monkopedia.ksrpc.channels/ChannelClient.Companion///PointingToDeclaration/"></a>[common]  <br>Content  <br>object [Companion](-companion/index.md)  <br><br><br>


## Functions  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc.channels/SerializedChannel/call/#com.monkopedia.ksrpc.channels.ChannelId#kotlin.String#com.monkopedia.ksrpc.channels.CallData/PointingToDeclaration/"></a>[call](../-serialized-channel/call.md)| <a name="com.monkopedia.ksrpc.channels/SerializedChannel/call/#com.monkopedia.ksrpc.channels.ChannelId#kotlin.String#com.monkopedia.ksrpc.channels.CallData/PointingToDeclaration/"></a>[common]  <br>Content  <br>abstract suspend fun [call](../-serialized-channel/call.md)(channelId: [ChannelId](../-channel-id/index.md), endpoint: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), data: [CallData](../-call-data/index.md)): [CallData](../-call-data/index.md)  <br><br><br>
| <a name="com.monkopedia.ksrpc/SuspendCloseable/close/#/PointingToDeclaration/"></a>[close](../../com.monkopedia.ksrpc/-suspend-closeable/close.md)| <a name="com.monkopedia.ksrpc/SuspendCloseable/close/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>abstract suspend fun [close](../../com.monkopedia.ksrpc/-suspend-closeable/close.md)()  <br>More info  <br>Called when the interaction with this object is done and its resources can be cleaned up.  <br><br><br>[common]  <br>Content  <br>abstract suspend fun [close](../-serialized-channel/close.md)(id: [ChannelId](../-channel-id/index.md))  <br><br><br>
| <a name="com.monkopedia.ksrpc.channels/ChannelClient/defaultChannel/#/PointingToDeclaration/"></a>[defaultChannel](default-channel.md)| <a name="com.monkopedia.ksrpc.channels/ChannelClient/defaultChannel/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open suspend fun [defaultChannel](default-channel.md)(): [SerializedService](../-serialized-service/index.md)  <br>More info  <br>Get a [SerializedService](../-serialized-service/index.md) that is the default on this client (i.e.  <br><br><br>
| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[equals](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[common]  <br>Content  <br>open operator fun [equals](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(other: [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)?): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)  <br><br><br>
| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[hashCode](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open fun [hashCode](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)  <br><br><br>
| <a name="com.monkopedia.ksrpc/SuspendCloseableObservable/onClose/#kotlin.coroutines.SuspendFunction0[kotlin.Unit]/PointingToDeclaration/"></a>[onClose](../../com.monkopedia.ksrpc/-suspend-closeable-observable/on-close.md)| <a name="com.monkopedia.ksrpc/SuspendCloseableObservable/onClose/#kotlin.coroutines.SuspendFunction0[kotlin.Unit]/PointingToDeclaration/"></a>[common]  <br>Content  <br>abstract suspend fun [onClose](../../com.monkopedia.ksrpc/-suspend-closeable-observable/on-close.md)(onClose: suspend () -> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))  <br>More info  <br>Add a callback to be invoked when [SuspendCloseable.close](../../com.monkopedia.ksrpc/-suspend-closeable/close.md) is called.  <br><br><br>
| <a name="kotlin/Any/toString/#/PointingToDeclaration/"></a>[toString](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2FtoString%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/toString/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open fun [toString](../-call-data/-companion/index.md#%5Bkotlin%2FAny%2FtoString%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)  <br><br><br>
| <a name="com.monkopedia.ksrpc.channels/ChannelClient/wrapChannel/#com.monkopedia.ksrpc.channels.ChannelId/PointingToDeclaration/"></a>[wrapChannel](wrap-channel.md)| <a name="com.monkopedia.ksrpc.channels/ChannelClient/wrapChannel/#com.monkopedia.ksrpc.channels.ChannelId/PointingToDeclaration/"></a>[common]  <br>Content  <br>abstract suspend fun [wrapChannel](wrap-channel.md)(channelId: [ChannelId](../-channel-id/index.md)): [SerializedService](../-serialized-service/index.md)  <br><br><br>


## Properties  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc.channels/ChannelClient/context/#/PointingToDeclaration/"></a>[context](context.md)| <a name="com.monkopedia.ksrpc.channels/ChannelClient/context/#/PointingToDeclaration/"></a> [common] open val [context](context.md): [CoroutineContext](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.coroutines/-coroutine-context/index.html)   <br>
| <a name="com.monkopedia.ksrpc.channels/ChannelClient/env/#/PointingToDeclaration/"></a>[env](env.md)| <a name="com.monkopedia.ksrpc.channels/ChannelClient/env/#/PointingToDeclaration/"></a> [common] abstract val [env](env.md): [KsrpcEnvironment](../../com.monkopedia.ksrpc/-ksrpc-environment/index.md)   <br>


## Inheritors  
  
|  Name| 
|---|
| <a name="com.monkopedia.ksrpc.channels/Connection///PointingToDeclaration/"></a>[Connection](../-connection/index.md)

