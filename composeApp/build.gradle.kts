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
        val desktopMain by getting {
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

        val iconPath = project.file("src/desktopMain/resources/GlassBeat.icns").absolutePath
        jvmArgs += listOf(
            "-Dapple.awt.application.name=GlassBeat",
            "-Dapple.awt.application.appearance=system",
            "-Dsun.java2d.metal=true",
            "-Xdock:name=GlassBeat",
            "-Xdock:icon=$iconPath"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg)
            packageName = "GlassBeat"
            packageVersion = "1.1.5"
            description = "GlassBeat Music Player for macOS"
            vendor = "ByteGif"
            modules("java.sql", "java.desktop", "jdk.unsupported")

            macOS {
                bundleID = "com.bytegif.glassbeat.macos"
                dockName = "GlassBeat"
                minimumSystemVersion = "12.0"
                iconFile.set(project.file("src/desktopMain/resources/GlassBeat.icns"))
            }
        }
    }
}
