package dev.petrolal.dotfiles.cli

import org.springframework.boot.Banner
import org.springframework.boot.ExitCodeGenerator
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
        //
        // A command throwing CommandFailedException (resolved by
        // ImpCommandSupport's @ExceptionResolver into a CommandHandlingResult)
        // goes through the same path: Spring Shell's own ExitCodeAutoConfiguration
        // wraps the resolved exit code in a package-private
        // `ShellExitCodeException` and lets it escape app.run() rather than
        // folding it into SpringApplication.exit() -- so it needs catching
        // here too. It can't be named directly (package-private), but it
        // implements the public ExitCodeGenerator, which is enough to catch
        // it generically and pull the real exit code back out.
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
            } catch (ex: RuntimeException) {
                val exitCodeGenerator = ex as? ExitCodeGenerator ?: throw ex
                exitCodeGenerator.exitCode
            }
        }
    }
}
