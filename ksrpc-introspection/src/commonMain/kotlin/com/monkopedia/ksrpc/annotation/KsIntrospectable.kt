package com.monkopedia.ksrpc.annotation

/**
 * Annotation tagging an interface for processing by the compiler plugin.
 *
 * This should be placed on interfaces which extend [IntrospectableRpcService].
 */
@Retention(AnnotationRetention.BINARY)
annotation class KsIntrospectable
