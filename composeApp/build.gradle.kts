import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    sourceSets {
        getByName("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)

                // Coroutines (Swing/Main dispatcher for Desktop)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

                // JSON Serialization for preferences & state
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

                // SQLite JDBC for Playlists, Favorites, and Listen History persistence
                implementation("org.xerial:sqlite-jdbc:3.47.1.0")

                // Audio Tag Editor (jaudiotagger)
                implementation("net.jthink:jaudiotagger:3.0.1")

                // MP3 / Vorbis / FLAC SPI for JVM audio fallback decoding
                implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
                implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
                implementation("org.jflac:jflac-codec:1.5.2")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.bytegif.glassbeat.MainKt"

        jvmArgs += listOf(
            "-Dapple.awt.application.name=GlassBeat",
            "-Dapple.awt.application.appearance=system",
            "-Dsun.java2d.metal=true",
            "-Xdock:name=GlassBeat"
        )

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Pkg,
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.Rpm
            )
            packageName = "GlassBeat"
            packageVersion = "1.1.8"
            description = "GlassBeat Music Player for Desktop (macOS, Windows, Linux)"
            vendor = "ByteGif"
            modules("java.sql", "java.desktop", "jdk.unsupported")

            macOS {
                bundleID = "com.bytegif.glassbeat.macos"
                dockName = "GlassBeat"
                minimumSystemVersion = "12.0"
                packageBuildVersion = "21"
                iconFile.set(project.file("src/desktopMain/resources/GlassBeat.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleDocumentTypes</key>
                        <array>
                            <dict>
                                <key>CFBundleTypeName</key>
                                <string>Audio File</string>
                                <key>CFBundleTypeRole</key>
                                <string>Viewer</string>
                                <key>LSHandlerRank</key>
                                <string>Alternate</string>
                                <key>CFBundleTypeIconFile</key>
                                <string>GlassBeat.icns</string>
                                <key>CFBundleTypeExtensions</key>
                                <array>
                                    <string>mp3</string>
                                    <string>flac</string>
                                    <string>m4a</string>
                                    <string>wav</string>
                                    <string>ogg</string>
                                    <string>opus</string>
                                    <string>aac</string>
                                    <string>aiff</string>
                                    <string>aif</string>
                                </array>
                                <key>LSItemContentTypes</key>
                                <array>
                                    <string>public.audio</string>
                                    <string>public.mp3</string>
                                    <string>public.mpeg-4-audio</string>
                                    <string>org.xiph.flac</string>
                                    <string>org.xiph.ogg-audio</string>
                                    <string>com.microsoft.waveform-audio</string>
                                    <string>public.aiff-audio</string>
                                </array>
                            </dict>
                        </array>
                    """.trimIndent()
                }
            }

            windows {
                menuGroup = "GlassBeat"
                shortcut = true
                perUserInstall = true
                upgradeUuid = "8f4e3b1a-7c9d-4e2f-9a1b-3c5d7e9f1a2b"
                iconFile.set(project.file("src/desktopMain/resources/GlassBeat.ico"))
            }

            linux {
                packageName = "glassbeat"
                debMaintainer = "support@bytegif.com"
                menuGroup = "AudioVideo;Audio;Player;"
                appCategory = "AudioVideo"
                shortcut = true
                iconFile.set(project.file("src/desktopMain/resources/glassbeat_dock_icon.png"))
            }
        }
    }
}
