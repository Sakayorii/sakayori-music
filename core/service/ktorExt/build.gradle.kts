import com.android.build.gradle.internal.tasks.CompileArtProfileTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    androidLibrary {
        namespace = "com.sakayori.ktorext"
        compileSdk = 36
        minSdk = 26
    }

    val xcfName = "ktorExtKit"

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

    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                api(libs.ktor.client.core)
                api(libs.ktor.client.cio)
                implementation(libs.ktor.client.encoding)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.brotli.dec)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.brotli.dec)
            }
        }

        iosMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

tasks.withType<CompileArtProfileTask> {
    enabled = false
}
