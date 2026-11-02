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
package com.monkopedia.ksrpc.annotation

/**
 * Marks a [KsMethod] function as a JSON-RPC-style notification (fire-and-forget,
 * no response expected).
 *
 * When a method is annotated with both [KsMethod] and [KsNotification], transports
 * that support notification semantics (e.g. jsonrpc) will send the call without
 * an `id` and will not wait for or send a response. Transports without notification
 * semantics (HTTP, sockets, etc.) ignore this annotation and treat the call as a
 * normal Unit-returning request.
 *
 * The compiler plugin will emit an error if [KsNotification] is applied to a method
 * whose return type is not `Unit` — notifications by definition have no reply.
 *
 * This annotation replaces the previous heuristic where any Unit-returning method
 * was implicitly treated as a notification. With [KsNotification], Unit-returning
 * methods without this annotation are treated as normal requests that happen to
 * return Unit.
 *
 * ## Ordering
 *
 * Notifications on a connection are handled one at a time, in the order they were
 * sent. A handler does not start until the previous notification's handler has
 * returned, so a consumer applying a stream of changes sees them in sequence.
 *
 * Requests are not part of that order. They are handled concurrently, with each other
 * and with notifications, so a request sent after a notification can be handled
 * before that notification's handler finishes.
 *
 * Two consequences of handling notifications in sequence: a slow notification handler
 * delays every notification after it on the same connection, and a notification
 * handler that waits for a later notification to arrive will never return.
 *
 * On transports without notification semantics, a [KsNotification] call is an
 * ordinary request whose reply the caller awaits, so successive calls are handled in
 * the order they are made.
 */
@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsNotification
