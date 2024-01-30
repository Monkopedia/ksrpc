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
package com.monkopedia.ksrpc.ktor

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.ktor.internal.HttpSerializedChannel
import io.ktor.client.HttpClient

/**
 * Turn an [HttpClient] into a [ChannelClient] for a specified baseUrl.
 *
 * This is functionally equivalent to baseUrl.toKsrpcUri().connect(env).
 */
fun HttpClient.asConnection(baseUrl: String, env: KsrpcEnvironment<String>): ChannelClient<String> =
    HttpSerializedChannel(this, baseUrl.trimEnd('/'), env)
