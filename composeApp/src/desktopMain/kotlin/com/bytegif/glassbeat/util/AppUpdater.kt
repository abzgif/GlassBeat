package com.bytegif.glassbeat.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.system.exitProcess

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L
)

sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class UpToDate(val currentVersion: String) : UpdateCheckState()
    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseTitle: String,
        val releaseNotes: String,
        val htmlUrl: String,
        val dmgAsset: GitHubReleaseAsset?
    ) : UpdateCheckState()
    data class Downloading(
        val latestVersion: String,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateCheckState()
    data class InstallingAndRestarting(val latestVersion: String) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

/**
 * Built-in OTA Updater for mac-glassbeat (https://github.com/abzgif/mac-glassbeat).
 * Checks GitHub Releases, downloads the new macOS .dmg/.zip asset (or pulls source if running via Gradle),
 * installs the new version, and automatically reopens GlassBeat.
 */
object AppUpdater {

    const val CURRENT_VERSION = "1.1.5"
    const val GITHUB_REPO_OWNER = "abzgif"
    const val GITHUB_REPO_NAME = "mac-glassbeat"
    const val GITHUB_REPO_URL = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
    private const val ALL_RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val state: StateFlow<UpdateCheckState> = _state.asStateFlow()

    fun resetState() {
        _state.value = UpdateCheckState.Idle
    }

    fun openGitHubRepo() {
        openUrl(GITHUB_REPO_URL)
    }

    fun openUrl(url: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
            } else {
                ProcessBuilder("open", url).start()
            }
        } catch (_: Exception) {
            runCatching { ProcessBuilder("open", url).start() }
        }
    }

    fun checkForUpdates() {
        if (_state.value is UpdateCheckState.Checking || _state.value is UpdateCheckState.Downloading) return
        _state.value = UpdateCheckState.Checking

        scope.launch {
            try {
                val release = fetchLatestRelease()
                if (release == null || release.tagName.isBlank()) {
                    _state.value = UpdateCheckState.UpToDate(CURRENT_VERSION)
                    return@launch
                }

                val latestClean = release.tagName.removePrefix("v").removePrefix("V").trim()
                val currentClean = CURRENT_VERSION.removePrefix("v").removePrefix("V").trim()

                if (isNewerVersion(latestClean, currentClean)) {
                    val dmgAsset = release.assets.firstOrNull {
                        it.name.endsWith(".dmg", ignoreCase = true) ||
                            it.name.endsWith(".pkg", ignoreCase = true) ||
                            it.name.endsWith(".app.zip", ignoreCase = true) ||
                            it.name.endsWith(".zip", ignoreCase = true)
                    }
                    _state.value = UpdateCheckState.UpdateAvailable(
                        currentVersion = CURRENT_VERSION,
                        latestVersion = latestClean,
                        releaseTitle = release.name.ifBlank { "GlassBeat v$latestClean" },
                        releaseNotes = release.body.ifBlank { "New macOS release available on GitHub." },
                        htmlUrl = release.htmlUrl.ifBlank { "$GITHUB_REPO_URL/releases" },
                        dmgAsset = dmgAsset
                    )
                } else {
                    _state.value = UpdateCheckState.UpToDate(CURRENT_VERSION)
                }
            } catch (e: Exception) {
                _state.value = UpdateCheckState.Error(
                    e.localizedMessage ?: "Unable to reach GitHub Releases API"
                )
            }
        }
    }

    private fun fetchLatestRelease(): GitHubRelease? {
        val latestConn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GlassBeat-macOS/$CURRENT_VERSION")
        }

        if (latestConn.responseCode == 200) {
            val body = latestConn.inputStream.bufferedReader().use { it.readText() }
            return json.decodeFromString<GitHubRelease>(body)
        }

        // Fallback to /releases list in case only pre-releases exist or /releases/latest returns 404
        val listConn = (URL(ALL_RELEASES_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GlassBeat-macOS/$CURRENT_VERSION")
        }
        if (listConn.responseCode == 200) {
            val body = listConn.inputStream.bufferedReader().use { it.readText() }
            val list = json.decodeFromString<List<GitHubRelease>>(body)
            return list.firstOrNull()
        }
        return null
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest == current) return false
        val latestParts = latest.split(".", "-").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return latest != current
    }

    /**
     * Downloads the update and launches a detached macOS updater script that installs
     * the new version and automatically reopens GlassBeat.
     */
    fun downloadAndApplyUpdate(updateInfo: UpdateCheckState.UpdateAvailable) {
        scope.launch {
            val asset = updateInfo.dmgAsset
            if (asset != null && asset.downloadUrl.isNotBlank()) {
                try {
                    val tempDir = File(System.getProperty("java.io.tmpdir"), "glassbeat_update")
                    if (!tempDir.exists()) tempDir.mkdirs()
                    val downloadedFile = File(tempDir, asset.name.ifBlank { "GlassBeat-${updateInfo.latestVersion}.dmg" })

                    _state.value = UpdateCheckState.Downloading(
                        latestVersion = updateInfo.latestVersion,
                        downloadedBytes = 0L,
                        totalBytes = asset.size
                    )

                    val conn = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        setRequestProperty("User-Agent", "GlassBeat-macOS/$CURRENT_VERSION")
                    }

                    val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else asset.size
                    conn.inputStream.use { input ->
                        FileOutputStream(downloadedFile).use { output ->
                            val buf = ByteArray(32 * 1024)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                downloaded += read
                                _state.value = UpdateCheckState.Downloading(
                                    latestVersion = updateInfo.latestVersion,
                                    downloadedBytes = downloaded,
                                    totalBytes = total
                                )
                            }
                        }
                    }

                    _state.value = UpdateCheckState.InstallingAndRestarting(updateInfo.latestVersion)
                    launchMacOsInstallerAndReopen(downloadedFile)
                } catch (e: Exception) {
                    _state.value = UpdateCheckState.Error(
                        "Update download failed: ${e.localizedMessage ?: "Network error"}"
                    )
                }
            } else {
                // If running from git source checkout and no binary asset is attached yet, pull & relaunch
                val workDir = File(System.getProperty("user.dir") ?: ".")
                if (File(workDir, "run_macos.sh").exists()) {
                    _state.value = UpdateCheckState.InstallingAndRestarting(updateInfo.latestVersion)
                    launchSourceGitPullAndReopen(workDir)
                } else {
                    openUrl(updateInfo.htmlUrl)
                }
            }
        }
    }

    private suspend fun launchMacOsInstallerAndReopen(installerFile: File) = withContext(Dispatchers.IO) {
        val currentPid = ProcessHandle.current().pid()
        val currentCmd = ProcessHandle.current().info().command().orElse("")
        val detectedAppBundle = if (currentCmd.contains(".app/Contents/")) {
            currentCmd.substringBefore(".app/Contents/") + ".app"
        } else {
            "/Applications/GlassBeat.app"
        }

        val workDir = File(System.getProperty("user.dir") ?: ".")
        val runScriptFile = File(workDir, "run_macos.sh")

        val scriptFile = File(System.getProperty("java.io.tmpdir"), "glassbeat_apply_update.sh")
        val scriptContent = """
            #!/usr/bin/env bash
            # Wait for old GlassBeat process ($currentPid) to exit
            for i in {1..30}; do
                if ! kill -0 $currentPid 2>/dev/null; then
                    break
                fi
                sleep 0.3
            done

            INSTALLER="${installerFile.absolutePath}"
            TARGET_APP="$detectedAppBundle"
            MNT_DIR="/tmp/glassbeat_dmg_mnt_$$"

            if [[ "${'$'}INSTALLER" == *.dmg ]]; then
                mkdir -p "${'$'}MNT_DIR"
                hdiutil attach "${'$'}INSTALLER" -nobrowse -readonly -mountpoint "${'$'}MNT_DIR" >/dev/null 2>&1
                FOUND_APP=${'$'}(find "${'$'}MNT_DIR" -maxdepth 2 -name "*.app" | head -n 1)
                if [[ -n "${'$'}FOUND_APP" ]]; then
                    rm -rf "${'$'}TARGET_APP" 2>/dev/null || true
                    ditto "${'$'}FOUND_APP" "${'$'}TARGET_APP"
                    xattr -cr "${'$'}TARGET_APP" 2>/dev/null || true
                fi
                hdiutil detach "${'$'}MNT_DIR" -force >/dev/null 2>&1 || true
                rmdir "${'$'}MNT_DIR" 2>/dev/null || true
                if [[ -d "${'$'}TARGET_APP" ]]; then
                    open -n "${'$'}TARGET_APP"
                    exit 0
                fi
            elif [[ "${'$'}INSTALLER" == *.zip ]]; then
                UNZIP_DIR="/tmp/glassbeat_zip_$$"
                mkdir -p "${'$'}UNZIP_DIR"
                ditto -x -k "${'$'}INSTALLER" "${'$'}UNZIP_DIR"
                FOUND_APP=${'$'}(find "${'$'}UNZIP_DIR" -maxdepth 2 -name "*.app" | head -n 1)
                if [[ -n "${'$'}FOUND_APP" ]]; then
                    rm -rf "${'$'}TARGET_APP" 2>/dev/null || true
                    ditto "${'$'}FOUND_APP" "${'$'}TARGET_APP"
                    xattr -cr "${'$'}TARGET_APP" 2>/dev/null || true
                fi
                rm -rf "${'$'}UNZIP_DIR"
                if [[ -d "${'$'}TARGET_APP" ]]; then
                    open -n "${'$'}TARGET_APP"
                    exit 0
                fi
            fi

            # Fallback if running from source directory
            if [[ -x "${runScriptFile.absolutePath}" ]]; then
                cd "${workDir.absolutePath}"
                git pull --ff-only 2>/dev/null || true
                nohup "${runScriptFile.absolutePath}" >/dev/null 2>&1 &
            fi
        """.trimIndent()

        scriptFile.writeText(scriptContent)
        scriptFile.setExecutable(true)

        ProcessBuilder("/bin/bash", "-c", "nohup \"${scriptFile.absolutePath}\" >/dev/null 2>&1 &")
            .start()

        kotlinx.coroutines.delay(400L)
        exitProcess(0)
    }

    private suspend fun launchSourceGitPullAndReopen(workDir: File) = withContext(Dispatchers.IO) {
        val currentPid = ProcessHandle.current().pid()
        val scriptFile = File(System.getProperty("java.io.tmpdir"), "glassbeat_source_update.sh")
        scriptFile.writeText(
            """
            #!/usr/bin/env bash
            for i in {1..30}; do
                if ! kill -0 $currentPid 2>/dev/null; then
                    break
                fi
                sleep 0.3
            done
            cd "${workDir.absolutePath}"
            git pull --ff-only 2>/dev/null || true
            nohup ./run_macos.sh >/dev/null 2>&1 &
            """.trimIndent()
        )
        scriptFile.setExecutable(true)
        ProcessBuilder("/bin/bash", "-c", "nohup \"${scriptFile.absolutePath}\" >/dev/null 2>&1 &")
            .start()
        kotlinx.coroutines.delay(400L)
        exitProcess(0)
    }
}
