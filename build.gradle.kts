/*
 * Copyright 2020 Jason Monk
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

buildscript {
    repositories {
        mavenCentral()
        jcenter()
        mavenLocal()
    }
    dependencies {
        classpath("com.monkopedia:ksrpc-gradle-plugin:0.4.1")
    }
    extra["kotlin_plugin_id"] = "com.monkopedia.ksrpc.plugin"
}
plugins {
    kotlin("multiplatform") version "1.5.21" apply false
    kotlin("plugin.serialization") version "1.5.21" apply false
    id("com.github.autostyle") version "3.1"
    id("org.jetbrains.dokka") version "1.4.10.2" apply false

    id("com.github.gmazzo.buildconfig") version "2.0.2" apply false
}

group = "com.monkopedia"

allprojects {
    repositories {
        jcenter()
        mavenLocal()
        mavenCentral()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-dev/")
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap/")
        maven(url = "https://kotlinx.bintray.com/kotlinx/")
        maven(url = "https://dl.bintray.com/kotlin/dokka")
    }
}

subprojects {
    plugins.apply("com.github.autostyle")
    autostyle {
        kotlinGradle {
            // Since kotlin doesn't pick up on multi platform projects
            filter.include("**/*.kt")
            ktlint("0.39.0") {
                userData(mapOf("android" to "true"))
            }

            licenseHeader(
                """
                |Copyright 2021 Jason Monk
                |
                |Licensed under the Apache License, Version 2.0 (the "License");
                |you may not use this file except in compliance with the License.
                |You may obtain a copy of the License at
                |
                |    https://www.apache.org/licenses/LICENSE-2.0
                |
                |Unless required by applicable law or agreed to in writing, software
                |distributed under the License is distributed on an "AS IS" BASIS,
                |WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                |See the License for the specific language governing permissions and
                |limitations under the License.""".trimMargin()
            )
        }
    }
}
