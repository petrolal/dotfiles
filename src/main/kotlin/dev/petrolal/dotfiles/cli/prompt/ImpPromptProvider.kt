package dev.petrolal.dotfiles.cli.prompt

import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle
import org.springframework.shell.jline.PromptProvider
import org.springframework.stereotype.Component

@Component
class ImpPromptProvider : PromptProvider {
    override fun getPrompt(): AttributedString {
        return AttributedString("imp:> ", AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
    }
}
