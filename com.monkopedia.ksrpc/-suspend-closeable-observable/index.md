//[ksrpc](../../index.md)/[com.monkopedia.ksrpc](../index.md)/[SuspendCloseableObservable](index.md)



# SuspendCloseableObservable  
 [common] interface [SuspendCloseableObservable](index.md) : [SuspendCloseable](../-suspend-closeable/index.md)

Used for implementations of [SuspendCloseable](../-suspend-closeable/index.md) that need observers attached to be notified when [SuspendCloseable.close](../-suspend-closeable/close.md) is called.

   


## Functions  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc/SuspendCloseable/close/#/PointingToDeclaration/"></a>[close](../-suspend-closeable/close.md)| <a name="com.monkopedia.ksrpc/SuspendCloseable/close/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>abstract suspend fun [close](../-suspend-closeable/close.md)()  <br>More info  <br>Called when the interaction with this object is done and its resources can be cleaned up.  <br><br><br>
| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[equals](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[common]  <br>Content  <br>open operator fun [equals](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(other: [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)?): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)  <br><br><br>
| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[hashCode](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open fun [hashCode](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)  <br><br><br>
| <a name="com.monkopedia.ksrpc/SuspendCloseableObservable/onClose/#kotlin.coroutines.SuspendFunction0[kotlin.Unit]/PointingToDeclaration/"></a>[onClose](on-close.md)| <a name="com.monkopedia.ksrpc/SuspendCloseableObservable/onClose/#kotlin.coroutines.SuspendFunction0[kotlin.Unit]/PointingToDeclaration/"></a>[common]  <br>Content  <br>abstract suspend fun [onClose](on-close.md)(onClose: suspend () -> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))  <br>More info  <br>Add a callback to be invoked when [SuspendCloseable.close](../-suspend-closeable/close.md) is called.  <br><br><br>
| <a name="kotlin/Any/toString/#/PointingToDeclaration/"></a>[toString](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2FtoString%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/toString/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open fun [toString](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2FtoString%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)  <br><br><br>


## Inheritors  
  
|  Name| 
|---|
| <a name="com.monkopedia.ksrpc.channels/SerializedChannel///PointingToDeclaration/"></a>[SerializedChannel](../../com.monkopedia.ksrpc.channels/-serialized-channel/index.md)
| <a name="com.monkopedia.ksrpc.channels/SerializedService///PointingToDeclaration/"></a>[SerializedService](../../com.monkopedia.ksrpc.channels/-serialized-service/index.md)

