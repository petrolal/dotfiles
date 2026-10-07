// Display.java --- xrandr-based resolution detection
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class Display {
    private Display() {}

    record DisplayInfo(String output, boolean primary, int width, int height) {}

    record PrimaryResolution(int width, int height, String output) {}

    /** Parses a "WIDTHxHEIGHT[+x+y]" token out of an xrandr output line. */
    static Optional<int[]> parseDisplayResolution(String line) {
        for (String tok : line.split("[ \t]+")) {
            int xPos = tok.indexOf('x');
            if (xPos > 0 && xPos < tok.length() - 1) {
                String widthStr = tok.substring(0, xPos);
                String restStr = tok.substring(xPos + 1);
                int plusPos = restStr.indexOf('+');
                String heightStr = plusPos >= 0 ? restStr.substring(0, plusPos) : restStr;
                if (!widthStr.isEmpty() && !heightStr.isEmpty()
                        && widthStr.chars().allMatch(Character::isDigit)
                        && heightStr.chars().allMatch(Character::isDigit)) {
                    return Optional.of(new int[]{Integer.parseInt(widthStr), Integer.parseInt(heightStr)});
                }
            }
        }
        return Optional.empty();
    }

    static List<DisplayInfo> detectDisplayResolutions() {
        if (!DotfilePaths.commandExists("xrandr")) return List.of();
        Result<String> result = Shell.captureOutput(List.of("xrandr", "--current"));
        if (!(result instanceof Result.Ok<String> ok)) return List.of();
        List<DisplayInfo> displays = new ArrayList<>();
        for (String line : ok.value().split("\r\n|\n|\r")) {
            if (line.contains(" connected ") && !line.contains(" disconnected ")) {
                Optional<int[]> wh = parseDisplayResolution(line);
                if (wh.isPresent()) {
                    String name = line.split("[ \t]+")[0];
                    boolean primary = line.contains(" primary ");
                    displays.add(new DisplayInfo(name, primary, wh.get()[0], wh.get()[1]));
                }
            }
        }
        return displays;
    }

    /** Prefers the primary connected monitor, otherwise the highest-resolution one,
     * or defaults to 1920x1080 if undetectable. */
    static PrimaryResolution determinePrimaryResolution() {
        List<DisplayInfo> displays = detectDisplayResolutions();
        if (displays.isEmpty()) {
            return new PrimaryResolution(1920, 1080, null);
        }
        Optional<DisplayInfo> primary = displays.stream().filter(DisplayInfo::primary).findFirst();
        if (primary.isPresent()) {
            DisplayInfo d = primary.get();
            return new PrimaryResolution(d.width(), d.height(), d.output());
        }
        DisplayInfo max = displays.stream()
                .max(Comparator.comparingInt(d -> d.width() * d.height()))
                .orElseThrow();
        return new PrimaryResolution(max.width(), max.height(), max.output());
    }
}
