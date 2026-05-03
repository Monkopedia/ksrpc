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
@file:OptIn(org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess::class)

package com.monkopedia.ksrpc.plugin.fir

import com.monkopedia.ksrpc.plugin.FqConstants
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
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
 *  - `@KsService` on a non-interface declaration (class, object, enum, annotation) (#53, #108)
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

    /** Service capability tiers, ordered by increasing requirement. */
    private enum class Tier(val interfaceName: String) {
        SIMPLE("RpcService"),
        HOST("RpcHostService"),
        BIDI("RpcBidiService")
    }

    private val KS_SERVICE_ID =
        ClassId(FqName("com.monkopedia.ksrpc.annotation"), Name.identifier("KsService"))
    private val FQRPC_SERVICE = FqConstants.FQRPC_SERVICE
    private val FQRPC_HOST_SERVICE = FqConstants.FQRPC_HOST_SERVICE
    private val FQRPC_BIDI_SERVICE = FqConstants.FQRPC_BIDI_SERVICE
    private val FQ_INTROSPECTABLE_RPC_SERVICE = FqConstants.FQ_INTROSPECTABLE_RPC_SERVICE
    private val FLOW_FQ = FqConstants.FLOW

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

        // #0: @KsService is contractually interface-only. Generated code (Stub, companion,
        // Obj) assumes the annotated declaration is an interface. Classes, objects, enums,
        // and annotation classes all produce malformed generated code. Reject them here so
        // IDEs show a clean diagnostic instead of confusing crashes downstream (#53, #108).
        if (declaration.classKind != ClassKind.INTERFACE) {
            val simpleName = symbol.classId.shortClassName.asString()
            reporter.reportOn(
                source,
                KsrpcDiagnostics.NOT_INTERFACE,
                "@KsService can only be applied to interfaces. Change `$simpleName` to an " +
                    "interface.",
                context
            )
            // Bail out early — downstream validations (supertypes, type parameters) assume
            // the declaration is an interface and would emit noisy secondary diagnostics.
            return
        }

        val allSuperFqNames = collectAllSuperFqNames(declaration, session)
        val hasRpcServiceSuper = allSuperFqNames.contains(FQRPC_SERVICE)
        val hasIntrospectableSuper = allSuperFqNames.contains(FQ_INTROSPECTABLE_RPC_SERVICE)

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

        // #4: service tier validation — check that the declared tier is sufficient
        // for the methods declared on this service.
        checkServiceTier(declaration, allSuperFqNames, session, source, context, reporter)
    }

    /**
     * Determine the tier that [declaration] explicitly extends.
     */
    private fun declaredTier(allSuperFqNames: Set<FqName>): Tier = when {
        allSuperFqNames.contains(FQRPC_BIDI_SERVICE) -> Tier.BIDI
        allSuperFqNames.contains(FQRPC_HOST_SERVICE) -> Tier.HOST
        else -> Tier.SIMPLE
    }

    /**
     * Determine the tier of a type that is itself a `@KsService`.
     */
    @OptIn(SymbolInternals::class)
    private fun tierOfService(
        serviceSymbol: FirClassSymbol<*>,
        session: FirSession
    ): Tier {
        val superFqNames = collectAllSuperFqNames(serviceSymbol.fir, session)
        return declaredTier(superFqNames)
    }

    /**
     * Check whether a [ConeKotlinType] resolves to a `Flow<T>`.
     */
    private fun isFlowType(type: ConeKotlinType): Boolean {
        val classId = type.classId ?: return false
        return classId.asSingleFqName() == FLOW_FQ
    }

    /**
     * Check whether a [ConeKotlinType] resolves to a `@KsService` type, and
     * if so return its symbol.
     */
    private fun asKsServiceSymbol(
        type: ConeKotlinType,
        session: FirSession
    ): FirClassSymbol<*>? {
        val classId = type.classId ?: return null
        val symbol = session.symbolProvider.getClassLikeSymbolByClassId(classId)
            as? FirClassSymbol<*> ?: return null
        return if (symbol.hasAnnotation(KS_SERVICE_ID, session)) symbol else null
    }

    /**
     * Compute the required tier and compare against the declared tier. Report
     * a diagnostic if the required tier exceeds the declared tier.
     */
    @OptIn(SymbolInternals::class)
    private fun checkServiceTier(
        declaration: FirClass,
        allSuperFqNames: Set<FqName>,
        session: FirSession,
        source: KtSourceElement,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        val declared = declaredTier(allSuperFqNames)
        val selfName = declaration.symbol.classId.shortClassName.asString()
        val ksMethodId =
            ClassId(FqName("com.monkopedia.ksrpc.annotation"), Name.identifier("KsMethod"))

        for (member in declaration.declarations) {
            val function = member as? FirNamedFunction ?: continue
            if (!function.symbol.hasAnnotation(ksMethodId, session)) continue

            val methodName = function.name.asString()

            // Check return type
            val returnType = function.returnTypeRef.coneType
            var requiredForMethod = Tier.SIMPLE

            if (isFlowType(returnType)) {
                // Flow<T> is backed by KsFlowService<T> which is RpcBidiService
                requiredForMethod = Tier.BIDI
            } else {
                val returnServiceSymbol = asKsServiceSymbol(returnType, session)
                if (returnServiceSymbol != null) {
                    // Returning a sub-service requires at least HOST
                    val subTier = tierOfService(returnServiceSymbol, session)
                    requiredForMethod = maxOf(requiredForMethod, Tier.HOST)
                    // If the sub-service itself requires BIDI, propagate
                    requiredForMethod = maxOf(requiredForMethod, subTier)
                }
            }

            // Check input parameters (skip dispatch receiver)
            for (param in function.valueParameters) {
                val paramType = param.returnTypeRef.coneType
                if (isFlowType(paramType)) {
                    requiredForMethod = Tier.BIDI
                } else {
                    val paramServiceSymbol = asKsServiceSymbol(paramType, session)
                    if (paramServiceSymbol != null) {
                        // Accepting a sub-service as input requires BIDI
                        requiredForMethod = Tier.BIDI
                    }
                }
            }

            if (requiredForMethod > declared) {
                val returnDesc = describeMethodRequirement(
                    returnType, function.valueParameters.map { it.returnTypeRef.coneType },
                    session
                )
                reporter.reportOn(
                    source,
                    KsrpcDiagnostics.SERVICE_TIER_MISMATCH,
                    "$selfName extends ${declared.interfaceName} but method " +
                        "'$methodName' $returnDesc. Change " +
                        "'${declared.interfaceName}' to " +
                        "'${requiredForMethod.interfaceName}'.",
                    context
                )
            }
        }
    }

    /**
     * Build a human-readable description of why a method requires a higher tier.
     */
    private fun describeMethodRequirement(
        returnType: ConeKotlinType,
        paramTypes: List<ConeKotlinType>,
        session: FirSession
    ): String {
        if (isFlowType(returnType)) {
            return "returns Flow<T> (a RpcBidiService)"
        }
        for (pt in paramTypes) {
            if (isFlowType(pt)) {
                return "accepts Flow<T> (a RpcBidiService)"
            }
            val sym = asKsServiceSymbol(pt, session)
            if (sym != null) {
                val name = sym.classId.shortClassName.asString()
                return "accepts $name (a @KsService input, requires RpcBidiService)"
            }
        }
        val retSym = asKsServiceSymbol(returnType, session)
        if (retSym != null) {
            val name = retSym.classId.shortClassName.asString()
            val tier = tierOfService(retSym, session)
            return "returns $name (a ${tier.interfaceName})"
        }
        return "requires a higher service tier"
    }

    /**
     * Collect FQ names of all transitive supertypes of [declaration], including direct ones.
     */
    @OptIn(SymbolInternals::class)
    private fun collectAllSuperFqNames(
        declaration: FirClass,
        session: FirSession
    ): Set<FqName> {
        val result = mutableSetOf<FqName>()
        val queue = ArrayDeque<FirClass>()
        queue.add(declaration)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (superRef in current.superTypeRefs) {
                val superSymbol = superRef.toRegularClassSymbol(session) ?: continue
                val fqName = superSymbol.classId.asSingleFqName()
                if (result.add(fqName)) {
                    superSymbol.fir.let { queue.add(it) }
                }
            }
        }
        return result
    }
}
