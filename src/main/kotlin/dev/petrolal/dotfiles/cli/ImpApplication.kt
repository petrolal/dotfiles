package dev.petrolal.dotfiles.cli

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.shell.command.annotation.CommandScan
import kotlin.system.exitProcess

@SpringBootApplication
@CommandScan(basePackages = ["dev.petrolal.dotfiles.cli.commands"])
open class ImpApplication {
    companion object {
        @JvmStatic
        fun main(argv: Array<String>) {
            exitProcess(run(argv))
        }

        @JvmStatic
        fun run(argv: Array<String>): Int {
            val app = SpringApplicationBuilder(ImpApplication::class.java)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .build()
            val context = app.run(*argv)
            return SpringApplication.exit(context)
        }
    }
}
