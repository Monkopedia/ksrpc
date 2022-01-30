package com.monkopedia.ksrpc.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// No threads in JS, just use main dispatcher.
internal actual fun maybeCreateChannelThread(): CoroutineDispatcher = Dispatchers.Main