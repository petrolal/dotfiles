// Main.java --- Entry point for the Abyssal Biopunk / Infernal Retro dotfiles
// deployer and desktop orchestrator.
// License: GPL-3.0-or-later
//
// Zero-dependency Java 21 CLI, built as a GraalVM native-image binary. See
// Cli.java for argument parsing and Orchestrator.java for the deploy/uninstall
// flow.

public final class Main {
    public static void main(String[] args) {
        Cli.main(args);
    }
}
