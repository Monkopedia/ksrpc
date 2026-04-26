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
    org.jetbrains.kotlin.fir.symbols.SymbolInternals::class,
    org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess::class
)

package com.monkopedia.ksrpc.plugin.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * FIR-side validation for `@KsContext`-meta-annotated annotations.
 *
 * For every annotation applied to a `@KsService` interface or a `@KsMethod`
 * function whose annotation class itself carries `@KsContext(binding = ...)`:
 *
 *  - The `binding` argument must reference a class that implements
 *    [com.monkopedia.ksrpc.KsContextBinding]. Anything else is rejected with a
 *    diagnostic naming the binding interface.
 *
 *  - Two `@KsContext`-meta-annotated annotations applied (directly or via the
 *    enclosing class) to a single `@KsMethod` function whose bindings declare
 *    the same `wireKey` are rejected. The check inspects each binding's
 *    `wireKey` property initializer when it is a string literal — bindings
 *    whose `wireKey` is computed are excluded from the duplicate check.
 *
 * Code emission for stub-side put-into-context, handler-side
 * read-from-coroutine-context, and per-transport wire formats is intentionally
 * out of scope for this checker — see issues #81 / #82.
 */
internal object KsContextAnnotationChecker {

    internal val KS_CONTEXT_ID =
        ClassId(FqName("com.monkopedia.ksrpc.annotation"), Name.identifier("KsContext"))
    internal val KS_CONTEXT_BINDING_FQ =
        FqName("com.monkopedia.ksrpc.KsContextBinding")
    internal val KS_METHOD_ID =
        ClassId(FqName("com.monkopedia.ksrpc.annotation"), Name.identifier("KsMethod"))
    internal val KS_SERVICE_ID =
        ClassId(FqName("com.monkopedia.ksrpc.annotation"), Name.identifier("KsService"))

    /**
     * Captures one `@KsContext`-meta-annotated annotation found on a
     * declaration: the call-site source (for diagnostic placement), the
     * resolved binding class symbol, and — when statically known — the
     * literal value of the binding's `wireKey` property.
     */
    internal data class BindingUsage(
        val source: KtSourceElement,
        val annotationFqName: FqName,
        val bindingSymbol: FirRegularClassSymbol?,
        val wireKey: String?
    )

    /**
     * Walks the annotations on [declaration]; for each annotation whose class
     * itself carries `@KsContext(binding = X::class)`, returns a [BindingUsage]
     * describing the binding and its statically-known `wireKey` (if any).
     */
    internal fun collectBindingUsages(
        session: FirSession,
        declaration: FirDeclaration
    ): List<BindingUsage> = declaration.annotations.mapNotNull { ann ->
        val annClass = ann.annotationTypeRef.toRegularClassSymbol(session) ?: return@mapNotNull null
        val ksContext = annClass.annotations.firstOrNull { meta ->
            meta.annotationTypeRef.toRegularClassSymbol(session)?.classId == KS_CONTEXT_ID
        } ?: return@mapNotNull null
        val source = ann.source ?: declaration.source ?: return@mapNotNull null
        val bindingSymbol = bindingArgumentClassSymbol(session, ksContext)
        val wireKey = bindingSymbol?.let { staticWireKey(it) }
        BindingUsage(
            source = source,
            annotationFqName = annClass.classId.asSingleFqName(),
            bindingSymbol = bindingSymbol,
            wireKey = wireKey
        )
    }

    /**
     * Resolve the `binding = X::class` argument on a `@KsContext` annotation to
     * the referenced class symbol. Returns null if the argument is missing or
     * the expression isn't a class literal.
     */
    internal fun bindingArgumentClassSymbol(
        session: FirSession,
        ksContext: FirAnnotation
    ): FirRegularClassSymbol? {
        val arg = ksContext.argumentMapping.mapping.values.firstOrNull() as? FirGetClassCall
            ?: return null
        return classFromGetClassCall(session, arg)
    }

    /**
     * Resolve `T` from a `T::class` expression. Tries the argument's own type
     * first; if that's unhelpful, falls back to the first type argument of the
     * `KClass<T>` type the expression as a whole was inferred to.
     */
    private fun classFromGetClassCall(
        session: FirSession,
        getClassCall: FirGetClassCall
    ): FirRegularClassSymbol? {
        coneTypeToRegularClassSymbol(session, getClassCall.argument.resolvedType)
            ?.let { return it }
        val firstArg = getClassCall.resolvedType.typeArguments.firstOrNull()
            as? ConeKotlinTypeProjection ?: return null
        val argType = firstArg.type ?: return null
        return coneTypeToRegularClassSymbol(session, argType)
    }

    /**
     * Resolve a [ConeKotlinType] (assumed to be a class-like type) to its
     * declaring class symbol. Returns null for type parameters, function
     * types, or types whose declaration isn't on the compile classpath.
     */
    private fun coneTypeToRegularClassSymbol(
        session: FirSession,
        type: ConeKotlinType?
    ): FirRegularClassSymbol? {
        val classLike = type as? ConeClassLikeType ?: return null
        val classId = classLike.lookupTag.classId
        return session.symbolProvider.getClassLikeSymbolByClassId(classId)
            as? FirRegularClassSymbol
    }

    /**
     * True iff [classSymbol] (or any of its supertypes, transitively) is the
     * `KsContextBinding` interface.
     */
    internal fun implementsKsContextBinding(
        session: FirSession,
        classSymbol: FirRegularClassSymbol
    ): Boolean {
        if (classSymbol.classId.asSingleFqName() == KS_CONTEXT_BINDING_FQ) return true
        val seen = mutableSetOf<ClassId>()
        val queue = ArrayDeque<FirRegularClassSymbol>()
        queue.addLast(classSymbol)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!seen.add(current.classId)) continue
            for (superType in current.resolvedSuperTypes) {
                val superSym = coneTypeToRegularClassSymbol(session, superType) ?: continue
                if (superSym.classId.asSingleFqName() == KS_CONTEXT_BINDING_FQ) return true
                queue.addLast(superSym)
            }
        }
        return false
    }

    /**
     * Read [bindingSymbol]'s `wireKey` property and, if its initializer is a
     * string literal, return that literal. Returns null if the class is not
     * source-visible (e.g. it lives in another already-compiled module) or the
     * initializer isn't a constant string.
     */
    private fun staticWireKey(bindingSymbol: FirRegularClassSymbol): String? {
        val classDecl: FirRegularClass = try {
            bindingSymbol.fir
        } catch (_: Throwable) {
            return null
        }
        for (decl in classDecl.declarations) {
            if (decl !is FirProperty) continue
            if (decl.name.asString() != "wireKey") continue
            val init = decl.initializer ?: continue
            return (init as? FirLiteralExpression)?.value as? String
        }
        // wireKey isn't declared in this class body — could be inherited or
        // declared via constructor parameter. Skip the duplicate-key check in
        // that case.
        return null
    }

    /**
     * Emit a "binding doesn't implement KsContextBinding" diagnostic for
     * [usage], referencing [usage]'s call-site so the IDE points the squiggle
     * at the offending annotation.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    internal fun reportInvalidBinding(usage: BindingUsage, ownerDescription: String) {
        val bindingFq = usage.bindingSymbol?.classId?.asFqNameString() ?: "<unresolved>"
        reporter.reportOn(
            usage.source,
            KsrpcDiagnostics.KSCONTEXT_BINDING_NOT_KSCONTEXTBINDING,
            "@${usage.annotationFqName.shortName().asString()} on $ownerDescription: " +
                "@KsContext binding `$bindingFq` must implement " +
                "${KS_CONTEXT_BINDING_FQ.asString()}.",
            context
        )
    }
}

/**
 * Class-level checker — validates `@KsContext`-meta-annotated annotations on
 * `@KsService`-tagged classes, plus all annotations on annotation classes
 * carrying the `@KsContext` meta themselves (so `@KsContext(binding = NotABinding::class)`
 * is rejected even if the user forgets to apply the resulting annotation).
 */
internal object KsContextClassChecker :
    FirDeclarationChecker<FirClass>(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        val session = context.session
        val symbol = declaration.symbol

        // Validate annotations applied to a @KsService class.
        val isKsService = symbol.hasAnnotation(KsContextAnnotationChecker.KS_SERVICE_ID, session)
        if (isKsService) {
            val usages = KsContextAnnotationChecker.collectBindingUsages(session, declaration)
            for (usage in usages) {
                val bindingSymbol = usage.bindingSymbol
                val ownerDescription = symbol.classId.asFqNameString()
                if (bindingSymbol == null ||
                    !KsContextAnnotationChecker.implementsKsContextBinding(session, bindingSymbol)
                ) {
                    KsContextAnnotationChecker.reportInvalidBinding(usage, ownerDescription)
                }
            }
        }

        // Validate `@KsContext(binding = ...)` directly on annotation classes.
        // This catches misuse at the declaration site even when the resulting
        // annotation is never applied to a service.
        val ksContext = declaration.annotations.firstOrNull { ann ->
            ann.annotationTypeRef.toRegularClassSymbol(session)?.classId ==
                KsContextAnnotationChecker.KS_CONTEXT_ID
        } ?: return
        val bindingSymbol = KsContextAnnotationChecker.bindingArgumentClassSymbol(
            session,
            ksContext
        )
        val source = ksContext.source ?: declaration.source ?: return
        if (bindingSymbol == null ||
            !KsContextAnnotationChecker.implementsKsContextBinding(session, bindingSymbol)
        ) {
            val bindingFq = bindingSymbol?.classId?.asFqNameString() ?: "<unresolved>"
            reporter.reportOn(
                source,
                KsrpcDiagnostics.KSCONTEXT_BINDING_NOT_KSCONTEXTBINDING,
                "@KsContext on annotation class ${symbol.classId.asFqNameString()}: " +
                    "binding `$bindingFq` must implement " +
                    "${KsContextAnnotationChecker.KS_CONTEXT_BINDING_FQ.asString()}.",
                context
            )
        }
    }
}

/**
 * Function-level checker — validates `@KsContext`-meta-annotated annotations
 * on `@KsMethod` functions, and rejects duplicate `wireKey`s across the union
 * of method-level and enclosing-class-level bindings.
 */
internal object KsContextMethodChecker :
    FirDeclarationChecker<FirNamedFunction>(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) {
        val session = context.session
        val symbol = declaration.symbol
        if (!symbol.hasAnnotation(KsContextAnnotationChecker.KS_METHOD_ID, session)) return

        val containingClass = containingClassSymbol(context)
        val methodFqName = "${containingClass?.classId?.asFqNameString() ?: "<no-class>"}." +
            declaration.name.asString()

        val methodUsages = KsContextAnnotationChecker.collectBindingUsages(session, declaration)
        val classUsages = if (containingClass is FirRegularClassSymbol) {
            KsContextAnnotationChecker.collectBindingUsages(session, containingClass.fir)
        } else {
            emptyList()
        }

        for (usage in methodUsages) {
            val bindingSymbol = usage.bindingSymbol
            if (bindingSymbol == null ||
                !KsContextAnnotationChecker.implementsKsContextBinding(session, bindingSymbol)
            ) {
                KsContextAnnotationChecker.reportInvalidBinding(usage, methodFqName)
            }
        }

        // Duplicate-wireKey: union of class-level + method-level bindings whose
        // wireKey is statically known. Class-level bindings are validated by
        // [KsContextClassChecker]; here we only need them to participate in the
        // duplicate-key calculation.
        val all = classUsages + methodUsages
        val byKey = all.filter { it.wireKey != null }.groupBy { it.wireKey!! }
        for ((wireKey, usages) in byKey) {
            if (usages.size <= 1) continue
            // Report on each of the second-and-later occurrences in the order
            // they appeared. The first occurrence is kept silent — same shape
            // as the duplicate-endpoint diagnostic.
            for (usage in usages.drop(1)) {
                reporter.reportOn(
                    usage.source,
                    KsrpcDiagnostics.KSCONTEXT_DUPLICATE_WIRE_KEY,
                    "$methodFqName: @KsContext bindings produce duplicate wireKey " +
                        "\"$wireKey\" — annotations " +
                        usages.joinToString(", ") { "@${it.annotationFqName.shortName()}" } +
                        " resolve to the same wire-level identifier.",
                    context
                )
            }
        }
    }

    private fun containingClassSymbol(context: CheckerContext): FirClassSymbol<*>? {
        val containers = context.containingDeclarations
        for (i in containers.indices.reversed()) {
            val sym = containers[i]
            if (sym is FirClassSymbol<*>) return sym
        }
        return null
    }
}
