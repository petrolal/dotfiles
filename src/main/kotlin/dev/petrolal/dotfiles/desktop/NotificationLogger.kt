// NotificationLogger.kt --- Background daemon that logs D-Bus notifications for the notification center
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.core.DotfilePaths
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object NotificationLogger {
    const val MAX_HISTORY: Int = 100
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    @JvmStatic
    fun getCacheDir(): File {
        val xdgCache = System.getenv("XDG_CACHE_HOME")
        val base = if (!xdgCache.isNullOrEmpty()) {
            File(xdgCache)
        } else {
            File(DotfilePaths.userHomeDirectory().toFile(), ".cache")
        }
        val dir = File(base, "notifications")
        dir.mkdirs()
        return dir
    }

    @JvmStatic
    fun getHistoryFile(): File {
        val file = File(getCacheDir(), "history.tsv")
        if (!file.exists()) {
            file.createNewFile()
        }
        return file
    }

    private fun killExistingInstances() {
        val currentPid = ProcessHandle.current().pid()
        val pidFile = File(getCacheDir(), "logger.pid")
        if (pidFile.exists()) {
            val oldPid = pidFile.readText().trim().toLongOrNull()
            if (oldPid != null && oldPid != currentPid) {
                ProcessHandle.of(oldPid).ifPresent {
                    try {
                        it.destroy()
                    } catch (_: Exception) {}
                }
            }
        }
        pidFile.writeText(currentPid.toString())
    }

    private fun readDbusString(reader: BufferedReader): String {
        var line = reader.readLine() ?: return ""
        while (!line.trimStart().startsWith("string \"")) {
            line = reader.readLine() ?: return ""
        }
        val trimmed = line.trimStart()
        val firstQuote = trimmed.indexOf('"')
        var content = trimmed.substring(firstQuote + 1)
        val sb = StringBuilder()

        while (!content.endsWith("\"")) {
            sb.append(content).append(' ')
            val nextLine = reader.readLine() ?: break
            content = nextLine
        }

        if (content.endsWith("\"")) {
            sb.append(content.substring(0, content.length - 1))
        }

        return sb.toString()
            .replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
    }

    @JvmStatic
    fun run(): Int {
        killExistingInstances()

        val historyFile = getHistoryFile()
        val pb = ProcessBuilder("dbus-monitor", "interface=org.freedesktop.Notifications,member=Notify")
            .redirectError(ProcessBuilder.Redirect.DISCARD)

        val process: Process
        try {
            process = pb.start()
        } catch (e: Exception) {
            System.err.println("Failed to start dbus-monitor: ${e.message}")
            return 1
        }

        val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
        try {
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.contains("member=Notify")) {
                    val app = readDbusString(reader).ifBlank { "Sistema" }
                    reader.readLine() // uint32 replaces_id
                    val _icon = readDbusString(reader)
                    val summary = readDbusString(reader).ifBlank { "(sem título)" }
                    val body = readDbusString(reader)

                    val now = LocalDateTime.now().format(TIME_FORMATTER)
                    val record = "$now\t$app\t$summary\t$body\n"

                    historyFile.appendText(record, StandardCharsets.UTF_8)
                    trimHistory(historyFile)
                }
                line = reader.readLine()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            System.err.println("Notification logger error: ${e.message}")
        } finally {
            process.destroy()
        }
        return 0
    }

    private fun trimHistory(historyFile: File) {
        try {
            val lines = historyFile.readLines(StandardCharsets.UTF_8)
            if (lines.size > MAX_HISTORY) {
                val lastLines = lines.takeLast(MAX_HISTORY)
                val tmp = File(historyFile.parentFile, "${historyFile.name}.tmp")
                tmp.writeText(lastLines.joinToString("\n") + "\n", StandardCharsets.UTF_8)
                Files.move(tmp.toPath(), historyFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }
        } catch (_: Exception) {}
    }
}
