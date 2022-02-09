//[ksrpc](../../index.md)/[com.monkopedia.ksrpc.channels](../index.md)/[CallData](index.md)



# CallData  
 [common] data class [CallData](index.md)

Wrapper around data being serialized through calls. Could be a reference to a string for a serialized object or to binary data.

   


## Types  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc.channels/CallData.Companion///PointingToDeclaration/"></a>[Companion](-companion/index.md)| <a name="com.monkopedia.ksrpc.channels/CallData.Companion///PointingToDeclaration/"></a>[common]  <br>Content  <br>object [Companion](-companion/index.md)  <br><br><br>


## Functions  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc.channels/CallData/copy/#kotlin.Any?/PointingToDeclaration/"></a>[copy](copy.md)| <a name="com.monkopedia.ksrpc.channels/CallData/copy/#kotlin.Any?/PointingToDeclaration/"></a>[common]  <br>Content  <br>fun [copy](copy.md)(value: [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)?): [CallData](index.md)  <br><br><br>
| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[equals](-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[common]  <br>Content  <br>open operator override fun [equals](-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(other: [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)?): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)  <br><br><br>
| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[hashCode](-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open override fun [hashCode](-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)  <br><br><br>
| <a name="com.monkopedia.ksrpc.channels/CallData/readBinary/#/PointingToDeclaration/"></a>[readBinary](read-binary.md)| <a name="com.monkopedia.ksrpc.channels/CallData/readBinary/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>fun [readBinary](read-binary.md)(): ByteReadChannel  <br>More info  <br>Get the ByteReadChannel for the binary data held by this call..  <br><br><br>
| <a name="com.monkopedia.ksrpc.channels/CallData/readSerialized/#/PointingToDeclaration/"></a>[readSerialized](read-serialized.md)| <a name="com.monkopedia.ksrpc.channels/CallData/readSerialized/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>fun [readSerialized](read-serialized.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)  <br>More info  <br>Read the serialized content of this object.  <br><br><br>
| <a name="com.monkopedia.ksrpc.channels/CallData/toString/#/PointingToDeclaration/"></a>[toString](to-string.md)| <a name="com.monkopedia.ksrpc.channels/CallData/toString/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open override fun [toString](to-string.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)  <br><br><br>


## Properties  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc.channels/CallData/isBinary/#/PointingToDeclaration/"></a>[isBinary](is-binary.md)| <a name="com.monkopedia.ksrpc.channels/CallData/isBinary/#/PointingToDeclaration/"></a> [common] val [isBinary](is-binary.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)   <br>

