/*
 * Copyright 2020 Jason Monk
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

import io.ktor.application.call
import io.ktor.client.request.post
import io.ktor.http.decodeURLPart
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post

fun Routing.serve(
    basePath: String,
    channel: SerializedChannel,
    errorListener: ErrorListener = ErrorListener { }
) {
    val baseStripped = basePath.trimEnd('/')
    post("$baseStripped/{method}") {
        try {
            val method = call.parameters["method"]?.decodeURLPart() ?: error("Missing method")
            val content = call.receive<String>()
            val response = channel.call(method, content)
            call.respond(response)
        } catch (t: Throwable) {
            errorListener.onError(t)
            throw t
        }
    }
}
