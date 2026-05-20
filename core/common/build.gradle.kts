import com.android.build.gradle.internal.tasks.CompileArtProfileTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
}

kotlin {
    jvmToolchain(21)
    androidLibrary {
        namespace = "com.sakayori.common"
        compileSdk = 36
        minSdk = 26
    }

    val xcfName = "commonKit"

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

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kermit.logging)
                api(libs.kotlinx.datetime)
                api(libs.uri)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
            }
        }

        iosMain {
            dependencies {
            }
        }

        jvmMain {
            dependencies {
            }
        }
    }
}

tasks.withType<CompileArtProfileTask> {
    enabled = false
}
