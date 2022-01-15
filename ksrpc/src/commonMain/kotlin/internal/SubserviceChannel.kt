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
package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.CallData
import com.monkopedia.ksrpc.ChannelClient
import com.monkopedia.ksrpc.ChannelId
import com.monkopedia.ksrpc.SerializedService
import kotlinx.serialization.StringFormat

internal class SubserviceChannel(
    private val baseChannel: ChannelClient,
    private val serviceId: ChannelId
) : SerializedService, ChannelClient by baseChannel {
    override val serialization: StringFormat
        get() = baseChannel.serialization

    override suspend fun call(endpoint: String, input: CallData): CallData {
        return baseChannel.call(serviceId, endpoint, input)
    }

    override suspend fun close() {
        baseChannel.close(serviceId)
    }
}
