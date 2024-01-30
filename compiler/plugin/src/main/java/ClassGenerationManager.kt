/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.plugin

import org.jetbrains.kotlin.ir.declarations.IrClass

class ClassGenerationManager private constructor() {

    private val builders = mutableListOf<Pair<IrClass, IrClass.() -> Unit>>()

    fun IrClass.build(exec: IrClass.() -> Unit): IrClass = apply {
        builders.add(this to exec)
    }

    private fun build() {
        builders.forEach { (cls, method) ->
            cls.method()
        }
    }

    companion object {
        fun buildClasses(cls: (ClassGenerationManager) -> Unit) {
            val manager = ClassGenerationManager()
            cls(manager)
            manager.build()
        }
    }
}
