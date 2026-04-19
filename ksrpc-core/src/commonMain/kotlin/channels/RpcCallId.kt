/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.channels

/**
 * A transport-agnostic identifier for an in-flight RPC call.
 *
 * Each transport that supports cancellation (or other features keyed by an original request)
 * provides its own subtype to carry the identifier in whatever native form the wire protocol
 * requires (`PacketCallId(channelId, messageId)` for packet transports,
 * `JsonRpcCallId(JsonPrimitive)` for json-rpc, etc.).
 *
 * Not declared `sealed` because the known subtypes live in sibling transport modules
 * (ksrpc-packets, ksrpc-jsonrpc), which Kotlin does not permit for sealed hierarchies — a
 * sealed hierarchy would have to collapse them into a single module. Consumers that need to
 * inspect a transport-specific shape should `is` / cast to the subtype they expect.
 *
 * Subtypes MUST be data classes / objects with stable `equals` and `hashCode` implementations
 * so they can be used as keys in cancellation-tracking maps and compared in tests.
 */
interface RpcCallId
