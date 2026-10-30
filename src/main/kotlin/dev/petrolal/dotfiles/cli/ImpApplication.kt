package dev.petrolal.dotfiles.cli

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.shell.CommandNotFound
import org.springframework.shell.command.CommandExecution.CommandParserExceptionsException
import org.springframework.shell.command.annotation.CommandScan

@SpringBootApplication
@CommandScan(basePackages = ["dev.petrolal.dotfiles.cli.commands"])
open class ImpApplication {
    companion object {
        // Spring Shell's non-interactive runner throws instead of returning a
        // failing exit code for an unknown command or a bad/missing option
        // (CommandNotFound / CommandParserExceptionsException) -- left
        // uncaught, that's an unhandled exception out of main() instead of a
        // clean "imp: <message>" + exit 1, which is the contract every
        // caller of run() (Cli.kt, keybindings, tests) relies on.
        @JvmStatic
        fun run(argv: Array<String>): Int {
            val app = SpringApplicationBuilder(ImpApplication::class.java)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .build()
            return try {
                val context = app.run(*argv)
                SpringApplication.exit(context)
            } catch (ex: CommandNotFound) {
                System.err.println("${Cli.PROGRAM_NAME}: ${ex.message}")
                1
            } catch (ex: CommandParserExceptionsException) {
                ex.parserExceptions.forEach { System.err.println("${Cli.PROGRAM_NAME}: ${it.message}") }
                1
            }
        }
    }
}
