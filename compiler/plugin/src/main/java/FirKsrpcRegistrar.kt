package com.monkopedia.ksrpc.plugin

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class FirKsrpcRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::FirKsrpcStubGenerator
        +::FirCompanionDeclarationGenerator
    }
}