//[ksrpc](../index.md)/[com.monkopedia.ksrpc.channels](index.md)/[registerHost](register-host.md)



# registerHost  
[common]  
Content  
inline suspend fun <[T](register-host.md) : [RpcService](../com.monkopedia.ksrpc/-rpc-service/index.md)> [ChannelHost](-channel-host/index.md).[registerHost](register-host.md)(service: [T](register-host.md)): [ChannelId](-channel-id/index.md)  
suspend fun <[T](register-host.md) : [RpcService](../com.monkopedia.ksrpc/-rpc-service/index.md)> [ChannelHost](-channel-host/index.md).[registerHost](register-host.md)(service: [T](register-host.md), obj: [RpcObject](../com.monkopedia.ksrpc/-rpc-object/index.md)<[T](register-host.md)>): [ChannelId](-channel-id/index.md)  
More info  


Register a service to be hosted, the [ChannelId](-channel-id/index.md) ollocated to this service is returned. Generally this should not be called directly, as it will happen automatically when services are returned from KsMethod tagged methods.

  



