package internal

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.withLock
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.DetachedObjectGraph
import kotlin.native.concurrent.attach

class MovableInstance<T>(creator: () -> T) : SynchronizedObject() {
    val graph = AtomicReference(DetachedObjectGraph { creator() })
    var instance: Any? = null
}

inline fun <reified T, R> MovableInstance<T>.using(block: (T) -> R): R {
    println("About to grab lock")
    return withLock {
        println("Have lock")
        if (instance != null) {
            return block(instance as T)
        }
        val objInstance = graph.value.attach()
        instance = objInstance
        block(objInstance).also {
            instance = null
            graph.value = DetachedObjectGraph { objInstance }
        }
    }.also {
        println("Released lock")
    }
}
