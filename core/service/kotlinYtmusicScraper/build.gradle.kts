@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.gradle.internal.tasks.CompileArtProfileTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    androidLibrary {
        namespace = "com.sakayori.kotlinytmusicscraper"
        compileSdk = 36
        minSdk = 26
    }

    val xcfName = "kotlinytmusicscraperKit"

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
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
                implementation(libs.kotlin.stdlib)
                implementation(projects.domain)
                implementation(projects.common)
                implementation(projects.ktorExt)
                implementation(libs.okio)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.encoding)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlin.reflect)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.serialization.kotlinx.xml)
                implementation(libs.ktor.serialization.kotlinx.protobuf)

                implementation(libs.ksoup.html)
                implementation(libs.ksoup.entities)
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
                implementation(libs.ffmpeg.kit.sakayori)
                implementation(libs.gson)

                implementation(libs.newpipe.extractor)
                implementation(libs.okhttp3.okhttp)
            }
        }

        iosMain {
            dependencies {
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.newpipe.extractor)
                implementation(libs.okhttp3.okhttp)
            }
        }
    }
}

tasks.withType<CompileArtProfileTask> {
    enabled = false
}
