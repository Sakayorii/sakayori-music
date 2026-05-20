import com.android.build.gradle.internal.tasks.CompileArtProfileTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    androidLibrary {
        namespace = "com.sakayori.domain"
        compileSdk = 36
        minSdk = 26
    }
    val xcfName = "domainKit"

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
                implementation(libs.room.runtime)
                implementation(libs.kotlinx.serialization.json)
                implementation(projects.common)
                api(libs.androidx.paging.common)
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
