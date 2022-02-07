package internal

import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.DetachedObjectGraph
import kotlin.native.concurrent.attach
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.withLock

@ThreadLocal
private val currentInstances = mutableMapOf<MovableInstance<*>, Any>()

class MovableInstance<T>(val creator: () -> T) : SynchronizedObject() {
    val graph = AtomicReference(DetachedObjectGraph<T?> { null })
}

var <T : Any> MovableInstance<T>.instance: T?
    get() = currentInstances[this] as? T
    set(value) {
        if (value != null) {
            currentInstances[this] = value
        } else {
            currentInstances.remove(this)
        }
    }

inline fun <reified T : Any, R> MovableInstance<T>.using(block: (T) -> R): R {
    return withLock {
        println("Have lock")
        instance?.let {
            println("Using instance")
            return block(it)
        }
        val objInstance = graph.value.attach() ?: creator()
        println("Attached")
        instance = objInstance
        block(objInstance).also {
            instance = null
            println("Detaching")
            graph.value = DetachedObjectGraph { objInstance }
        }
    }
}
