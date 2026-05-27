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
package com.monkopedia.ksrpc.plugin.fir

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.psi.KtElement

/**
 * FIR-phase diagnostic factories emitted by the ksrpc compiler plugin's
 * `FirDeclarationChecker`s. Each factory carries a single `String` payload so
 * the existing message text (used by the plugin's compile-testing test suite)
 * is preserved verbatim from the IR-phase `PluginReporter.reportUserError`
 * diagnostics they replace.
 *
 * Errors are attached to the offending declaration / annotation source element,
 * producing precise `KtSourceElement` locations (file:line:column) and IDE
 * red-squiggles on the offending element rather than the file as a whole.
 *
 * See issue #65 for the migration context.
 */
@Suppress("detekt:ObjectPropertyNaming")
object KsrpcDiagnostics : KtDiagnosticsContainer() {

    // 0: @KsService applied to a non-interface (class, object, enum, annotation)
    val NOT_INTERFACE: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 1: @KsService applied to a subtype of another @KsService
    val KSSERVICE_SUBTYPE_OF_KSSERVICE: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 2: Class without RpcService supertype
    val NOT_RPC_SERVICE: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 3: Class-level variant type parameter (out T / in T)
    val VARIANT_TYPE_PARAMETER: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 4: @KsMethod function with type parameters
    val METHOD_TYPE_PARAMETERS: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 5: @KsMethod function with more than 1 non-dispatch parameter
    val METHOD_TOO_MANY_PARAMETERS: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 6a: unparseable @KsMethod("...") argument
    val UNPARSEABLE_ENDPOINT: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 6b: duplicate @KsMethod("...") endpoint
    val DUPLICATE_ENDPOINT: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 7a: binary types in both input and output (unsupported)
    val BINARY_IN_BOTH_POSITIONS: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 7b: binary adapter module missing on compile classpath
    val BINARY_ADAPTER_MISSING: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 8: @KsNotification on non-Unit-returning method
    val NOTIFICATION_NON_UNIT: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 9: @KsMethod used outside a @KsService interface
    val KSMETHOD_OUTSIDE_KSSERVICE: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 10: Multiple @KsService supertypes on a class
    val MULTIPLE_KSSERVICE_SUPERTYPES: KtDiagnosticFactory1<String> by error1<KtElement, String>()

    // 11: @KsContext-meta-annotated annotation whose `binding` argument doesn't
    // implement `KsContextBinding`.
    val KSCONTEXT_BINDING_NOT_KSCONTEXTBINDING: KtDiagnosticFactory1<String>
        by error1<KtElement, String>()

    // 12: Two @KsContext-meta-annotated annotations on one method whose
    // bindings declare the same `wireKey`.
    val KSCONTEXT_DUPLICATE_WIRE_KEY: KtDiagnosticFactory1<String>
        by error1<KtElement, String>()

    // 13: Service extends wrong tier for its method signatures (e.g. extends
    // RpcService but has a method returning a sub-service, which requires
    // RpcHostService or RpcBidiService).
    val SERVICE_TIER_MISMATCH: KtDiagnosticFactory1<String>
        by error1<KtElement, String>()

    // 14: Unsupported nested shape inside a `Result<...>` return type (issue
    // #133, v1): Result<Flow<…>>, Flow<Result<…>>, Result<RpcService-subtype>,
    // nested Result<Result<…>>.
    val UNSUPPORTED_RESULT_SHAPE: KtDiagnosticFactory1<String>
        by error1<KtElement, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = KsrpcDiagnosticRenderers
}

@Suppress("ktlint:standard:property-naming")
private object KsrpcDiagnosticRenderers : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap(
        "ksrpc"
    ) { map ->
        with(KsrpcDiagnostics) {
            map.put(NOT_INTERFACE, "{0}", CommonRenderers.STRING)
            map.put(KSSERVICE_SUBTYPE_OF_KSSERVICE, "{0}", CommonRenderers.STRING)
            map.put(NOT_RPC_SERVICE, "{0}", CommonRenderers.STRING)
            map.put(VARIANT_TYPE_PARAMETER, "{0}", CommonRenderers.STRING)
            map.put(METHOD_TYPE_PARAMETERS, "{0}", CommonRenderers.STRING)
            map.put(METHOD_TOO_MANY_PARAMETERS, "{0}", CommonRenderers.STRING)
            map.put(UNPARSEABLE_ENDPOINT, "{0}", CommonRenderers.STRING)
            map.put(DUPLICATE_ENDPOINT, "{0}", CommonRenderers.STRING)
            map.put(BINARY_IN_BOTH_POSITIONS, "{0}", CommonRenderers.STRING)
            map.put(BINARY_ADAPTER_MISSING, "{0}", CommonRenderers.STRING)
            map.put(NOTIFICATION_NON_UNIT, "{0}", CommonRenderers.STRING)
            map.put(KSMETHOD_OUTSIDE_KSSERVICE, "{0}", CommonRenderers.STRING)
            map.put(MULTIPLE_KSSERVICE_SUPERTYPES, "{0}", CommonRenderers.STRING)
            map.put(KSCONTEXT_BINDING_NOT_KSCONTEXTBINDING, "{0}", CommonRenderers.STRING)
            map.put(KSCONTEXT_DUPLICATE_WIRE_KEY, "{0}", CommonRenderers.STRING)
            map.put(SERVICE_TIER_MISMATCH, "{0}", CommonRenderers.STRING)
            map.put(UNSUPPORTED_RESULT_SHAPE, "{0}", CommonRenderers.STRING)
        }
    }
}
