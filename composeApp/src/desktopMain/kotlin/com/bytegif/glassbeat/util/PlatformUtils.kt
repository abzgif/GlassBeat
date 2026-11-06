package com.bytegif.glassbeat.util

import java.awt.Desktop
import java.io.File

enum class DesktopOs {
    MACOS,
    WINDOWS,
    LINUX
}

object PlatformUtils {

    val currentOs: DesktopOs by lazy {
        val osName = System.getProperty("os.name")?.lowercase() ?: ""
        when {
            osName.contains("mac") || osName.contains("darwin") -> DesktopOs.MACOS
            osName.contains("win") -> DesktopOs.WINDOWS
            else -> DesktopOs.LINUX
        }
    }

    val isMacOs: Boolean get() = currentOs == DesktopOs.MACOS
    val isWindows: Boolean get() = currentOs == DesktopOs.WINDOWS
    val isLinux: Boolean get() = currentOs == DesktopOs.LINUX

    val osDisplayName: String
        get() = when (currentOs) {
            DesktopOs.MACOS -> "macOS"
            DesktopOs.WINDOWS -> "Windows"
            DesktopOs.LINUX -> "Linux"
        }

    val editionTitle: String
        get() = "$osDisplayName Edition"

    val revealInFileManagerLabel: String
        get() = when (currentOs) {
            DesktopOs.MACOS -> "Reveal in Finder"
            DesktopOs.WINDOWS -> "Show in File Explorer"
            DesktopOs.LINUX -> "Show in File Manager"
        }

    fun getAppSupportDirectory(): File {
        val home = System.getProperty("user.home") ?: "."
        val dir = when (currentOs) {
            DesktopOs.MACOS -> File(home, "Library/Application Support/GlassBeat")
            DesktopOs.WINDOWS -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) File(appData, "GlassBeat")
                else File(home, "AppData/Roaming/GlassBeat")
            }
            DesktopOs.LINUX -> {
                val xdgData = System.getenv("XDG_DATA_HOME")
                if (!xdgData.isNullOrBlank()) File(xdgData, "GlassBeat")
                else File(home, ".local/share/GlassBeat")
            }
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun revealFileInSystemManager(file: File) {
        if (!file.exists()) return
        try {
            when (currentOs) {
                DesktopOs.MACOS -> {
                    ProcessBuilder("open", "-R", file.absolutePath).start()
                }
                DesktopOs.WINDOWS -> {
                    ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
                }
                DesktopOs.LINUX -> {
                    val parent = file.parentFile ?: file
                    ProcessBuilder("xdg-open", parent.absolutePath).start()
                }
            }
        } catch (_: Exception) {
            runCatching {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file.parentFile ?: file)
                }
            }
        }
    }
}
