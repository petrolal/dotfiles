// Cli.java --- CLI interface
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles;


final class Cli {
    private Cli() {}

    static final String VERSION = "1.2.0";
    static final String PROGRAM_NAME = "invoker";

    private static void printHelp() {
        System.out.printf("Usage: %s [COMMAND|OPTION]...%n%n", PROGRAM_NAME);
        System.out.println("Deploy Abyssal Biopunk dotfiles symlinks and configure desktop settings.\n");
        System.out.println("Commands:");
        System.out.println("  deploy            perform full deployment (generate, link, configure, reload) [default]");
        System.out.println("  uninstall         remove all deployed dotfile symlinks safely");
        System.out.println("  scale             query display resolution and reset WM margins");
        System.out.println("  generate          generate and verify templated configuration assets\n");
        System.out.println("Options:");
        System.out.println("  -u, --uninstall   remove all deployed dotfile symlinks safely");
        System.out.println("  -s, --scale       detect resolution and reset WM margins");
        System.out.println("  -g, --generate    generate/ensure templated configuration assets");
        System.out.println("  -n, --dry-run     simulate actions without modifying filesystem or xfconf");
        System.out.println("  -q, --quiet       suppress non-error output");
        System.out.println("      --links-only  only symlink dotfiles (implies --no-xfconf --no-reload)");
        System.out.println("      --no-xfconf   do not apply XFCE desktop settings via xfconf");
        System.out.println("      --no-generate do not generate templated configuration assets");
        System.out.println("      --no-reload   do not reload XFCE desktop services");
        System.out.println("  -h, --help        display this help text and exit");
        System.out.println("  -v, --version     display version information and exit");
    }

    private static void printVersion() {
        System.out.printf("%s %s (Abyssal Biopunk / Mac OS 9.2 Platinum)%n", PROGRAM_NAME, VERSION);
        System.out.println("License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>.");
        System.out.println("This is free software: you are free to change and redistribute it.");
        System.out.println("There is NO WARRANTY, to the extent permitted by law.");
    }

    private enum Action { DEPLOY, UNINSTALL, SCALE, GENERATE }

    static void main(String[] argv) {
        boolean dryRun = false;
        boolean verbose = true;
        boolean reload = true;
        boolean applySettings = true;
        boolean generateConfigs = true;
        Action action = Action.DEPLOY;

        for (String arg : argv) {
            switch (arg) {
                case "-h", "--help" -> {
                    printHelp();
                    System.exit(0);
                }
                case "-v", "--version" -> {
                    printVersion();
                    System.exit(0);
                }
                case "-n", "--dry-run" -> dryRun = true;
                case "-q", "--quiet" -> verbose = false;
                case "--links-only" -> {
                    applySettings = false;
                    reload = false;
                }
                case "--no-xfconf" -> applySettings = false;
                case "--no-generate" -> generateConfigs = false;
                case "--no-reload" -> reload = false;
                case "-u", "--uninstall", "uninstall" -> action = Action.UNINSTALL;
                case "-s", "--scale", "scale" -> action = Action.SCALE;
                case "-g", "--generate", "generate" -> action = Action.GENERATE;
                case "deploy" -> action = Action.DEPLOY;
                default -> {
                    System.err.println(PROGRAM_NAME + ": unrecognized option '" + arg + "'");
                    System.err.println("Try '" + PROGRAM_NAME + " --help' for more information.");
                    System.exit(1);
                }
            }
        }

        switch (action) {
            case UNINSTALL -> {
                Orchestrator.UninstallResult result = Orchestrator.uninstall(dryRun, verbose);
                if (!result.ok()) System.exit(1);
            }
            case SCALE -> {
                Xfconf.applyDynamicResolutionScaling(dryRun, verbose);
                if (reload) Orchestrator.reloadDesktopServices(dryRun, verbose);
            }
            case GENERATE -> {
                if (!Generators.generateAllConfigs(verbose)) System.exit(1);
            }
            case DEPLOY -> {
                Orchestrator.DeployResult result = Orchestrator.deploy(dryRun, verbose, reload, applySettings, generateConfigs);
                if (!result.ok()) System.exit(1);
            }
        }
        System.exit(0);
    }
}
