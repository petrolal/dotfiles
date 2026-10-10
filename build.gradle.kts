import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

plugins {
    java
    application
    id("org.graalvm.buildtools.native") version "0.10.4"
}

group = "dev.petrolal"
version = "1.0.0"

defaultTasks("all")

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("dev.petrolal.dotfiles.Main")
}

tasks.jar {
    archiveBaseName.set("dotfiles-deployer")
    manifest {
        attributes(
            mapOf(
                "Main-Class" to "dev.petrolal.dotfiles.Main",
                "Implementation-Title" to "dotfiles-deployer",
                "Implementation-Version" to project.version
            )
        )
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

fun resolveGraalVmHome(): String? {
    val env = System.getenv("GRAALVM_HOME")
    if (!env.isNullOrEmpty()) {
        return env
    }
    return try {
        val proc = ProcessBuilder(
            "sh", "-c",
            "dirname \$(dirname \$(readlink -f \$(command -v native-image 2>/dev/null) 2>/dev/null))"
        ).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        if (proc.waitFor() == 0 && out.isNotEmpty() && File(out).isDirectory) out else null
    } catch (ignored: Exception) {
        null
    }
}

fun getDeployFlags(): List<String> {
    val raw = (
        project.findProperty("deployFlags")
            ?: project.findProperty("flags")
            ?: project.findProperty("args")
            ?: System.getenv("DEPLOY_FLAGS")
            ?: ""
        ).toString().trim()
    return raw.split(Regex("\\s+")).filter { it.isNotEmpty() }
}

val invokerFile = layout.projectDirectory.file("bin/invoker").asFile
val wrapperSrc = layout.projectDirectory.file("config/gtk-3.0/wrapper-menu-fix.c").asFile
val wrapperModule = layout.projectDirectory.file("config/gtk-3.0/libwrapper-menu-fix.so").asFile
val gtk3ModDir = File(System.getenv("HOME") ?: System.getProperty("user.home"), ".local/state/nix/profile/lib/gtk-3.0/modules")

// ---------------------------------------------------------------------------
// Native Image Configuration
// ---------------------------------------------------------------------------

graalvmNative {
    toolchainDetection.set(false)
    binaries {
        named("main") {
            imageName.set("invoker")
            mainClass.set("dev.petrolal.dotfiles.Main")
            buildArgs.addAll("-O3", "--gc=epsilon", "-march=native")
        }
    }
}

tasks.named<BuildNativeImageTask>("nativeCompile") {
    outputDirectory.set(layout.projectDirectory.dir("bin"))
    doFirst {
        layout.projectDirectory.dir("bin").asFile.mkdirs()
        layout.buildDirectory.dir("native/nativeCompile").get().asFile.mkdirs()
    }
    val graalHome = resolveGraalVmHome()
    if (graalHome != null) {
        try {
            val field = BuildNativeImageTask::class.java.getDeclaredField("graalvmHomeProvider")
            field.isAccessible = true
            field.set(this, providers.provider { graalHome })
        } catch (e: Exception) {
            logger.warn("Could not set graalvmHomeProvider via reflection: " + e.message)
        }
    }
}

// ---------------------------------------------------------------------------
// Build & Modules Tasks
// ---------------------------------------------------------------------------

tasks.register<Exec>("compileWrapperModule") {
    group = "Build"
    description = "Compiles GTK wrapper menu fix module (libwrapper-menu-fix.so)."
    inputs.file(wrapperSrc)
    outputs.file(wrapperModule)
    workingDir = projectDir
    commandLine(
        "sh", "-c",
        """
        nix-shell -p gtk3 gcc pkg-config --run 'gcc -shared -fPIC ${'$'}(pkg-config --cflags gtk+-3.0) "${wrapperSrc.absolutePath}" -o "${wrapperModule.absolutePath}" ${'$'}(pkg-config --libs gtk+-3.0)'
        """
    )
}

tasks.register("modules") {
    group = "Build"
    description = "Compiles and installs GTK fix module into user profile."
    dependsOn("compileWrapperModule")
    doLast {
        gtk3ModDir.mkdirs()
        val target = File(gtk3ModDir, "libwrapper-menu-fix.so")
        exec {
            commandLine("install", "-m", "755", wrapperModule.absolutePath, target.absolutePath)
        }
        println("==> Installed GTK module into ${target.absolutePath}")
    }
}

tasks.register("all") {
    group = "Build"
    description = "Compile the native GraalVM invoker and GTK module (default goal)."
    dependsOn("nativeCompile", "compileWrapperModule")
}

// NOTE: An Emacs+ Eclipse plugin install step was attempted here (p2 director,
// IU com.mulgasoft.emacsplus.feature.feature.group) but was dropped: that
// plugin hasn't been updated since 2021 and depends on an OSGi bundle
// (javax.xml 1.3.4) that current Eclipse no longer ships -- a real binary
// incompatibility, not something fixable with director flags. The native
// Emacs keybinding scheme (config/eclipse/plugin_customization.ini +
// org.eclipse.ui.workbench.prefs, see nixos/modules/eclipse.nix) already
// gives C-x C-f, C-x b, C-x 2/3 etc. without needing this plugin.

// ---------------------------------------------------------------------------
// Verification & Check Tasks
// ---------------------------------------------------------------------------

tasks.named("check") {
    dependsOn("test", "nativeCompile")
    doLast {
        println("==> Running dry-run deploy check...")
        exec {
            commandLine(invokerFile.absolutePath, "--dry-run", "--no-reload")
        }
    }
}

tasks.register<Exec>("installcheck") {
    group = "Verification"
    description = "Verify the installed invoker runs."
    dependsOn("nativeCompile")
    commandLine(invokerFile.absolutePath, "--version")
}

// ---------------------------------------------------------------------------
// Deploy Tasks
// ---------------------------------------------------------------------------

tasks.register("deploy") {
    group = "Deploy"
    description = "Link dotfiles and apply XFCE theme via the native invoker."
    dependsOn("nativeCompile")
    doLast {
        val cmd = listOf(invokerFile.absolutePath) + getDeployFlags()
        exec {
            commandLine(cmd)
        }
    }
}

tasks.register("install") {
    group = "Deploy"
    description = "Link dotfiles, install GTK module, and apply XFCE theme."
    dependsOn("deploy", "modules")
}

tasks.register("dry-run") {
    group = "Deploy"
    description = "Show what deploy would do without changing anything."
    dependsOn("nativeCompile")
    doLast {
        val cmd = listOf(invokerFile.absolutePath, "--dry-run") + getDeployFlags()
        exec {
            commandLine(cmd)
        }
    }
}
tasks.register("dryRun") {
    group = "Deploy"
    description = "Alias for dry-run."
    dependsOn("dry-run")
}

tasks.register("scale") {
    group = "Deploy"
    description = "Reset WM margins for current display, reload XFCE."
    dependsOn("nativeCompile")
    doLast {
        val cmd = listOf(invokerFile.absolutePath, "--scale") + getDeployFlags()
        exec {
            commandLine(cmd)
        }
    }
}

tasks.register<JavaExec>("quick-deploy") {
    group = "Deploy"
    description = "Deploy via the JVM directly, without a native-image build."
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.petrolal.dotfiles.Main")
    environment("DOTFILES_DIR", projectDir.absolutePath)
    args = getDeployFlags()
}
tasks.register("quickDeploy") {
    group = "Deploy"
    description = "Alias for quick-deploy."
    dependsOn("quick-deploy")
}

tasks.register("uninstall") {
    group = "Deploy"
    description = "Remove all dotfiles symlinks managed by deploy."
    dependsOn("nativeCompile")
    doLast {
        val cmd = listOf(invokerFile.absolutePath, "uninstall") + getDeployFlags()
        exec {
            commandLine(cmd)
        }
    }
}

// ---------------------------------------------------------------------------
// Desktop Reload Tasks
// ---------------------------------------------------------------------------

tasks.register("reload") {
    group = "Desktop"
    description = "Reload all restartable desktop components (panel, xfwm4, xsettingsd, GTK, thunar, notifyd)."
    doLast {
        println("==> Reloading all restartable desktop components...")
        exec {
            isIgnoreExitValue = true
            commandLine(
                "sh", "-c",
                """
                -xfce4-panel -r 2>/dev/null || xfce4-panel -r || true
                -xfwm4 --replace &
                -xfsettingsd --replace &
                -pkill -f xfce4-notifyd 2>/dev/null || true
                -thunar -q 2>/dev/null || true
                xfconf-query -c xsettings -p /Net/ThemeName -s "Adwaita" 2>/dev/null || true
                xfconf-query -c xsettings -p /Net/ThemeName -s "Adwaita-dark" 2>/dev/null || true
                """
            )
        }
    }
}

tasks.register("reload-panel") {
    group = "Desktop"
    description = "Restart only xfce4-panel."
    doLast {
        println("==> Restarting XFCE panel...")
        exec {
            isIgnoreExitValue = true
            commandLine("xfce4-panel", "-r")
        }
    }
}
tasks.register("reloadPanel") {
    group = "Desktop"
    description = "Alias for reload-panel."
    dependsOn("reload-panel")
}

tasks.register("reload-wm") {
    group = "Desktop"
    description = "Restart only xfwm4 window manager."
    doLast {
        println("==> Restarting XFWM4 window manager...")
        exec {
            isIgnoreExitValue = true
            commandLine("sh", "-c", "xfwm4 --replace &")
        }
    }
}
tasks.register("reloadWm") {
    group = "Desktop"
    description = "Alias for reload-wm."
    dependsOn("reload-wm")
}

tasks.register("reload-theme") {
    group = "Desktop"
    description = "Trigger instant GTK CSS reload across all windows."
    doLast {
        println("==> Forcing GTK theme stylesheet reload...")
        exec {
            isIgnoreExitValue = true
            commandLine("sh", "-c", "xfconf-query -c xsettings -p /Net/ThemeName -s \"Adwaita\" && xfconf-query -c xsettings -p /Net/ThemeName -s \"Adwaita-dark\"")
        }
    }
}
tasks.register("reloadTheme") {
    group = "Desktop"
    description = "Alias for reload-theme."
    dependsOn("reload-theme")
}

// ---------------------------------------------------------------------------
// NixOS Tasks
// ---------------------------------------------------------------------------

tasks.register<Exec>("bootstrap") {
    group = "NixOS"
    description = "First-time setup from a fresh NixOS (runs bootstrap.sh)."
    workingDir = projectDir
    commandLine("./bootstrap.sh")
}

tasks.register("nix-link") {
    group = "NixOS"
    description = "Symlink configuration.nix and flake.nix into /etc/nixos."
    doLast {
        val nixosDir = layout.projectDirectory.dir("nixos").asFile.absolutePath
        exec {
            isIgnoreExitValue = true
            commandLine("git", "add", "-N", ".")
        }
        val confTarget = "$nixosDir/configuration.nix"
        val flakeTarget = "$nixosDir/flake.nix"
        val confLink = Paths.get("/etc/nixos/configuration.nix")
        val flakeLink = Paths.get("/etc/nixos/flake.nix")

        val needsConf = !Files.isSymbolicLink(confLink) || Files.readSymbolicLink(confLink).toString() != confTarget
        val needsFlake = !Files.isSymbolicLink(flakeLink) || Files.readSymbolicLink(flakeLink).toString() != flakeTarget

        if (needsConf) {
            exec {
                standardInput = System.`in`
                commandLine("sudo", "-S", "ln", "-sfn", confTarget, "/etc/nixos/configuration.nix")
            }
        }
        if (needsFlake) {
            exec {
                standardInput = System.`in`
                commandLine("sudo", "-S", "ln", "-sfn", flakeTarget, "/etc/nixos/flake.nix")
            }
        }
    }
}
tasks.register("nixLink") {
    group = "NixOS"
    description = "Alias for nix-link."
    dependsOn("nix-link")
}

tasks.register("nix-switch") {
    group = "NixOS"
    description = "Rebuild NixOS from the flake and switch to it."
    dependsOn("nix-link")
    doLast {
        val nixosDir = layout.projectDirectory.dir("nixos").asFile.absolutePath
        exec {
            standardInput = System.`in`
            commandLine("sudo", "-S", "nixos-rebuild", "switch", "--flake", nixosDir)
        }
    }
}
tasks.register("nixSwitch") {
    group = "NixOS"
    description = "Alias for nix-switch."
    dependsOn("nix-switch")
}

tasks.register("nix-check") {
    group = "NixOS"
    description = "Verify Nix flake syntax and evaluate configuration without building."
    doLast {
        val nixosDir = layout.projectDirectory.dir("nixos").asFile.absolutePath
        exec {
            commandLine("nix", "flake", "check", nixosDir)
        }
    }
}
tasks.register("nixCheck") {
    group = "NixOS"
    description = "Alias for nix-check."
    dependsOn("nix-check")
}

tasks.register("nix-update") {
    group = "NixOS"
    description = "Update Nix flake inputs (flake.lock) to latest upstream revisions."
    doLast {
        val nixosDir = layout.projectDirectory.dir("nixos").asFile.absolutePath
        exec {
            commandLine("nix", "flake", "update", "--flake", nixosDir)
        }
    }
}
tasks.register("nixUpdate") {
    group = "NixOS"
    description = "Alias for nix-update."
    dependsOn("nix-update")
}

tasks.register("nix-gc") {
    group = "NixOS"
    description = "Collect garbage and remove old Nix store generations."
    doLast {
        exec {
            commandLine("nix-collect-garbage", "-d")
        }
        exec {
            commandLine("sudo", "-S", "nix-collect-garbage", "-d")
        }
    }
}
tasks.register("nixGc") {
    group = "NixOS"
    description = "Alias for nix-gc."
    dependsOn("nix-gc")
}

tasks.register("system-install") {
    group = "NixOS"
    description = "nix-switch, then install."
    dependsOn("nix-switch", "install")
}
tasks.register("systemInstall") {
    group = "NixOS"
    description = "Alias for system-install."
    dependsOn("system-install")
}

tasks.register("upgrade") {
    group = "NixOS"
    description = "Full system upgrade: update flake inputs, rebuild NixOS, and redeploy dotfiles."
    dependsOn("nix-update", "system-install")
}

// Ensure proper task execution sequence
tasks.named("install") {
    mustRunAfter("nix-switch")
}
tasks.named("system-install") {
    mustRunAfter("nix-update")
}

// ---------------------------------------------------------------------------
// Cleaning Tasks
// ---------------------------------------------------------------------------

tasks.register("mostlyclean") {
    group = "Clean"
    description = "Remove the build directory and editor backup files."
    dependsOn("clean")
    doLast {
        projectDir.walkTopDown().forEach { f ->
            if (f.isFile && (f.name.endsWith("~") || (f.name.startsWith("#") && f.name.endsWith("#")))) {
                f.delete()
            }
        }
    }
}

tasks.named("clean") {
    doLast {
        if (invokerFile.exists()) {
            invokerFile.delete()
            println("==> Removed ${invokerFile.absolutePath}")
        }
    }
}

tasks.register("distclean") {
    group = "Clean"
    description = "clean + remove Nix result links."
    dependsOn("clean", "mostlyclean")
    doLast {
        projectDir.listFiles()
            ?.filter { it.name == "result" || it.name.startsWith("result-") }
            ?.forEach { it.delete() }
    }
}

tasks.register("maintainer-clean") {
    group = "Clean"
    description = "distclean + remove additional maintainer files."
    dependsOn("distclean")
}
