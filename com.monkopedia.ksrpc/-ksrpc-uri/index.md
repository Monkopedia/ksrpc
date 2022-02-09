//[ksrpc](../../index.md)/[com.monkopedia.ksrpc](../index.md)/[KsrpcUri](index.md)



# KsrpcUri  
 [common] data class [KsrpcUri](index.md)(**type**: [KsrpcType](../-ksrpc-type/index.md), **path**: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))

Class with explicit specification of the type of connection used in a uri.



Generally created using [String.toKsrpcUri](../to-ksrpc-uri.md).

   


## Constructors  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc/KsrpcUri/KsrpcUri/#com.monkopedia.ksrpc.KsrpcType#kotlin.String/PointingToDeclaration/"></a>[KsrpcUri](-ksrpc-uri.md)| <a name="com.monkopedia.ksrpc/KsrpcUri/KsrpcUri/#com.monkopedia.ksrpc.KsrpcType#kotlin.String/PointingToDeclaration/"></a> [common] fun [KsrpcUri](-ksrpc-uri.md)(type: [KsrpcType](../-ksrpc-type/index.md), path: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))   <br>


## Functions  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc/KsrpcUri/component1/#/PointingToDeclaration/"></a>[component1](component1.md)| <a name="com.monkopedia.ksrpc/KsrpcUri/component1/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>operator fun [component1](component1.md)(): [KsrpcType](../-ksrpc-type/index.md)  <br><br><br>
| <a name="com.monkopedia.ksrpc/KsrpcUri/component2/#/PointingToDeclaration/"></a>[component2](component2.md)| <a name="com.monkopedia.ksrpc/KsrpcUri/component2/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>operator fun [component2](component2.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)  <br><br><br>
| <a name="com.monkopedia.ksrpc/KsrpcUri/copy/#com.monkopedia.ksrpc.KsrpcType#kotlin.String/PointingToDeclaration/"></a>[copy](copy.md)| <a name="com.monkopedia.ksrpc/KsrpcUri/copy/#com.monkopedia.ksrpc.KsrpcType#kotlin.String/PointingToDeclaration/"></a>[common]  <br>Content  <br>fun [copy](copy.md)(type: [KsrpcType](../-ksrpc-type/index.md), path: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [KsrpcUri](index.md)  <br><br><br>
| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[equals](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[common]  <br>Content  <br>open operator override fun [equals](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(other: [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)?): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)  <br><br><br>
| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[hashCode](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open override fun [hashCode](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)  <br><br><br>
| <a name="com.monkopedia.ksrpc/KsrpcUri/toString/#/PointingToDeclaration/"></a>[toString](to-string.md)| <a name="com.monkopedia.ksrpc/KsrpcUri/toString/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open override fun [toString](to-string.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)  <br><br><br>


## Properties  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc/KsrpcUri/path/#/PointingToDeclaration/"></a>[path](path.md)| <a name="com.monkopedia.ksrpc/KsrpcUri/path/#/PointingToDeclaration/"></a> [common] val [path](path.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)   <br>
| <a name="com.monkopedia.ksrpc/KsrpcUri/type/#/PointingToDeclaration/"></a>[type](type.md)| <a name="com.monkopedia.ksrpc/KsrpcUri/type/#/PointingToDeclaration/"></a> [common] val [type](type.md): [KsrpcType](../-ksrpc-type/index.md)   <br>


## Extensions  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc//connect/com.monkopedia.ksrpc.KsrpcUri#com.monkopedia.ksrpc.KsrpcEnvironment#kotlin.Function0[io.ktor.client.HttpClient]/PointingToDeclaration/"></a>[connect](../connect.md)| <a name="com.monkopedia.ksrpc//connect/com.monkopedia.ksrpc.KsrpcUri#com.monkopedia.ksrpc.KsrpcEnvironment#kotlin.Function0[io.ktor.client.HttpClient]/PointingToDeclaration/"></a>[common]  <br>Content  <br>suspend fun [KsrpcUri](index.md).[connect](../connect.md)(env: [KsrpcEnvironment](../-ksrpc-environment/index.md), clientFactory: () -> HttpClient = { HttpClient { } }): [ChannelClient](../../com.monkopedia.ksrpc.channels/-channel-client/index.md)  <br>More info  <br>Creates a [ChannelClient](../../com.monkopedia.ksrpc.channels/-channel-client/index.md) from a [KsrpcUri](index.md).  <br><br><br>[jvm, native]  <br>Content  <br>[jvm]  <br>suspend fun [KsrpcUri](index.md#%5Bcom.monkopedia.ksrpc%2FKsrpcUri%2F%2F%2FPointingToDeclaration%2F%5D%2FExtensions%2F987004091).[connect](../connect.md)(env: [KsrpcEnvironment](../-ksrpc-environment/index.md), clientFactory: () -> HttpClient = { HttpClient { } }): [ChannelClient](../../com.monkopedia.ksrpc.channels/-channel-client/index.md)  <br>[native]  <br>suspend fun [KsrpcUri](index.md#%5Bcom.monkopedia.ksrpc%2FKsrpcUri%2F%2F%2FPointingToDeclaration%2F%5D%2FExtensions%2F1414128974).[connect](../connect.md)(env: [KsrpcEnvironment](../-ksrpc-environment/index.md), clientFactory: () -> HttpClient = { HttpClient { } }): [ChannelClient](../../com.monkopedia.ksrpc.channels/-channel-client/index.md)  <br>More info  <br>  <br><br><br>

