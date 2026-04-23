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

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = KsrpcDiagnosticRenderers
}

@Suppress("ktlint:standard:property-naming")
private object KsrpcDiagnosticRenderers : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap(
        "ksrpc"
    ) { map ->
        with(KsrpcDiagnostics) {
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
        }
    }
}
