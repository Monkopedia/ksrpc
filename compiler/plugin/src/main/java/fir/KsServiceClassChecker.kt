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

import com.monkopedia.ksrpc.plugin.FqConstants
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

/**
 * FIR-side checker for `@KsService`-annotated classes. Covers the
 * user-reachable diagnostics previously emitted in IR by
 * `KsrpcIrGenerationExtension.validateClass` and
 * `ServiceClass.SubclassVisitor`:
 *
 *  - `@KsService` on a subtype of another `@KsService` (issue #45)
 *  - `@KsService` class without `RpcService` (or `IntrospectableRpcService`) supertype
 *  - Class-level variant type parameter (`out T` / `in T`) — not yet supported
 *  - Multiple `@KsService` supertypes on a single class
 *
 * Emitting at FIR phase attaches the diagnostic to the offending
 * `KtSourceElement` (the class name, a type-parameter declaration, ...) which
 * enables IDE red-squiggle support (see issue #65).
 */
internal object KsServiceClassChecker :
    FirDeclarationChecker<FirClass>(MppCheckerKind.Common) {

    private val KS_SERVICE_ID =
        ClassId(FqName("com.monkopedia.ksrpc.annotation"), Name.identifier("KsService"))
    private val FQRPC_SERVICE = FqConstants.FQRPC_SERVICE
    private val FQ_INTROSPECTABLE_RPC_SERVICE = FqConstants.FQ_INTROSPECTABLE_RPC_SERVICE

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        val session = context.session
        val symbol = declaration.symbol

        val isKsService = symbol.hasAnnotation(KS_SERVICE_ID, session)
        // Count @KsService supertypes even on classes without @KsService themselves,
        // because the legacy ServiceClass.SubclassVisitor reported "multiple @KsService
        // super types" on any implementing class. But the primary rejection for
        // @KsService-on-@KsService-subtype is only emitted on @KsService-annotated
        // classes.
        val ksServiceSuperSymbols = declaration.superTypeRefs
            .asSequence()
            .mapNotNull { it.toRegularClassSymbol(session) }
            .filter { it.hasAnnotation(KS_SERVICE_ID, session) }
            .toList()

        if (isKsService) {
            checkKsServiceClass(declaration, symbol, ksServiceSuperSymbols, context, reporter)
        } else {
            // Non-@KsService classes are only interesting if they implement multiple
            // @KsService interfaces (legacy SubclassVisitor behaviour).
            if (ksServiceSuperSymbols.size > 1) {
                val source = declaration.source ?: return
                reporter.reportOn(
                    source,
                    KsrpcDiagnostics.MULTIPLE_KSSERVICE_SUPERTYPES,
                    "${symbol.classId.asFqNameString()} has " +
                        "multiple @KsService super types, which is not supported.",
                    context
                )
            }
        }
    }

    private fun checkKsServiceClass(
        declaration: FirClass,
        symbol: FirClassSymbol<*>,
        ksServiceSuperSymbols: List<FirClassSymbol<*>>,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        val source = declaration.source ?: return
        val session = context.session
        val superTypeFqNames = declaration.superTypeRefs
            .mapNotNull { it.toRegularClassSymbol(session)?.classId?.asSingleFqName() }
        val hasRpcServiceSuper = superTypeFqNames.any { it == FQRPC_SERVICE }
        val hasIntrospectableSuper = superTypeFqNames.any { it == FQ_INTROSPECTABLE_RPC_SERVICE }

        val ksServiceSuper = ksServiceSuperSymbols.firstOrNull()
        if (ksServiceSuper != null) {
            // #1: @KsService on a subtype of another @KsService — reject with the
            // existing wording so the existing test suite keeps asserting on the
            // same message.
            val selfFqName = symbol.classId.asFqNameString()
            val superFqName = ksServiceSuper.classId.asFqNameString()
            val selfShortName = symbol.classId.shortClassName.asString()
            reporter.reportOn(
                source,
                KsrpcDiagnostics.KSSERVICE_SUBTYPE_OF_KSSERVICE,
                "@KsService cannot be applied to $selfFqName because " +
                    "its supertype $superFqName is already " +
                    "@KsService. Remove @KsService from " +
                    "$selfFqName; rpcObject<" +
                    "$selfShortName>() will find the parent's companion " +
                    "automatically.",
                context
            )
        }
        if (!hasRpcServiceSuper && !hasIntrospectableSuper && ksServiceSuper == null) {
            // #2
            reporter.reportOn(
                source,
                KsrpcDiagnostics.NOT_RPC_SERVICE,
                "${symbol.classId.asFqNameString()} does not extend ${FQRPC_SERVICE.asString()}",
                context
            )
        }

        // #3: class-level variant type parameters.
        if (declaration is FirRegularClass) {
            for (tpRef in declaration.typeParameters) {
                val tp = tpRef as? FirTypeParameter ?: continue
                if (tp.variance != Variance.INVARIANT) {
                    val tpSource = tp.source ?: source
                    reporter.reportOn(
                        tpSource,
                        KsrpcDiagnostics.VARIANT_TYPE_PARAMETER,
                        "${symbol.classId.asFqNameString()}: type parameter " +
                            "${tp.name.asString()} must be invariant " +
                            "(got ${tp.variance.label})",
                        context
                    )
                }
            }
        }
    }
}
