// NotificationCenter.kt --- Interactive Tofi-based Notification Center for Hyprland/RetroVHSSucks
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.system.ProcessRunner
import java.io.File
import java.nio.charset.StandardCharsets

object NotificationCenter {
    data class NotificationEntry(
        val line: Int,
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

        companion object {
            /** Parses one history.tsv record, tagging it with its 0-based
             * `line` index so it can later be removed by identity rather
             * than by re-comparing every field. */
            fun parse(line: Int, raw: String): NotificationEntry? {
                val parts = raw.split("\t")
                if (parts.size < 3 || parts[2].isBlank()) return null
                val body = if (parts.size >= 4) parts[3] else ""
                return NotificationEntry(line, parts[0], parts[1], parts[2], body)
            }
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

    private fun loadHistory(): List<NotificationEntry> {
        val file = NotificationLogger.getHistoryFile()
        if (!file.exists()) return emptyList()
        return file.readLines(StandardCharsets.UTF_8)
            .mapIndexedNotNull { index, line -> NotificationEntry.parse(index, line) }
    }

    @JvmStatic
    fun show(): Int {
        var dndStatus = getDndStatus()
        var history = loadHistory()
        while (true) {
            val entryMap = mutableMapOf<String, NotificationEntry>()
            for (entry in history.asReversed()) {
                val label = entry.toMenuLabel()
                var uniqueLabel = label
                var count = 1
                while (entryMap.containsKey(uniqueLabel)) {
                    uniqueLabel = "$label ($count)"
                    count++
                }
                entryMap[uniqueLabel] = entry
            }

            val menu = buildString {
                append("[Limpar histórico]\n")
                append("[Não Perturbe: $dndStatus]\n")
                if (entryMap.isEmpty()) {
                    append("(Histórico vazio)\n")
                } else {
                    for (label in entryMap.keys) {
                        append(label).append("\n")
                    }
                }
            }

            val selected = Tofi.prompt(
                menu,
                "> ",
                "notificações:"
            ) ?: return 0

            val lower = selected.lowercase()
            when {
                lower.contains("limpar") || lower.contains("clear") -> {
                    clearNotifications()
                    Thread.sleep(150)
                    history = loadHistory()
                }
                lower.contains("perturbe") || lower.contains("dnd") -> {
                    toggleDnd()
                    Thread.sleep(150)
                    dndStatus = getDndStatus()
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
        val detail = buildString {
            append("[< Voltar]\n")
            append("[Excluir notificação]\n")
            append("App: ${entry.app}\n")
            append("Data: ${entry.datetime}\n")
            append("Título: ${entry.summary}\n")
            if (entry.body.isNotBlank()) {
                append("Mensagem: ${entry.body.replace('\n', ' ')}\n")
            }
        }

        val action = Tofi.prompt(
            detail,
            "> ",
            "detalhes:"
        ) ?: return

        val lower = action.lowercase()
        if (lower.contains("excluir") || lower.contains("delete")) {
            val file = NotificationLogger.getHistoryFile()
            val lines = file.readLines(StandardCharsets.UTF_8).filterIndexed { index, _ -> index != entry.line }
            file.writeText(lines.joinToString("\n") + if (lines.isNotEmpty()) "\n" else "", StandardCharsets.UTF_8)
            Thread.sleep(150)
        }
    }
}
