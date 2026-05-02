# Module flow-streaming

# Flow Streaming

The `ksrpc-flow` module lets you use `kotlinx.coroutines.Flow<T>` in `@KsMethod` signatures for streaming data over ksrpc connections.

## Setup

Add the flow dependency:

```kotlin
implementation("com.monkopedia.ksrpc:ksrpc-flow:0.11.1")
```

Flow streaming requires a bidirectional transport (WebSockets, sockets, or service workers) because the flow protocol uses sub-services internally.

## Using Flow in service signatures

Declare `Flow<T>` as a return type on a `@KsMethod`:

```kotlin
import kotlinx.coroutines.flow.Flow

@KsService
interface EventService : RpcService {
    @KsMethod("/events")
    suspend fun streamEvents(filter: String): Flow<Event>
}
```

The compiler plugin handles the transformation automatically. On the wire, `Flow<T>` is backed by a [KsFlowService] sub-service.

## Implementing a flow endpoint

Return a standard `Flow` from your implementation:

```kotlin
class EventServiceImpl : EventService {
    override suspend fun streamEvents(filter: String): Flow<Event> = flow {
        while (true) {
            val event = waitForEvent(filter)
            emit(event)
        }
    }
}
```

The runtime wraps your `Flow` in a `KsFlowService` using `asKsFlow()` and registers it as a sub-service on the connection.

## Collecting on the client

The client receives a standard `Flow<T>` and collects it normally:

```kotlin
val service = connection.defaultChannel().toStub<EventService>()
service.streamEvents("error").collect { event ->
    println("Got event: ${event.message}")
}
```

### Lifecycle

When you use `Flow<T>` in a return type, the returned flow is single-use and auto-closing. After collection completes (normally, with an error, or via cancellation), the underlying sub-service is closed automatically.

### Cancellation

Cancelling the collecting coroutine propagates cancellation to the server:

```kotlin
val job = launch {
    service.streamEvents("all").collect { event ->
        process(event)
    }
}

// Later: cancel the collection
job.cancel() // server-side flow collection is also cancelled
```

## Back-pressure

Flow items are delivered through the sub-service call path. Each `emit` on the server side is a suspend call to the client's collector -- the server blocks until the client processes the item. This provides natural back-pressure without additional configuration.

## Error propagation

If the server-side flow throws an exception, the client receives it as an `RpcFailure` through the collector's `onError` signal, which is then re-thrown from `collect`.

## Advanced: KsFlowService directly

If you need multi-collection or explicit lifecycle control, declare [KsFlowService] directly instead of `Flow<T>`:

```kotlin
@KsService
interface AdvancedEventService : RpcService {
    @KsMethod("/events")
    suspend fun streamEvents(filter: String): KsFlowService<Event>
}
```

With `KsFlowService<T>`:

- You can call `collect` multiple times (each gets its own server-side collection job).
- The service does NOT auto-close after collection -- you must call `close()` explicitly.
- Each `startCollection` returns a [KsCollectionToken] that can cancel that specific collection.

```kotlin
val flowService = service.streamEvents("all")
try {
    // First collection
    flowService.collect { event -> handleBatch1(event) }
    // Second collection (same flow service)
    flowService.collect { event -> handleBatch2(event) }
} finally {
    flowService.close()
}
```

## Under the hood

The flow protocol uses three sub-services:

- **[KsFlowService]** -- the main flow service, with a `startCollection` method that accepts a collector and returns a token.
- **[KsFlowCollector]** -- a callback sub-service that receives `onItem`, `onComplete`, and `onError` signals. Terminal signals (`onComplete`, `onError`) are annotated with `@KsNotification`.
- **[KsCollectionToken]** -- a sub-service with a `cancelCollection` method for cancelling an active collection.

The compiler plugin generates the `FlowSubserviceTransformer` that bridges between `Flow<T>` and `KsFlowService<T>` transparently.
