/**
 * Copyright (C) 2022 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.plugin

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.synthetics.SyntheticClassOrObjectDescriptor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.TypeAttributes
import org.jetbrains.kotlin.types.TypeProjectionImpl
import org.jetbrains.kotlin.types.Variance

class KsrpcSyntheticResolveExtension(private val messageCollector: MessageCollector) :
    SyntheticResolveExtension {
    private val service = FqName("com.monkopedia.ksrpc.annotation.KsService")
    private fun Annotated.isKsService() = annotations.hasAnnotation(service)

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? {
        return when {
            thisDescriptor.kind == ClassKind.INTERFACE &&
                thisDescriptor.isKsService() -> Name.identifier("Companion")
            else -> return null
        }
    }

    override fun getPossibleSyntheticNestedClassNames(
        thisDescriptor: ClassDescriptor
    ): List<Name>? {
        if (thisDescriptor.isKsService()) {
            return listOf(Name.identifier("Companion"))
        }
        return emptyList()
    }

    override fun generateSyntheticClasses(
        thisDescriptor: ClassDescriptor,
        name: Name,
        ctx: LazyClassContext,
        declarationProvider: ClassMemberDeclarationProvider,
        result: MutableSet<ClassDescriptor>
    ) {
        if (thisDescriptor.kind == ClassKind.INTERFACE &&
            thisDescriptor.isKsService() &&
            name == Name.identifier("Companion")
        ) {
            result.add(addCompanionRpcObject(thisDescriptor, declarationProvider, ctx))
        }
    }

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> {
        if (thisDescriptor.isCompanionObject && thisDescriptor.parents.any { it.isKsService() }) {
            return listOf(
                Name.identifier(FqConstants.FIND_ENDPOINT),
                Name.identifier(FqConstants.CREATE_STUB)
            )
        }
        return super.getSyntheticFunctionNames(thisDescriptor)
    }

    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        if (thisDescriptor.isCompanionObject && thisDescriptor.parents.any { it.isKsService() }) {
            when (name.asString()) {
                FqConstants.FIND_ENDPOINT -> {
                    result.add(generateFindEndpoint(thisDescriptor, bindingContext, fromSupertypes))
                }
                FqConstants.CREATE_STUB -> {
                    result.add(generateCreateStub(thisDescriptor, bindingContext, fromSupertypes))
                }
            }
        }
        super.generateSyntheticMethods(
            thisDescriptor,
            name,
            bindingContext,
            fromSupertypes,
            result
        )
    }

    private fun generateCreateStub(
        thisDescriptor: ClassDescriptor,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>
    ): SimpleFunctionDescriptor {
        return fromSupertypes.find {
            it.name.asString() == FqConstants.CREATE_STUB
        }?.newCopyBuilder()?.apply {
            setOwner(thisDescriptor)
            setModality(Modality.FINAL)
            setKind(CallableMemberDescriptor.Kind.SYNTHESIZED)
            setDispatchReceiverParameter(thisDescriptor.thisAsReceiverParameter)
        }?.build()
            ?: error(
                "Can't find base ${
                FqConstants.CREATE_STUB} within ${thisDescriptor.fqNameSafe.asString()}"
            )
    }

    private fun generateFindEndpoint(
        thisDescriptor: ClassDescriptor,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>
    ): SimpleFunctionDescriptor {
        return fromSupertypes.find {
            it.name.asString() == FqConstants.FIND_ENDPOINT
        }?.newCopyBuilder()?.apply {
            setOwner(thisDescriptor)
            setModality(Modality.FINAL)
            setKind(CallableMemberDescriptor.Kind.SYNTHESIZED)
            setDispatchReceiverParameter(thisDescriptor.thisAsReceiverParameter)
        }?.build()
            ?: error(
                "Can't find base ${
                FqConstants.FIND_ENDPOINT} within ${thisDescriptor.fqNameSafe.asString()}"
            )
    }

    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> {
        if (thisDescriptor.isKsService()) {
            return listOf(Name.identifier("Companion"))
        }
        return super.getSyntheticNestedClassNames(thisDescriptor)
    }

    private fun addCompanionRpcObject(
        interfaceDesc: ClassDescriptor,
        declarationProvider: ClassMemberDeclarationProvider,
        ctx: LazyClassContext
    ): ClassDescriptor {
        val interfaceDecl = declarationProvider.correspondingClassOrObject!!
        val scope = ctx.declarationScopeProvider.getResolutionScopeForDeclaration(
            declarationProvider.ownerInfo!!.scopeAnchor
        )

        // if there are some properties, there will be a public synthetic constructor
        // at the codegen phase
        val primaryCtorVisibility = DescriptorVisibilities.PUBLIC

        val descriptor = SyntheticClassOrObjectDescriptor(
            ctx,
            interfaceDecl,
            interfaceDesc,
            Name.identifier("Companion"),
            interfaceDesc.source,
            scope,
            Modality.FINAL,
            DescriptorVisibilities.PUBLIC,
            Annotations.EMPTY,
            primaryCtorVisibility,
            ClassKind.OBJECT,
            false
        )
        descriptor.initialize()
        return descriptor
    }

    override fun addSyntheticSupertypes(
        thisDescriptor: ClassDescriptor,
        supertypes: MutableList<KotlinType>
    ) {
        if (!thisDescriptor.isCompanionObject) return
        if (thisDescriptor !is SyntheticClassOrObjectDescriptor) return
        val target = thisDescriptor.parents.filterIsInstance<ClassDescriptor>().find {
            it.kind == ClassKind.INTERFACE && it.isKsService()
        } ?: return
        val projectionType = Variance.INVARIANT
        val types = listOf(
            TypeProjectionImpl(
                projectionType,
                KotlinTypeFactory.simpleNotNullType(TypeAttributes.Empty, target, emptyList())
            )
        )
        val descriptor = target.module.findClassAcrossModuleDependencies(
            ClassId.topLevel(FqName("com.monkopedia.ksrpc.RpcObject"))
        )
            ?: error("Can't find RpcObject within module ${target.name.asString()}")
        supertypes.add(KotlinTypeFactory.simpleNotNullType(TypeAttributes.Empty, descriptor, types))
    }
}
