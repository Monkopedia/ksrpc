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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.internal.threadSafe
import com.monkopedia.ksrpc.internal.useBlocking
import com.monkopedia.ksrpc.internal.useSafe
import kotlinx.serialization.StringFormat
import kotlin.jvm.JvmName

interface Connection : ChannelHost, ChannelClient, SerializedChannel

// Problems with JS compiler and serialization
data class ChannelId(val id: String)

internal expect interface VoidService : RpcService

suspend inline fun <reified T : RpcService, reified R : RpcService> Connection.connect(
    host: (R) -> T
) = connect { channel ->
    host(channel.toStub()).serialized()
}

@JvmName("connectSerialized")
suspend inline fun Connection.connect(
    host: (SerializedService) -> SerializedService
) {
    val recv = defaultChannel()
    val serializedHost = host(recv)
    registerDefault(serializedHost)
}

suspend fun SerializedService.threadSafe(): SerializedService {
    return threadSafe {
        object : SerializedService {
            override suspend fun call(endpoint: String, input: CallData): CallData {
                return useSafe {
                    it.call(endpoint, input)
                }
            }

            override suspend fun close() {
                useSafe {
                    it.close()
                }
            }

            override val serialization: StringFormat
                get() = useBlocking {
                    it.serialization
                }
        }
    }
}

suspend fun Connection.threadSafe(): Connection {
    return threadSafe {
        object : Connection {
            override suspend fun registerHost(service: SerializedService): ChannelId {
                return useSafe {
                    it.registerHost(service.threadSafe())
                }
            }

            override suspend fun registerDefault(service: SerializedService) {
                return useSafe {
                    it.registerDefault(service.threadSafe())
                }
            }

            override suspend fun close(id: ChannelId) {
                return useSafe {
                    it.close(id)
                }
            }

            override suspend fun close() {
                return useSafe {
                    it.close()
                }
            }

            override val serialization: StringFormat
                get() = useBlocking {
                    it.serialization
                }

            override suspend fun wrapChannel(channelId: ChannelId): SerializedService {
                return useSafe {
                    it.wrapChannel(channelId).threadSafe()
                }
            }

            override suspend fun call(
                channelId: ChannelId,
                endpoint: String,
                data: CallData
            ): CallData {
                return useSafe {
                    it.call(channelId, endpoint, data)
                }
            }

        }
    }
}