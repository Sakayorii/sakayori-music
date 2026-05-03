@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.INT
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.apache.commons.io.FileUtils
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties

val isFullBuild: Boolean =
    try {
        extra["isFullBuild"] == "true"
    } catch (e: Exception) {
        false
    }

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutlibraries.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.build.config)
    alias(libs.plugins.osdetector)
    alias(libs.plugins.packagedeps)
    alias(libs.plugins.vlc.setup)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xwhen-guards")
        freeCompilerArgs.add("-Xcontext-parameters")
        freeCompilerArgs.add("-Xmulti-dollar-interpolation")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    android {
        namespace = "com.sakayori.music.composeapp"
        compileSdk = 36
        minSdk = 26
        withJava()
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        dependencies {
            val composeBom = project.dependencies.platform(libs.compose.bom)
            val koinBom = project.dependencies.platform(libs.koin.bom)
            implementation(composeBom)
            implementation(koinBom)
            implementation(libs.commons.io)
        }
        androidMain.dependencies {
            api(libs.koin.android)
            implementation(libs.koin.androidx.compose)

            implementation(libs.jetbrains.ui.tooling.preview)
            implementation(libs.ui.tooling.preview)
            implementation(libs.constraintlayout.compose)

            api(libs.work.runtime.ktx)
            api(libs.coil.network.okhttp)

            // Runtime
            api(libs.startup.runtime)

            api(projects.media3)
            api(projects.media3Ui)
        }
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.components.resources)
            implementation(libs.jetbrains.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Compose
            implementation(libs.compose.material3.adaptive)
            implementation(libs.compose.material.ripple)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material.icons.extended)

            // Other module
            api(projects.common)
            api(projects.domain)
            implementation(projects.data)

            // Navigation Compose
            implementation(libs.navigation.compose)

            // Kotlin Serialization
            implementation(libs.kotlinx.serialization.json)

            // Coil
            api(libs.coil.compose)
            api(libs.kmpalette.core)
            api(libs.kmpalette.network)
            implementation(libs.ktor.client.cio)

            // DataStore
            implementation(libs.datastore.preferences)

            // Lottie
            implementation(libs.compottie)
            implementation(libs.compottie.dot)
            implementation(libs.compottie.network)
            implementation(libs.compottie.resources)

            // Paging 3
            implementation(libs.androidx.paging.common)
            implementation(libs.paging.compose)

            implementation(libs.aboutlibraries)
            implementation(libs.aboutlibraries.compose.m3)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Jetbrains Markdown
            api(libs.markdown)

            // Blur Haze
            implementation(libs.haze)
            implementation(libs.haze.material)

            api(libs.cmptoast)
            implementation(libs.file.picker)

            // Liquid glass
            implementation(libs.liquid.glass)
            implementation(libs.liquid.glass.shape)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sentry.jvm)
            implementation(libs.native.tray)
            implementation(libs.kcef)
            implementation(libs.slf4j.simple)
            implementation(libs.coil.network.okhttp)
            implementation(projects.mediaJvmUi)
        }
    }
}

vlcSetup {
    vlcVersion = libs.versions.vlc.get()
    // Music player only needs audio/demuxer plugins. Compressing + filtering
    // drops the bundled VLC payload from ~80 MB to ~25 MB per platform.
    shouldCompressVlcFiles = true
    shouldIncludeAllVlcFiles = false
    pathToCopyVlcLinuxFilesTo = rootDir.resolve("vlc-natives/linux/")
    pathToCopyVlcMacosFilesTo = rootDir.resolve("vlc-natives/macos/")
    pathToCopyVlcWindowsFilesTo = rootDir.resolve("vlc-natives/windows/")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.sakayori.music.generated.resources"
}

compose.desktop {
    application {
        mainClass = "com.sakayori.music.MainKt"
        jvmArgs += "--add-opens=java.base/java.nio=ALL-UNNAMED"
        jvmArgs += "--add-opens=java.desktop/sun.awt=ALL-UNNAMED"
        jvmArgs += "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED"
        jvmArgs += "-Xmx768m"
        jvmArgs += "-Xms96m"
        jvmArgs += "-XX:+UseG1GC"
        jvmArgs += "-XX:MaxGCPauseMillis=80"
        jvmArgs += "-XX:MaxMetaspaceSize=192m"
        jvmArgs += "-XX:ReservedCodeCacheSize=128m"
        jvmArgs += "-XX:MaxDirectMemorySize=128m"
        jvmArgs += "-XX:MinHeapFreeRatio=10"
        jvmArgs += "-XX:MaxHeapFreeRatio=30"
        jvmArgs += "-XX:SoftRefLRUPolicyMSPerMB=50"
        jvmArgs += "-XX:+UseStringDeduplication"
        jvmArgs += "-XX:TieredStopAtLevel=1"
        jvmArgs += "-XX:CICompilerCount=2"
        jvmArgs += "-XX:+UseCompressedOops"
        jvmArgs += "-XX:+UseCompressedClassPointers"
        jvmArgs += "-XX:+ExplicitGCInvokesConcurrent"
        jvmArgs += "-XX:+HeapDumpOnOutOfMemoryError"
        jvmArgs += "-XX:HeapDumpPath=${'$'}{user.home}/.sakayori-music/heap-oom.hprof"
        jvmArgs += "-Xshare:auto"
        jvmArgs += "-Dfile.encoding=UTF-8"
        jvmArgs += "-Dsun.java2d.opengl=true"
        jvmArgs += "-Dsun.java2d.d3d=false"

        nativeDistributions {
            appResourcesRootDir = rootDir.resolve("vlc-natives/")
            val currentOs = org.gradle.internal.os.OperatingSystem.current()
            val listTarget = mutableListOf<TargetFormat>()
            when {
                currentOs.isWindows -> {
                    listTarget.add(TargetFormat.Exe)
                    listTarget.add(TargetFormat.Msi)
                }
                currentOs.isMacOsX -> {
                    listTarget.add(TargetFormat.Dmg)
                    listTarget.add(TargetFormat.Pkg)
                }
                currentOs.isLinux -> {
                    listTarget.add(TargetFormat.Deb)
                    listTarget.add(TargetFormat.Rpm)
                    listTarget.add(TargetFormat.AppImage)
                }
            }
            targetFormats(*listTarget.toTypedArray())
            // Explicit JDK modules — replaces the per-OS `includeAllModules = true`
            // which was bundling the full ~150 MB JDK. jlink trims this list down.
            modules(
                "java.naming",          // LDAP / network providers used by Ktor + JDK
                "java.management",      // Sentry, JMX agents
                "java.sql",             // some transitive deps (datastore, etc.)
                "java.security.jgss",   // Kerberos/SPNEGO for HTTPS
                "jdk.crypto.ec",        // EC curves for TLS
                "jdk.unsupported",      // sun.misc.Unsafe (JNA, kotlinx-coroutines)
                "java.instrument",      // Sentry self-instrumentation
                "jdk.localedata",       // i18n locales
                "jdk.accessibility",    // a11y APIs used by Compose
            )
            packageName = "SakayoriMusic"
            description = "SakayoriMusic - Music Player"
            vendor = "Sakayorii"
            macOS {
                val formatedDate =
                    Instant.now().let {
                        DateTimeFormatter
                            .ofPattern("yyyy.MM.dd")
                            .withZone(ZoneId.of("UTC"))
                            .format(it)
                    }
                bundleID = "com.sakayori.music"
                packageVersion = formatedDate
                iconFile.set(project.file("icon/circle_app_icon.icns"))
                val macExtraPlistKeys =
                    """
                    <key>LSApplicationCategoryType</key>
                    <string>public.app-category.music</string>
                    <key>UIBackgroundModes</key>
                    <array>
                        <string>audio</string>
                        <string>fetch</string>
                        <string>processing</string>
                    </array>
                    <key>CFBundleURLTypes</key>
                    <array>
                        <dict>
                            <key>CFBundleTypeRole</key>
                            <string>Viewer</string>
                            <key>CFBundleURLName</key>
                            <string>com.sakayori.music.deeplink</string>
                            <key>CFBundleURLSchemes</key>
                            <array>
                                <string>SakayoriMusic</string>
                            </array>
                        </dict>
                    </array>
                    """.trimIndent()
                infoPlist {
                    extraKeysRawXml = macExtraPlistKeys
                }
            }
            windows {
                packageVersion =
                    libs.versions.version.name
                        .get()
                        .removeSuffix("-hf")
                iconFile.set(project.file("icon/circle_app_icon.ico"))
                shortcut = true
                menu = true
                menuGroup = "SakayoriMusic"
                console = false
                dirChooser = true
                perUserInstall = false
                upgradeUuid = "a1b2c3d4-5678-9abc-def0-1a2b3c4d5e6f"
                msiPackageVersion =
                    libs.versions.version.name
                        .get()
                        .removeSuffix("-hf")
            }
            licenseFile.set(rootProject.file("EULA.rtf"))
            linux {
                packageVersion =
                    libs.versions.version.name
                        .get()
                        .removeSuffix("-hf")
                iconFile.set(project.file("icon/circle_app_icon.png"))
            }
        }

        buildTypes.release.proguard {
            optimize.set(true)
            obfuscate.set(true)
            configurationFiles.from("proguard-desktop-rules.pro")
        }
    }
}

buildkonfig {
    packageName = "com.sakayori.music"
    exposeObjectWithName = "BuildKonfig"
    defaultConfigs {
        val versionName =
            libs.versions.version.name
                .get()
        val versionCode =
            libs.versions.version.code
                .get()
                .toInt()
        buildConfigField(STRING, "versionName", versionName)
        buildConfigField(INT, "versionCode", "$versionCode")

        val defaultDsnAndroid = "https://4a0e76069c2e28dd95116965fb61dcee@o4511241357623296.ingest.us.sentry.io/4511241365422080"
        val defaultDsnDesktop = "https://ef7be6702ae19b1dfff88553e159184f@o4511241357623296.ingest.us.sentry.io/4511241397469184"
        val dsnAndroid = try {
            val properties = Properties()
            properties.load(rootProject.file("local.properties").inputStream())
            properties.getProperty("SENTRY_DSN_ANDROID") ?: properties.getProperty("SENTRY_DSN") ?: defaultDsnAndroid
        } catch (_: Exception) {
            defaultDsnAndroid
        }
        val dsnDesktop = try {
            val properties = Properties()
            properties.load(rootProject.file("local.properties").inputStream())
            properties.getProperty("SENTRY_DSN_DESKTOP") ?: properties.getProperty("SENTRY_DSN") ?: defaultDsnDesktop
        } catch (_: Exception) {
            defaultDsnDesktop
        }
        buildConfigField(STRING, "sentryDsnAndroid", dsnAndroid)
        buildConfigField(STRING, "sentryDsnDesktop", dsnDesktop)
        buildConfigField(STRING, "sentryDsn", "")
    }
}

aboutLibraries {
    collect.configPath = file("../config")
    export {
        outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json")
        prettyPrint = true
        excludeFields = listOf("generated")
    }
    library {
        // Enable the duplication mode, allows to merge, or link dependencies which relate
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        // Configure the duplication rule, to match "duplicates" with
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    }
}

linuxDebConfig {
    startupWMClass.set("java-lang-Thread")
}

afterEvaluate {
    tasks.withType<JavaExec> {
        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.base/java.nio=ALL-UNNAMED")

        // Pass bundled VLC natives path to the runtime for development
        val osSubDir =
            when {
                System.getProperty("os.name").contains("Mac") -> "macos"
                System.getProperty("os.name").contains("Win") -> "windows"
                else -> "linux"
            }
        val vlcNativesPath = rootDir.resolve("vlc-natives/$osSubDir").absolutePath
        systemProperty("vlc.bundled.path", vlcNativesPath)

        if (System.getProperty("os.name").contains("Mac")) {
            jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        }
    }

    fun packAppImage(isRelease: Boolean) {
        val appName = "SakayoriMusic"
        val appDirSrc = project.file("appimage")
        val packageOutput =
            if (isRelease) {
                layout.buildDirectory
                    .dir("compose/binaries/main-release/app/$appName")
                    .get()
                    .asFile
            } else {
                layout.buildDirectory
                    .dir("compose/binaries/main/app/$appName")
                    .get()
                    .asFile
            }
        if (!appDirSrc.exists() || !packageOutput.exists()) {
            return
        }

        val osArch = System.getProperty("os.arch")
        val isArm64 = osArch == "aarch64" || osArch == "arm64"
        val archString = if (isArm64) "aarch64" else "x86_64"

        val appimagetool =
            layout.buildDirectory
                .dir("tmp")
                .get()
                .asFile
                .resolve("appimagetool-$archString.AppImage")

        if (!appimagetool.exists()) {
            downloadFile(
                "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-$archString.AppImage",
                appimagetool,
            )
        }

        if (!appimagetool.canExecute()) {
            appimagetool.setExecutable(true)
        }

        val appDir =
            if (isRelease) {
                layout.buildDirectory
                    .dir("appimage/main-release/$appName.AppDir")
                    .get()
                    .asFile
            } else {
                layout.buildDirectory
                    .dir("appimage/main/$appName.AppDir")
                    .get()
                    .asFile
            }
        if (appDir.exists()) {
            appDir.deleteRecursively()
        }

        FileUtils.copyDirectory(appDirSrc, appDir)
        FileUtils.copyDirectory(packageOutput, appDir)

        // Generate SakayoriMusic.desktop dynamically
        val versionName =
            libs.versions.version.name
                .get()
        val desktopFile = appDir.resolve("SakayoriMusic.desktop")
        desktopFile.writeText(
            """[Desktop Entry]
            |Type=Application
            |Version=1.0
            |Name=SakayoriMusic
            |Comment=SakayoriMusic v$versionName - FOSS YouTube Music Client
            |Exec=bin/SakayoriMusic %u
            |Icon=sakayorimusic
            |Terminal=false
            |Categories=Audio;AudioVideo;
            |StartupWMClass=com.sakayori.music.MainKt
            |MimeType=x-scheme-handler/SakayoriMusic;
            |
            """.trimMargin(),
        )

        // Generate AppRun with icon and .desktop auto-install
        val appRun = appDir.resolve("AppRun")
        appRun.writeText(
            """#!/bin/sh
            |
            |SELF=${'$'}(readlink -f "${'$'}0")
            |HERE=${'$'}{SELF%/*}
            |
            |# Install icon to XDG icon directories for desktop integration
            |ICON_DIR="${'$'}HOME/.local/share/icons/hicolor/256x256/apps"
            |if [ ! -f "${'$'}ICON_DIR/sakayorimusic.png" ] || [ "${'$'}HERE/sakayorimusic.png" -nt "${'$'}ICON_DIR/sakayorimusic.png" ]; then
            |    mkdir -p "${'$'}ICON_DIR"
            |    cp "${'$'}HERE/sakayorimusic.png" "${'$'}ICON_DIR/sakayorimusic.png"
            |    gtk-update-icon-cache -f -t "${'$'}HOME/.local/share/icons/hicolor" 2>/dev/null || true
            |fi
            |
            |# Install .desktop file with WM_CLASS name so GNOME/KDE can match window to icon
            |DESKTOP_DIR="${'$'}HOME/.local/share/applications"
            |mkdir -p "${'$'}DESKTOP_DIR"
            |APPIMAGE_PATH="${'$'}{APPIMAGE:-${'$'}SELF}"
            |sed "s|Exec=bin/SakayoriMusic|Exec=${'$'}APPIMAGE_PATH|" "${'$'}HERE/SakayoriMusic.desktop" > "${'$'}DESKTOP_DIR/com.sakayori.music.MainKt.desktop"
            |update-desktop-database "${'$'}DESKTOP_DIR" 2>/dev/null || true
            |
            |cd "${'$'}HERE"
            |exec bin/$appName "${'$'}@"
            |
            """.trimMargin(),
        )
        appRun.setExecutable(true, false)

        val appExecutable = appDir.resolve("bin/$appName")
        if (!appExecutable.canExecute()) {
            appExecutable.setExecutable(true)
        }

        // Use ProcessBuilder instead of exec {} to avoid capturing project reference
        val process =
            ProcessBuilder(
                appimagetool.canonicalPath,
                "$appName.AppDir",
                "$appName-$archString.AppImage",
            ).directory(appDir.parentFile)
                .apply { environment()["ARCH"] = archString }
                .inheritIO()
                .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("appimagetool failed with exit code $exitCode")
        }
    }

    tasks.findByName("packageAppImage")?.doLast {
        packAppImage(false)
    }
    tasks.findByName("packageReleaseAppImage")?.doLast {
        packAppImage(true)
    }
}

// Mark JPackage tasks as not compatible with configuration cache
// This must be done outside afterEvaluate to work properly
tasks.withType<AbstractJPackageTask>().configureEach {
    notCompatibleWithConfigurationCache("Compose Desktop JPackage tasks are not yet compatible with configuration cache")
}

listOf("vlcExtract", "vlcFilterPlugins", "vlcSetup", "clean").forEach { taskName ->
    tasks.findByName(taskName)?.let {
        it.notCompatibleWithConfigurationCache("vlc-setup plugin tasks are not yet compatible with configuration cache")
    }
}

fun isVlcBundleComplete(root: File): Boolean {
    if (!root.exists()) return false
    val demuxDir = root.resolve("plugins/demux")
    if (!demuxDir.exists()) return false
    val requiredPlugins = listOf(
        "libmp4_plugin",
        "libavformat_plugin",
        "libmkv_plugin",
        "libadaptive_plugin",
    )
    val presentNames = demuxDir.listFiles()?.map { it.nameWithoutExtension.substringAfterLast('/') } ?: emptyList()
    return requiredPlugins.all { required -> presentNames.any { it.contains(required.removePrefix("lib")) } }
}

tasks.named("clean").configure {
    setDependsOn(emptyList<Any>())
    doFirst {
        val preserveDirs = listOf(
            rootDir.resolve("vlc-natives/windows"),
            rootDir.resolve("vlc-natives/linux"),
            rootDir.resolve("vlc-natives/macos"),
        )
        preserveDirs.forEach { dir ->
            if (dir.exists() && isVlcBundleComplete(dir)) {
                val backup = File(dir.parentFile, "${dir.name}.preserved")
                if (backup.exists()) backup.deleteRecursively()
                dir.copyRecursively(backup, overwrite = true)
            } else if (dir.exists()) {
                logger.warn("VLC bundle at ${dir.name} is incomplete, will force regeneration")
                dir.deleteRecursively()
            }
        }
    }
    doLast {
        val preserveDirs = listOf(
            rootDir.resolve("vlc-natives/windows"),
            rootDir.resolve("vlc-natives/linux"),
            rootDir.resolve("vlc-natives/macos"),
        )
        preserveDirs.forEach { dir ->
            val backup = File(dir.parentFile, "${dir.name}.preserved")
            if (backup.exists()) {
                if (dir.exists()) dir.deleteRecursively()
                backup.copyRecursively(dir, overwrite = true)
                backup.deleteRecursively()
            }
        }
    }
}

private fun downloadFile(
    url: String,
    destFile: File,
) {
    val destParent = destFile.parentFile
    destParent.mkdirs()

    if (destFile.exists()) {
        destFile.delete()
    }

    println("Download $url")
    URI(url).toURL().openStream().use { input ->
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    println("Download finish")
}
