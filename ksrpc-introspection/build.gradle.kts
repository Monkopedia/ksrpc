import com.monkopedia.ksrpc.local.ksrpcModule

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.atomicfu)
}

ksrpcModule()
