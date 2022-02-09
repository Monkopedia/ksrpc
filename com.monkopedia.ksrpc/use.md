//[ksrpc](../index.md)/[com.monkopedia.ksrpc](index.md)/[use](use.md)



# use  
[common]  
Content  
inline suspend fun <[T](use.md) : [SuspendCloseable](-suspend-closeable/index.md), [R](use.md)> [T](use.md).[use](use.md)(usage: ([T](use.md)) -> [R](use.md)): [R](use.md)  
More info  


Helper that runs usage then invokes [SuspendCloseable.close](-suspend-closeable/close.md) in the finally block.

  



