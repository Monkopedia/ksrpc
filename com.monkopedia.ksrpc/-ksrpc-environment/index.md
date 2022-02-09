//[ksrpc](../../index.md)/[com.monkopedia.ksrpc](../index.md)/[KsrpcEnvironment](index.md)



# KsrpcEnvironment  
 [common] interface [KsrpcEnvironment](index.md)

Global configuration for KSRPC channels and services.

   


## Types  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc/KsrpcEnvironment.Element///PointingToDeclaration/"></a>[Element](-element/index.md)| <a name="com.monkopedia.ksrpc/KsrpcEnvironment.Element///PointingToDeclaration/"></a>[common]  <br>Content  <br>interface [Element](-element/index.md)  <br><br><br>


## Functions  
  
|  Name|  Summary| 
|---|---|
| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[equals](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/equals/#kotlin.Any?/PointingToDeclaration/"></a>[common]  <br>Content  <br>open operator fun [equals](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2Fequals%2F%23kotlin.Any%3F%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(other: [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)?): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)  <br><br><br>
| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[hashCode](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/hashCode/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open fun [hashCode](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2FhashCode%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)  <br><br><br>
| <a name="kotlin/Any/toString/#/PointingToDeclaration/"></a>[toString](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2FtoString%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)| <a name="kotlin/Any/toString/#/PointingToDeclaration/"></a>[common]  <br>Content  <br>open fun [toString](../../com.monkopedia.ksrpc.channels/-call-data/-companion/index.md#%5Bkotlin%2FAny%2FtoString%2F%23%2FPointingToDeclaration%2F%5D%2FFunctions%2F-909481617)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)  <br><br><br>


## Properties  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc/KsrpcEnvironment/defaultScope/#/PointingToDeclaration/"></a>[defaultScope](default-scope.md)| <a name="com.monkopedia.ksrpc/KsrpcEnvironment/defaultScope/#/PointingToDeclaration/"></a> [common] abstract val [defaultScope](default-scope.md): CoroutineScope   <br>
| <a name="com.monkopedia.ksrpc/KsrpcEnvironment/errorListener/#/PointingToDeclaration/"></a>[errorListener](error-listener.md)| <a name="com.monkopedia.ksrpc/KsrpcEnvironment/errorListener/#/PointingToDeclaration/"></a> [common] abstract val [errorListener](error-listener.md): [ErrorListener](../-error-listener/index.md)   <br>
| <a name="com.monkopedia.ksrpc/KsrpcEnvironment/serialization/#/PointingToDeclaration/"></a>[serialization](serialization.md)| <a name="com.monkopedia.ksrpc/KsrpcEnvironment/serialization/#/PointingToDeclaration/"></a> [common] abstract val [serialization](serialization.md): StringFormat   <br>


## Inheritors  
  
|  Name| 
|---|
| <a name="com.monkopedia.ksrpc/KsrpcEnvironmentBuilder///PointingToDeclaration/"></a>[KsrpcEnvironmentBuilder](../-ksrpc-environment-builder/index.md)


## Extensions  
  
|  Name|  Summary| 
|---|---|
| <a name="com.monkopedia.ksrpc//onError/com.monkopedia.ksrpc.KsrpcEnvironment#com.monkopedia.ksrpc.ErrorListener/PointingToDeclaration/"></a>[onError](../on-error.md)| <a name="com.monkopedia.ksrpc//onError/com.monkopedia.ksrpc.KsrpcEnvironment#com.monkopedia.ksrpc.ErrorListener/PointingToDeclaration/"></a>[common]  <br>Content  <br>fun [KsrpcEnvironment](index.md).[onError](../on-error.md)(listener: [ErrorListener](../-error-listener/index.md)): [KsrpcEnvironment](index.md)  <br>More info  <br>Convenience method for easily creating a copy of [KsrpcEnvironment](index.md) with a local error listener.  <br><br><br>
| <a name="com.monkopedia.ksrpc//reconfigure/com.monkopedia.ksrpc.KsrpcEnvironment#kotlin.Function1[com.monkopedia.ksrpc.KsrpcEnvironmentBuilder,kotlin.Unit]/PointingToDeclaration/"></a>[reconfigure](../reconfigure.md)| <a name="com.monkopedia.ksrpc//reconfigure/com.monkopedia.ksrpc.KsrpcEnvironment#kotlin.Function1[com.monkopedia.ksrpc.KsrpcEnvironmentBuilder,kotlin.Unit]/PointingToDeclaration/"></a>[common]  <br>Content  <br>fun [KsrpcEnvironment](index.md).[reconfigure](../reconfigure.md)(builder: [KsrpcEnvironmentBuilder](../-ksrpc-environment-builder/index.md).() -> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)): [KsrpcEnvironment](index.md)  <br>More info  <br>Creates a copy of the [KsrpcEnvironment](index.md) provided and allows changes to it before returning it.  <br><br><br>

