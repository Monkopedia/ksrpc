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
@file:OptIn(
    org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess::class,
    org.jetbrains.kotlin.fir.symbols.SymbolInternals::class
)

package com.monkopedia.ksrpc.plugin.fir

import com.monkopedia.ksrpc.plugin.FqConstants
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * FIR-side checker for `@KsMethod`-annotated functions. Covers the
 * user-reachable diagnostics previously emitted in IR by
 * `KsrpcIrGenerationExtension.validateMethod`, `validateNotification`, and
 * `ServiceClass.visitSimpleFunction`:
 *
 *  - `@KsMethod` function with type parameters
 *  - `@KsMethod` function with more than one non-dispatch parameter
 *  - Unparseable or duplicate `@KsMethod("...")` endpoint
 *  - Binary types (`ByteReadChannel` / `Source` / `BufferedSource`) in both
 *    input and output position
 *  - Binary adapter module missing on compile classpath
 *  - `@KsNotification` on non-`Unit`-returning method
 *  - `@KsMethod` declared outside a `@KsService` interface
 *
 * Duplicate-endpoint detection runs over the siblings in the enclosing class
 * rather than per-function, so it fires only on the *second* declaration that
 * reuses the endpoint string — preserving the existing IR-phase behaviour.
 */
internal object KsMethodFunctionChecker :
    FirDeclarationChecker<FirNamedFunction>(MppCheckerKind.Common) {

    private val KS_METHOD_ID =
        ClassId(FqName("com.monkopedia.ksrpc.annotation"), Name.identifier("KsMethod"))
    private val KS_SERVICE_ID =
        ClassId(FqName("com.monkopedia.ksrpc.annotation"), Name.identifier("KsService"))
    private val KS_NOTIFICATION_ID =
        ClassId(FqName("com.monkopedia.ksrpc.annotation"), Name.identifier("KsNotification"))
    private val FQ_INTROSPECTABLE_RPC_SERVICE =
        FqConstants.FQ_INTROSPECTABLE_RPC_SERVICE

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) {
        val session = context.session
        val symbol = declaration.symbol
        if (!symbol.hasAnnotation(KS_METHOD_ID, session)) return

        val containingClassSymbol = containingClassSymbol(context)
        val containingIsKsService =
            containingClassSymbol != null &&
                containingClassSymbol.hasAnnotation(KS_SERVICE_ID, session)

        // #9: @KsMethod outside a @KsService interface. Match the existing
        // escape hatch — methods declared on `IntrospectableRpcService` are
        // allowed because ksrpc's own interface carries @KsMethod annotations
        // that the plugin should not reject.
        if (!containingIsKsService) {
            val containingFqName = containingClassSymbol?.classId?.asSingleFqName()
            if (containingFqName != FQ_INTROSPECTABLE_RPC_SERVICE) {
                val source = declaration.source ?: return
                val receiverFqName =
                    containingFqName?.asString() ?: "no enclosing class"
                reporter.reportOn(
                    source,
                    KsrpcDiagnostics.KSMETHOD_OUTSIDE_KSSERVICE,
                    "@KsMethod can only be applied to functions declared inside a " +
                        "@KsService interface. ${declaration.name.asString()} is " +
                        "declared on $receiverFqName.",
                    context
                )
            }
            return
        }

        val containingFqName = containingClassSymbol.classId.asFqNameString()
        val methodName = declaration.name.asString()
        val source = declaration.source ?: return

        // #4: method-level type parameters.
        if (declaration.typeParameters.isNotEmpty()) {
            reporter.reportOn(
                source,
                KsrpcDiagnostics.METHOD_TYPE_PARAMETERS,
                "$containingFqName.$methodName cannot have type parameters",
                context
            )
        }

        // #5: more than one non-dispatch parameter. FIR's valueParameters list
        // excludes the dispatch receiver, matching the IR-phase filter.
        if (declaration.valueParameters.size > 1) {
            reporter.reportOn(
                source,
                KsrpcDiagnostics.METHOD_TOO_MANY_PARAMETERS,
                "$containingFqName.$methodName cannot have more than 1 parameter",
                context
            )
        }

        // #6: endpoint name — unparseable or duplicate.
        val ksMethodAnnotation = declaration.annotations.firstOrNull { ann ->
            ann.annotationTypeRef.toRegularClassSymbol(session)?.classId == KS_METHOD_ID
        }
        val firstArg = ksMethodAnnotation?.argumentMapping?.mapping?.values?.firstOrNull()
        val endpointName = (firstArg as? FirLiteralExpression)?.value as? String
        if (endpointName == null) {
            reporter.reportOn(
                source,
                KsrpcDiagnostics.UNPARSEABLE_ENDPOINT,
                "$containingFqName.$methodName: could not parse annotation argument $firstArg",
                context
            )
        } else {
            // Walk the enclosing class's declared @KsMethod functions in source order
            // and fire the diagnostic only on the second and subsequent declarations
            // that reuse the endpoint string. This matches the IR-phase behaviour of
            // `validateMethod`, which fires on insertion collisions against a mutable
            // seen-set accumulated while iterating the methods in declaration order.
            val classSym = containingClassSymbol as? FirRegularClassSymbol
            val earlierUsesName = classSym != null &&
                classEndpointUsedBefore(session, classSym, declaration, endpointName)
            if (earlierUsesName) {
                reporter.reportOn(
                    source,
                    KsrpcDiagnostics.DUPLICATE_ENDPOINT,
                    "$containingFqName.$methodName: cannot use endpoint $endpointName, " +
                        "it has already been used",
                    context
                )
            }
        }

        // #7: binary-type shape. Use the FIR classId on the parameter/return ConeKotlinType.
        val inputType = declaration.valueParameters.firstOrNull()
            ?.returnTypeRef?.coneType?.classId?.asSingleFqName()
        val outputType = declaration.returnTypeRef.coneType.classId?.asSingleFqName()
        val binaryUserFqs = BinaryAdapterRegistry.userFqNames()
        if (inputType in binaryUserFqs && outputType in binaryUserFqs) {
            reporter.reportOn(
                source,
                KsrpcDiagnostics.BINARY_IN_BOTH_POSITIONS,
                "$containingFqName.$methodName: binary streams not yet supported for both " +
                    "input and output",
                context
            )
        }
        for (sideFq in listOfNotNull(inputType, outputType)) {
            val adapter =
                BinaryAdapterRegistry.find(sideFq) ?: continue
            val transformerPresent = session.symbolProvider
                .getClassLikeSymbolByClassId(adapter.transformerClassId) != null
            if (!transformerPresent) {
                reporter.reportOn(
                    source,
                    KsrpcDiagnostics.BINARY_ADAPTER_MISSING,
                    "@KsMethod `$containingFqName.$methodName` uses " +
                        "`${adapter.userFqName.asString()}` but the adapter module is not " +
                        "on the compile classpath. Add " +
                        "`implementation(\"com.monkopedia:${adapter.moduleHint}\")` to " +
                        "your dependencies.",
                    context
                )
            }
        }

        // #8: @KsNotification on non-Unit return.
        if (symbol.hasAnnotation(KS_NOTIFICATION_ID, session)) {
            val returnFq = declaration.returnTypeRef.coneType.classId?.asSingleFqName()
            if (returnFq?.asString() != "kotlin.Unit") {
                reporter.reportOn(
                    source,
                    KsrpcDiagnostics.NOTIFICATION_NON_UNIT,
                    "$containingFqName.$methodName: @KsNotification methods must return Unit, " +
                        "but returns $returnFq",
                    context
                )
            }
        }
    }

    /**
     * True iff some declared function on [classSymbol] that occurs *before*
     * [currentFunction] in the source file already declares [endpoint].
     *
     * This mirrors the IR-phase `validateMethod` loop, which walks methods in
     * declaration order and reports the diagnostic on the second and following
     * occurrences of the same endpoint string. The seen-set approach can't
     * easily be mapped to FIR's per-function checker invocations (which are
     * triggered independently), so we scan the enclosing class instead.
     */
    private fun classEndpointUsedBefore(
        session: org.jetbrains.kotlin.fir.FirSession,
        classSymbol: FirRegularClassSymbol,
        currentFunction: FirNamedFunction,
        endpoint: String
    ): Boolean {
        val currentStart = currentFunction.source?.startOffset ?: return false
        val classDecl: FirRegularClass = classSymbol.fir
        for (decl in classDecl.declarations) {
            if (decl === currentFunction) continue
            if (decl !is FirNamedFunction) continue
            val declStart = decl.source?.startOffset ?: continue
            if (declStart >= currentStart) continue
            val ksMethod = decl.annotations.firstOrNull { ann ->
                ann.annotationTypeRef.toRegularClassSymbol(session)?.classId == KS_METHOD_ID
            } ?: continue
            val value = ksMethod.argumentMapping.mapping.values.firstOrNull()
            val other = (value as? FirLiteralExpression)?.value as? String ?: continue
            if (other == endpoint) return true
        }
        return false
    }

    private fun containingClassSymbol(context: CheckerContext): FirClassSymbol<*>? {
        // FIR's checker context pushes the containing symbols. The innermost
        // containing class is the last FirClassSymbol we pushed through, which
        // for a member function is the owning class. This matches the IR
        // `dispatchReceiverParameter?.type` check at IR phase.
        val containers = context.containingDeclarations
        for (i in containers.indices.reversed()) {
            val sym = containers[i]
            if (sym is FirClassSymbol<*> && sym.classKind != ClassKind.ENUM_ENTRY) {
                return sym
            }
        }
        return null
    }
}
