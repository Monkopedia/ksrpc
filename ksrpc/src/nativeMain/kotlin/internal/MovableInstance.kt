/*
 * Copyright 2021 Jason Monk
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package internal

import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.DetachedObjectGraph
import kotlin.native.concurrent.TransferMode.UNSAFE
import kotlin.native.concurrent.attach
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.SynchronizedObject.Status.UNLOCKED
import kotlinx.atomicfu.locks.withLock
import platform.posix.pthread_self

@ThreadLocal
private val currentInstances = mutableMapOf<MovableInstance<*>, Any>()

internal class MovableInstance<T>(val creator: () -> T) : SynchronizedObject() {
    val graph = AtomicReference(DetachedObjectGraph<T?> { null })
    val holdsLock: Boolean
        get() = lock.value.let { it.status != UNLOCKED && it.ownerThreadId == pthread_self() }
}

internal var <T : Any> MovableInstance<T>.instance: T?
    get() = currentInstances[this] as? T
    set(value) {
        if (value != null) {
            currentInstances[this] = value
        } else {
            currentInstances.remove(this)
        }
    }

internal inline fun <reified T : Any, R> MovableInstance<T>.using(block: (T) -> R): R {
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
