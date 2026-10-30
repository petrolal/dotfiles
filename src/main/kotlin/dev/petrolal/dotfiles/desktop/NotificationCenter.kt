// NotificationCenter.kt --- Interactive Tofi-based Notification Center for Hyprland/RetroVHSSucks
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.system.ProcessRunner
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object NotificationCenter {
    data class NotificationEntry(
        val datetime: String,
        val app: String,
        val summary: String,
        val body: String
    ) {
        fun toMenuLabel(): String {
            val base = if (body.isNotBlank()) {
                "[$datetime] $app: $summary — ${body.replace('\n', ' ')}"
            } else {
                "[$datetime] $app: $summary"
            }
            return if (base.length > 55) base.take(52) + "..." else base
        }
    }

    private fun getDndFile(): File = File(NotificationLogger.getCacheDir(), "dnd")

    fun getDndStatus(): String {
        val result = ProcessRunner.captureOutput(listOf("qs", "ipc", "call", "notifs", "getDnd"))
        val status = result.getOrNull()?.trim()
        if (status == "ATIVADO" || status == "DESATIVADO") {
            return status
        }
        val dndFile = getDndFile()
        if (dndFile.exists() && dndFile.readText().trim() == "true") {
            return "ATIVADO"
        }
        return "DESATIVADO"
    }

    fun toggleDnd() {
        val result = ProcessRunner.captureOutput(listOf("qs", "ipc", "call", "notifs", "toggleDnd"))
        val newStatus = result.getOrNull()?.trim()
        val dndFile = getDndFile()
        when (newStatus) {
            "ATIVADO" -> dndFile.writeText("true")
            "DESATIVADO" -> dndFile.writeText("false")
            else -> {
                val current = getDndStatus()
                if (current == "ATIVADO") {
                    dndFile.writeText("false")
                } else {
                    dndFile.writeText("true")
                }
            }
        }
    }

    fun clearNotifications() {
        NotificationLogger.getHistoryFile().writeText("")
        ProcessRunner.run(listOf("qs", "ipc", "call", "notifs", "clear"))
    }

    private fun runTofi(input: String, prompt: String = "> ", placeholder: String = "notificações:"): String? {
        val pb = ProcessBuilder(
            "tofi",
            "--prompt-text=$prompt",
            "--placeholder-text=$placeholder"
        )
        try {
            val process = pb.start()
            OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(input)
                writer.flush()
            }
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
            val exitCode = process.waitFor()
            return if (exitCode == 0 && output.isNotEmpty()) output else null
        } catch (_: Exception) {
            return null
        }
    }

    private fun loadHistory(): List<NotificationEntry> {
        val file = NotificationLogger.getHistoryFile()
        if (!file.exists()) return emptyList()
        val list = mutableListOf<NotificationEntry>()
        val lines = file.readLines(StandardCharsets.UTF_8)
        for (line in lines) {
            val parts = line.split("\t")
            if (parts.size >= 3) {
                val dt = parts[0]
                val app = parts[1]
                val sum = parts[2]
                val body = if (parts.size >= 4) parts[3] else ""
                if (sum.isNotBlank()) {
                    list.add(NotificationEntry(dt, app, sum, body))
                }
            }
        }
        return list
    }

    @JvmStatic
    fun show(): Int {
        while (true) {
            val dndStatus = getDndStatus()
            val history = loadHistory()

            val menuSb = java.lang.StringBuilder()
            menuSb.append("[Limpar histórico]\n")
            menuSb.append("[Não Perturbe: $dndStatus]\n")

            val entryMap = mutableMapOf<String, NotificationEntry>()
            if (history.isEmpty()) {
                menuSb.append("(Histórico vazio)\n")
            } else {
                for (entry in history.asReversed()) {
                    val label = entry.toMenuLabel()
                    var uniqueLabel = label
                    var count = 1
                    while (entryMap.containsKey(uniqueLabel)) {
                        uniqueLabel = "$label ($count)"
                        count++
                    }
                    entryMap[uniqueLabel] = entry
                    menuSb.append(uniqueLabel).append("\n")
                }
            }

            val selected = runTofi(
                menuSb.toString(),
                "> ",
                "notificações:"
            ) ?: return 0

            val lower = selected.lowercase()
            when {
                lower.contains("limpar") || lower.contains("clear") -> {
                    clearNotifications()
                    Thread.sleep(150)
                }
                lower.contains("perturbe") || lower.contains("dnd") -> {
                    toggleDnd()
                    Thread.sleep(150)
                }
                selected.startsWith("(") -> {
                    return 0
                }
                else -> {
                    val matchingEntry = entryMap[selected]
                    if (matchingEntry != null) {
                        showDetails(matchingEntry)
                    }
                }
            }
        }
    }

    private fun showDetails(entry: NotificationEntry) {
        val detailSb = java.lang.StringBuilder()
        detailSb.append("[< Voltar]\n")
        detailSb.append("[Excluir notificação]\n")
        detailSb.append("App: ${entry.app}\n")
        detailSb.append("Data: ${entry.datetime}\n")
        detailSb.append("Título: ${entry.summary}\n")
        if (entry.body.isNotBlank()) {
            detailSb.append("Mensagem: ${entry.body.replace('\n', ' ')}\n")
        }

        val action = runTofi(
            detailSb.toString(),
            "> ",
            "detalhes:"
        ) ?: return

        val lower = action.lowercase()
        if (lower.contains("excluir") || lower.contains("delete")) {
            val file = NotificationLogger.getHistoryFile()
            val lines = file.readLines(StandardCharsets.UTF_8).filterNot { line ->
                val parts = line.split("\t")
                if (parts.size >= 3) {
                    val dt = parts[0]
                    val app = parts[1]
                    val sum = parts[2]
                    val body = if (parts.size >= 4) parts[3] else ""
                    dt == entry.datetime && app == entry.app && sum == entry.summary && body == entry.body
                } else false
            }
            file.writeText(lines.joinToString("\n") + if (lines.isNotEmpty()) "\n" else "", StandardCharsets.UTF_8)
            Thread.sleep(150)
        }
    }
}
