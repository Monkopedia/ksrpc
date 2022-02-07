package internal

import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.DetachedObjectGraph
import kotlin.native.concurrent.attach
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.SynchronizedObject.Status.UNLOCKED
import kotlinx.atomicfu.locks.withLock
import platform.posix.pthread_self
import kotlin.native.concurrent.TransferMode.UNSAFE

@ThreadLocal
private val currentInstances = mutableMapOf<MovableInstance<*>, Any>()

class MovableInstance<T>(val creator: () -> T) : SynchronizedObject() {
    val graph = AtomicReference(DetachedObjectGraph<T?> { null })
    val holdsLock: Boolean
        get() = lock.value.let { it.status != UNLOCKED && it.ownerThreadId == pthread_self() }
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
    if (holdsLock) {
        return block(instance ?: error("Holds lock but missing instance"))
    }
    return withLock {
        val objInstance = graph.value.attach() ?: creator()
        try {
            instance = objInstance
            block(objInstance)
        } finally {
            instance = null
            graph.value = DetachedObjectGraph(UNSAFE) { objInstance }
        }
    }
}
