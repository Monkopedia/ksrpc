package com.monkopedia.ksrpc.plugin

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.GeneratedByPlugin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

abstract class AbstractTransformerForGenerator(
    protected val context: IrPluginContext
) : IrElementVisitorVoid {
    protected val irFactory = context.irFactory

    abstract fun interestedIn(key: GeneratedDeclarationKey?): Boolean

    abstract fun generateBodyForFunction(
        function: IrSimpleFunction,
        key: GeneratedDeclarationKey?
    ): IrBody?

    abstract fun generateBodyForConstructor(
        constructor: IrConstructor,
        key: GeneratedDeclarationKey?
    ): IrBody?

    abstract fun generateChildrenForClass(
        declaration: IrClass,
        key: GeneratedDeclarationKey?
    ): Collection<IrDeclaration>

    final override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        val origin = declaration.origin
        if (origin !is GeneratedByPlugin || !interestedIn(origin.pluginKey)) {
            visitElement(declaration)
            return
        }
        for (item in generateChildrenForClass(declaration, origin.pluginKey)) {
            declaration.addChild(item)
        }
        declaration.declarations
            .filter { (it as? IrSimpleFunction)?.isFakeOverride == true }
            .forEach { fake ->
                declaration.declarations.remove(fake)
            }
        visitElement(declaration)
    }

    final override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        val origin = declaration.origin
        if (origin !is GeneratedByPlugin || !interestedIn(origin.pluginKey)) {
            visitElement(declaration)
            return
        }
        require(declaration.body == null) {
            "Found body for method ${declaration.name.asString()}"
        }
        declaration.body = generateBodyForFunction(declaration, origin.pluginKey)
    }

    final override fun visitConstructor(declaration: IrConstructor) {
        val origin = declaration.origin
        if (origin !is GeneratedByPlugin ||
            !interestedIn(origin.pluginKey) ||
            declaration.body != null
        ) {
            visitElement(declaration)
            return
        }
        declaration.body = generateBodyForConstructor(declaration, origin.pluginKey)
    }
}
