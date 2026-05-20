@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.gradle.internal.tasks.CompileArtProfileTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xmulti-dollar-interpolation")
        freeCompilerArgs.add("-Xwhen-guards")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvmToolchain(21)
    androidLibrary {
        namespace = "com.sakayori.data"
        compileSdk = 36
        minSdk = 26
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    val xcfName = "dataKit"

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = xcfName
            isStatic = true
            linkerOpts.add("-lsqlite3")
        }
    }

    jvm {
    }

    dependencies {
        implementation(platform(libs.koin.bom))
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.common)
                implementation(projects.domain)
                implementation(projects.aiService)
                implementation(projects.lyricsService)
                implementation(projects.spotify)
                implementation(projects.kotlinYtmusicScraper)
                implementation(projects.kizzy)

                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.datastore.preferences)

                implementation(libs.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.androidx.room.migration)

                implementation(libs.koin.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.koin.android)
                implementation(projects.media3)
                implementation(libs.room.ktx)
            }
        }

        iosMain {
            dependencies {
            }
        }

        jvmMain {
            dependencies {
                implementation(projects.mediaJvm)
                implementation(libs.nowplaying)
                implementation(libs.jna)
                implementation(libs.jna.platform)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

tasks.withType<CompileArtProfileTask> {
    enabled = false
}
