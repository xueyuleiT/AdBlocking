/**
 * amagi and lisonge <https://github.com/gkd-kit/gkd>
 * Copyright (C) 2024 amagi and lisonge
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
fun String.runCommand(): String {
    val process = ProcessBuilder(split(" "))
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        error("Command failed with exit code $exitCode: $output")
    }
    return output
}

data class GitInfo(
    val commitId: String,
    val commitTime: String,
    val tagName: String?,
)

val gitInfo by lazy {
    (try {
        GitInfo(
            commitId = "git rev-parse HEAD".runCommand(),
            commitTime = "git log -1 --format=%ct".runCommand(),
            tagName = try {
                "git describe --tags --exact-match".runCommand()
            } catch (_: Exception) {
                println("app: current git commit is not a tag")
                null
            },
        )
    } catch (_: Exception) {
        println("app: git is not available")
        null
    }).apply {
        println("app: $this")
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.rikka.refine)
}

android {
    namespace = "com.ps.gkd"
    compileSdk = project.properties["android_compileSdk"].toString().toInt()
    buildToolsVersion = project.properties["android_buildToolsVersion"].toString()

    defaultConfig {
        minSdk = project.properties["android_minSdk"].toString().toInt()
        targetSdk = project.properties["android_targetSdk"].toString().toInt()

        applicationId = "com.ps.gkd"
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        resourceConfigurations.addAll(listOf("zh", "en"))
        ndk {
            // noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        manifestPlaceholders["commitId"] = gitInfo?.commitId ?: "unknown"
        manifestPlaceholders["commitTime"] = gitInfo?.commitTime?.let { it + "000" } ?: "0"
    }

    lint {}

    buildFeatures {
        compose = true
        aidl = true
    }

    val currentSigning = if (project.hasProperty("GKD_STORE_FILE")) {
        signingConfigs.create("release") {
            storeFile = file(project.properties["GKD_STORE_FILE"] as String)
            storePassword = project.properties["GKD_STORE_PASSWORD"] as String
            keyAlias = project.properties["GKD_KEY_ALIAS"] as String
            keyPassword = project.properties["GKD_KEY_PASSWORD"] as String
        }
    } else {
        signingConfigs.getByName("debug")
    }

    buildTypes {
        all {
            signingConfig = currentSigning
//            if (gitInfo?.tagName == null) {
//                versionNameSuffix = "-${gitInfo?.commitId?.substring(0, 7) ?: "unknown"}"
//            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            setProguardFiles(
                listOf(
                    // /sdk/tools/proguard/proguard-android-optimize.txt
                    getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
                )
            )
        }
        debug {
//            applicationIdSuffix = ".debug"

            // add "debug" suffix
//            listOf(
//                "app_name" to "去广告神器",
//                "capture_snapshot" to "捕获快照",
//                "import_data" to "导入数据",
//                "http_server" to "HTTP服务",
//                "floating_button" to "悬浮按钮",
//            ).forEach {
//                resValue("string", it.first, it.second + "-debug")
//            }
        }
    }
    productFlavors {
        flavorDimensions += "channel"
        create("AdBlocking") {
            isDefault = true
            manifestPlaceholders["updateEnabled"] = false
        }
        create("foss") {
            manifestPlaceholders["updateEnabled"] = false
        }
        all {
            dimension = flavorDimensionList.first()
            manifestPlaceholders["channel"] = name
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.majorVersion
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
        )
    }
    dependenciesInfo.includeInApk = false
    packagingOptions.resources.excludes += setOf(
        // https://github.com/Kotlin/kotlinx.coroutines/issues/2023
        "META-INF/**", "**/attach_hotspot_windows.dll",

        "**.properties", "**.bin", "**/*.proto",
        "**/kotlin-tooling-metadata.json",

        // ktor
        "**/custom.config.conf",
        "**/custom.config.yaml",
    )
}

// https://developer.android.com/jetpack/androidx/releases/room?hl=zh-cn#compiler-options
room {
    schemaDirectory("$projectDir/schemas")
}
ksp {
    arg("room.generateKotlin", "true")
}

configurations.configureEach {
    //    https://github.com/Kotlin/kotlinx.coroutines/issues/2023
    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-debug")
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.addAll(
        project.layout.projectDirectory.file("../stability_config.conf"),
    )
}

dependencies {
    implementation(libs.kotlin.stdlib)

    implementation(project(mapOf("path" to ":selector")))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.graphics)
    implementation(libs.compose.icons)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
    androidTestImplementation(libs.compose.junit4)

    implementation(libs.compose.activity)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)

    compileOnly(project(mapOf("path" to ":hidden_api")))
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    implementation(libs.lsposed.hiddenapibypass)

    implementation(libs.tencent.mmkv)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.google.accompanist.drawablepainter)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.utilcodex)
    implementation(libs.activityResultLauncher)
    implementation(libs.floatingBubbleView)

    implementation(libs.destinations.core)
    ksp(libs.destinations.ksp)

    implementation(libs.reorderable)

    implementation(libs.androidx.splashscreen)

    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.coil.gif)

    implementation(libs.exp4j)

    implementation(libs.toaster)
    implementation(libs.permissions)

    implementation(libs.json5)
    implementation(libs.playServicesAds)


}