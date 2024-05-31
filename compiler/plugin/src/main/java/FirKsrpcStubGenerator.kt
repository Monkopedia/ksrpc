package com.monkopedia.ksrpc.plugin

import com.monkopedia.ksrpc.plugin.FqConstants.KS_METHOD
import com.monkopedia.ksrpc.plugin.FqConstants.KS_SERVICE
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.isExpect
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.ClassBuildingContext
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class FirKsrpcStubGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {

    private val predicates =
        listOf(KS_METHOD, KS_SERVICE).map { LookupPredicate.create { annotated(it) } }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(predicates)
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        if (name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) return null
        val classId = owner.classId.createNestedClassId(STUB)
        return ClassBuildingContext(
            session,
            Key(owner.classId.asFqNameString()),
            owner,
            classId,
            ClassKind.CLASS
        ).apply {
            modality = Modality.FINAL
            status {
                isExpect = owner.isExpect
            }
            superType(owner.defaultType())
            superType(RPC_SERVICE.createConeType(session))
        }.build().symbol
    }

    private fun checkOwnerKey(context: MemberGenerationContext): Key? {
        return (context.owner.origin as? FirDeclarationOrigin.Plugin)?.key as? Key
    }

    override fun generateConstructors(
        context: MemberGenerationContext
    ): List<FirConstructorSymbol> {
        val ownerKey = checkOwnerKey(context) ?: return emptyList()
        val constructor = createConstructor(context.owner, ownerKey, isPrimary = true) {
            valueParameter(
                CHANNEL,
                SERIALIZED_SERVICE.createConeType(session, arrayOf(ConeStarProjection))
            )
        }
        return listOf(constructor.symbol)
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        if (classSymbol.classKind != ClassKind.CLASS) return emptySet()
        (classSymbol as? FirRegularClassSymbol)
            ?.classId
            ?.takeIf { it.isNestedClass }
            ?.takeIf { it.shortClassName == STUB }
            ?: return emptySet()
        val origin = classSymbol.origin as? FirDeclarationOrigin.Plugin
        return if (origin?.key is Key) {
            setOf(SpecialNames.INIT)
        } else {
            setOf()
        }
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> {
        return if (session.predicateBasedProvider.matches(predicates, classSymbol)) {
            setOf(STUB)
        } else {
            emptySet()
        }
    }

    data class Key(val target: String) : GeneratedDeclarationKey() {
        override fun toString(): String {
            return "KsrpcStub($target)"
        }
    }

    companion object {
        val STUB = Name.identifier("Stub")
        val CHANNEL = Name.identifier("channel")
        val RPC_SERVICE = ClassId(FqName("com.monkopedia.ksrpc"), Name.identifier("RpcService"))
        val SERIALIZED_SERVICE =
            ClassId(FqName("com.monkopedia.ksrpc.channels"), Name.identifier("SerializedService"))
    }
}
