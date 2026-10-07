// Main.java --- Abyssal Biopunk / Infernal Retro dotfiles deployer and desktop orchestrator
// License: GPL-3.0-or-later
//
// Single-file, zero-dependency Java 21 port of the Common Lisp deployer
// (dotfiles.asd, src/*.lisp). Builds as a GraalVM native-image binary.

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Main {

    // ------------------------------------------------------------------
    // Result<T> / DotfileError — functional error handling
    // ------------------------------------------------------------------

    sealed interface Result<T> permits Result.Ok, Result.Err {
        record Ok<T>(T value) implements Result<T> {}
        record Err<T>(DotfileError error) implements Result<T> {}

        default boolean isOk() {
            return this instanceof Ok<T>;
        }
    }

    sealed interface DotfileError
            permits DotfileError.CommandFailed, DotfileError.FileNotFound,
            DotfileError.IoError, DotfileError.ParseError {
        record CommandFailed(String command, int exitCode) implements DotfileError {}
        record FileNotFound(Path path) implements DotfileError {}
        record IoError(String message) implements DotfileError {}
        record ParseError(String message) implements DotfileError {}

        default String describe() {
            if (this instanceof CommandFailed c) return "command failed (" + c.exitCode() + "): " + c.command();
            if (this instanceof FileNotFound f) return "file not found: " + f.path();
            if (this instanceof IoError i) return "I/O error: " + i.message();
            if (this instanceof ParseError p) return "parse error: " + p.message();
            throw new IllegalStateException("unreachable");
        }
    }

    // ------------------------------------------------------------------
    // Domain records
    // ------------------------------------------------------------------

    /** A (from -> to) path pair: used both for root-target prefixes and resolved mappings. */
    record Mapping(String from, String to) {}

    record XfceSetting(String channel, String property, String type, String value) {}

    record GnomeSetting(String schema, String key, String value) {}

    record DisplayInfo(String output, boolean primary, int width, int height) {}

    record PrimaryResolution(int width, int height, String output) {}

    enum UnlinkOutcome { SKIPPED, REMOVED, FAILED }

    record DeployResult(boolean ok, int failures, int successes) {}

    record UninstallResult(boolean ok, int failures, int removed) {}

    // ------------------------------------------------------------------
    // Shell — minimal-allocation ProcessBuilder wrapper
    // ------------------------------------------------------------------

    static final class Shell {
        private Shell() {}

        static Result<String> captureOutput(List<String> cmd) {
            try {
                Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                p.getErrorStream().readAllBytes();
                p.waitFor();
                return new Result.Ok<>(out);
            } catch (IOException e) {
                return new Result.Err<>(new DotfileError.IoError(e.getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Result.Err<>(new DotfileError.IoError("interrupted"));
            }
        }

        /** Runs CMD to completion, discarding output. Exit status is not treated as failure
         * (mirrors uiop:run-program :ignore-error-status t — callers that care check themselves). */
        static Result<Integer> run(List<String> cmd) {
            try {
                Process p = new ProcessBuilder(cmd)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                return new Result.Ok<>(p.waitFor());
            } catch (IOException e) {
                return new Result.Err<>(new DotfileError.IoError(e.getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Result.Err<>(new DotfileError.IoError("interrupted"));
            }
        }

        /** Fire-and-forget launch. ProcessBuilder.start() without waitFor() genuinely detaches
         * in the JVM (unlike SBCL's uiop:run-program :wait nil, which blocked empirically). */
        static void spawnDetached(List<String> cmd) {
            try {
                new ProcessBuilder(cmd)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                        .start();
            } catch (IOException ignored) {
                // best-effort: service reload is not fatal to deployment
            }
        }
    }

    // ------------------------------------------------------------------
    // Paths — path/environment utilities
    // ------------------------------------------------------------------

    static final class Paths_ {
        private Paths_() {}

        static Path userHomeDirectory() {
            String home = System.getenv("HOME");
            return Paths.get(home != null ? home : System.getProperty("user.home"));
        }

        private static Path normalizeDirParent(Path dir) {
            Path last = dir.getFileName();
            if (last != null && (last.toString().equals("src") || last.toString().equals("bin"))) {
                Path parent = dir.getParent();
                return parent != null ? parent : dir;
            }
            return dir;
        }

        static Path findDotfilesRoot() {
            String envDir = System.getenv("DOTFILES_DIR");
            if (envDir != null) {
                Path p = Paths.get(envDir);
                if (Files.exists(p)) {
                    try {
                        return p.toRealPath();
                    } catch (IOException ignored) {
                        return p.toAbsolutePath().normalize();
                    }
                }
            }
            Optional<String> exePath = ProcessHandle.current().info().command();
            if (exePath.isPresent()) {
                Path exe = Paths.get(exePath.get());
                if (Files.exists(exe)) {
                    try {
                        Path real = exe.toRealPath();
                        Path dir = real.getParent();
                        if (dir != null) {
                            return normalizeDirParent(dir);
                        }
                    } catch (IOException ignored) {
                        // fall through to cwd
                    }
                }
            }
            return Paths.get("").toAbsolutePath().normalize();
        }

        static boolean commandExists(String cmd) {
            if (cmd == null || cmd.isEmpty()) return false;
            if (cmd.indexOf('/') >= 0) {
                Path p = Paths.get(cmd);
                return Files.isRegularFile(p) && Files.isExecutable(p);
            }
            String pathEnv = System.getenv("PATH");
            if (pathEnv == null) return false;
            for (String dir : pathEnv.split(":")) {
                if (dir.isEmpty()) continue;
                Path candidate = Paths.get(dir).resolve(cmd);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Linker — symlink discovery/creation/removal
    // ------------------------------------------------------------------

    static final class Linker {
        private Linker() {}

        /** Source prefixes (relative to the repo root) auto-discovered and mirrored
         * file-by-file under the given target base (relative to $HOME). Longest
         * matching prefix wins, so a more specific entry can override a general one. */
        static final List<Mapping> ROOT_TARGETS = List.of(
                // Open Display standalone: embedded in the Settings Manager it renders blank,
                // so it lives under applications/ and needs its own target root.
                new Mapping("config/applications", ".local/share/applications"),
                new Mapping("config", ".config"),
                new Mapping("fonts", ".local/share/fonts"),
                new Mapping("themes/imp98", ".local/share/themes/imp98"),
                // Universal asset store (icons/images shared across gtk-3.0, xfce4-panel, etc.)
                new Mapping("assets/imp98", ".local/share/imp98")
        );

        /** Source paths skipped entirely during auto-discovery; a listed directory
         * is not descended into. */
        static final List<String> EXCLUDES = List.of(
                "config/gtk-2.0/gtkrc",
                "config/gtk-3.0/libwrapper-menu-fix.so",
                "config/gtk-3.0/wrapper-menu-fix.c"
        );

        /** Explicit (source -> target) pairs for paths that don't fit the generic
         * root-mirrored layout: renames, or a source linked to more than one target. */
        static final List<Mapping> OVERRIDES = List.of(
                new Mapping("config/gtk-2.0/gtkrc", ".gtkrc-2.0"),
                new Mapping("themes/icons", ".local/share/icons/imp98"),
                new Mapping("themes/icons", ".icons/imp98"),
                // gtk.css references images via a relative url("assets/...") — this keeps
                // that resolving without editing the CSS; assets/imp98 in the repo stays
                // the single canonical source (see ROOT_TARGETS above).
                new Mapping("assets/imp98", ".config/gtk-3.0/assets")
        );

        private static final String XFCONF_EXPORT_MARKER = "xfconf/xfce-perchannel-xml";

        /** True if relpath is (or is under) an xfce-perchannel-xml export directory.
         * Those hold live settings exports: never symlinked (the running session
         * clobbers them), instead auto-applied via xfconf-query. */
        static boolean xfconfExportDirP(String relpath) {
            int pos = relpath.indexOf(XFCONF_EXPORT_MARKER);
            return pos >= 0 && (pos == 0 || relpath.charAt(pos - 1) == '/');
        }

        static boolean excludedP(String relpath) {
            if (xfconfExportDirP(relpath)) return true;
            for (String prefix : EXCLUDES) {
                if (relpath.equals(prefix)) return true;
                if (relpath.length() > prefix.length()
                        && relpath.startsWith(prefix)
                        && relpath.charAt(prefix.length()) == '/') {
                    return true;
                }
            }
            return false;
        }

        static boolean prefixMatchP(String prefix, String relpath) {
            return relpath.length() >= prefix.length()
                    && relpath.startsWith(prefix)
                    && (relpath.length() == prefix.length() || relpath.charAt(prefix.length()) == '/');
        }

        static Optional<Mapping> rootTargetFor(String relpath) {
            Mapping best = null;
            for (Mapping entry : ROOT_TARGETS) {
                if (prefixMatchP(entry.from(), relpath) && (best == null || entry.from().length() > best.from().length())) {
                    best = entry;
                }
            }
            return Optional.ofNullable(best);
        }

        /** ROOT_TARGETS prefixes not nested inside another prefix — the minimal
         * set of directories that need walking once. */
        static List<String> topLevelRoots() {
            List<String> prefixes = ROOT_TARGETS.stream().map(Mapping::from).toList();
            List<String> out = new ArrayList<>();
            for (String prefix : prefixes) {
                boolean nested = prefixes.stream().anyMatch(other -> !other.equals(prefix) && prefixMatchP(other, prefix));
                if (!nested) out.add(prefix);
            }
            return out;
        }

        /** Recursively lists every non-excluded file under relpath (relative to root). */
        static List<String> scanTree(String relpath, Path root) {
            List<String> out = new ArrayList<>();
            Path dir = root.resolve(relpath);
            if (!Files.isDirectory(dir)) return out;
            List<Path> entries;
            try (var stream = Files.list(dir)) {
                entries = stream.sorted().toList();
            } catch (IOException e) {
                return out;
            }
            for (Path entry : entries) {
                String childRel = relpath + "/" + entry.getFileName();
                if (excludedP(childRel)) continue;
                if (Files.isDirectory(entry)) {
                    out.addAll(scanTree(childRel, root));
                } else {
                    out.add(childRel);
                }
            }
            return out;
        }

        /** Builds the full (source -> target) list: OVERRIDES plus every file
         * auto-discovered under the ROOT_TARGETS prefixes. */
        static List<Mapping> collectAllMappings(Path root) {
            List<Mapping> out = new ArrayList<>(OVERRIDES);
            for (String top : topLevelRoots()) {
                for (String fileRel : scanTree(top, root)) {
                    rootTargetFor(fileRel).ifPresent(entry ->
                            out.add(new Mapping(fileRel, entry.to() + fileRel.substring(entry.from().length()))));
                }
            }
            return out;
        }

        static boolean symlinkP(Path path) {
            return Files.isSymbolicLink(path);
        }

        /** True if PATH exists on disk and is a directory (symlink or not), mirroring `test -d`. */
        static boolean directoryExistsP(Path path) {
            return Files.isDirectory(path);
        }

        private static void deleteRecursively(Path path) throws IOException {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        /** Ensures DIR (under HOME) exists as a real directory, replacing any symlinked
         * ancestor with a real one first. Needed because an earlier whole-directory link
         * scheme left some of these as symlinks straight into the repo; leaving such an
         * ancestor in place would make a new leaf symlink resolve back onto its own source. */
        static void ensureRealDirectory(Path dir, Path home) throws IOException {
            Path rel;
            try {
                rel = home.relativize(dir);
            } catch (IllegalArgumentException e) {
                Files.createDirectories(dir);
                return;
            }
            Path current = home;
            for (Path part : rel) {
                current = current.resolve(part);
                if (Files.isSymbolicLink(current)) {
                    Files.delete(current);
                }
            }
            Files.createDirectories(dir);
        }

        /** Atomically points DEST at TARGET: create a temp symlink beside DEST, then rename
         * it over DEST. Avoids the window where DEST is briefly missing that a plain
         * delete-then-create would have. */
        private static void atomicSymlink(Path dest, Path target) throws IOException {
            Path parent = dest.getParent();
            Path tmp = Files.createTempFile(parent, dest.getFileName().toString(), ".tmp-symlink");
            Files.delete(tmp);
            Files.createSymbolicLink(tmp, target);
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }

        static Result<Void> linkFile(String sourceRel, String targetRel, Path root, Path home,
                                      boolean dryRun, boolean verbose) {
            Path src = root.resolve(sourceRel);
            Path dest = home.resolve(targetRel);
            if (!Files.exists(src)) {
                if (verbose) System.err.println("[SKIP] Source missing: " + src);
                return new Result.Err<>(new DotfileError.FileNotFound(src));
            }
            if (dryRun) {
                System.out.println("[DRY-RUN] Would link: " + dest + " -> " + src);
                return new Result.Ok<>(null);
            }
            try {
                ensureRealDirectory(dest.getParent(), home);
                // If SRC is a directory and DEST already exists as a real (non-symlink) directory
                // — e.g. left over from before this path became a directory-level link — a plain
                // symlink rename can't replace it; clear it out first so the symlink lands at DEST.
                if (directoryExistsP(src) && directoryExistsP(dest) && !symlinkP(dest)) {
                    deleteRecursively(dest);
                }
                atomicSymlink(dest, src);
                if (verbose) System.out.println("[OK] Linked: " + dest + " -> " + src);
                return new Result.Ok<>(null);
            } catch (IOException e) {
                System.err.println("[FAIL] Failed to link " + dest + " -> " + src);
                return new Result.Err<>(new DotfileError.IoError(e.getMessage()));
            }
        }

        static UnlinkOutcome unlinkFile(String targetRel, Path home, boolean dryRun, boolean verbose) {
            Path dest = home.resolve(targetRel);
            if (!symlinkP(dest)) {
                if (verbose) {
                    if (Files.exists(dest)) {
                        System.err.println("[SKIP] Not a symlink: " + dest + " (refusing to delete)");
                    } else {
                        System.out.println("[SKIP] Symlink does not exist: " + dest);
                    }
                }
                return UnlinkOutcome.SKIPPED;
            }
            if (dryRun) {
                System.out.println("[DRY-RUN] Would remove symlink: " + dest);
                return UnlinkOutcome.REMOVED;
            }
            try {
                Files.delete(dest);
                if (verbose) System.out.println("[OK] Removed symlink: " + dest);
                return UnlinkOutcome.REMOVED;
            } catch (IOException e) {
                System.err.println("[FAIL] Failed to remove symlink " + dest);
                return UnlinkOutcome.FAILED;
            }
        }
    }

    // ------------------------------------------------------------------
    // Display — xrandr-based resolution detection
    // ------------------------------------------------------------------

    static final class Display {
        private Display() {}

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
            if (!Paths_.commandExists("xrandr")) return List.of();
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

    // ------------------------------------------------------------------
    // Generators — templated config file generation
    // ------------------------------------------------------------------

    static final class Generators {
        private Generators() {}

        /** Ensures TARGET exists with CONTENT, skipping the rewrite if unchanged. */
        static Result<Void> ensureFileContent(Path target, String content, boolean dryRun, boolean verbose) {
            if (dryRun) {
                System.out.println("[DRY-RUN] Would generate " + target);
                return new Result.Ok<>(null);
            }
            try {
                if (Files.exists(target)) {
                    String existing = Files.readString(target);
                    if (existing.equals(content)) {
                        if (verbose) System.out.println("[UP-TO-DATE] Generated config unchanged: " + target);
                        return new Result.Ok<>(null);
                    }
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, content);
                if (verbose) System.out.println("[GEN] Generated: " + target);
                return new Result.Ok<>(null);
            } catch (IOException e) {
                System.err.println("[FAIL] Failed generating " + target + ": " + e.getMessage());
                return new Result.Err<>(new DotfileError.IoError(e.getMessage()));
            }
        }

        /** Currently a no-op: every config file under config/ is a plain tracked dotfile
         * handled by the symlink mechanism (Linker) rather than generated content. This
         * hook exists for a future config that genuinely needs to be derived (e.g. from
         * theme colors) rather than duplicated verbatim. */
        static boolean generateAllConfigs(boolean verbose) {
            if (verbose) System.out.println("Ensuring templated configuration assets... (none defined)");
            return true;
        }
    }

    // ------------------------------------------------------------------
    // XfconfXml — hand-rolled xfce-perchannel-xml parser
    // ------------------------------------------------------------------

    static final class XfconfXml {
        private XfconfXml() {}

        record Node(String name, Map<String, String> attrs, List<Node> children) {
            String attr(String key) {
                return attrs.get(key);
            }
        }

        sealed interface Setting permits Setting.Scalar, Setting.Array {
            record Scalar(String path, String type, String value) implements Setting {}
            record Array(String path, String type, List<String> values) implements Setting {}
        }

        private static final String[][] ENTITIES = {
                {"&lt;", "<"}, {"&gt;", ">"}, {"&quot;", "\""}, {"&apos;", "'"}, {"&amp;", "&"}
        };

        /** xfconf's own exporter escapes attribute values on write, so they must be
         * unescaped before being replayed via xfconf-query, or the escaped form is
         * written back verbatim. */
        private static String decodeEntities(String s) {
            for (String[] pair : ENTITIES) {
                s = s.replace(pair[0], pair[1]);
            }
            return s;
        }

        private static int skipWs(String s, int i) {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            return i;
        }

        private record AttrsResult(Map<String, String> attrs, int end, boolean selfClosing) {}

        private static AttrsResult parseAttrs(String s, int i) {
            Map<String, String> attrs = new LinkedHashMap<>();
            boolean selfClosing = false;
            while (true) {
                i = skipWs(s, i);
                if (i + 1 < s.length() && s.charAt(i) == '/' && s.charAt(i + 1) == '>') {
                    selfClosing = true;
                    i += 2;
                    break;
                }
                if (s.charAt(i) == '>') {
                    i++;
                    break;
                }
                int eqPos = s.indexOf('=', i);
                String name = s.substring(i, eqPos).trim();
                char quoteChar = s.charAt(eqPos + 1);
                int valStart = eqPos + 2;
                int valEnd = s.indexOf(quoteChar, valStart);
                attrs.put(name, decodeEntities(s.substring(valStart, valEnd)));
                i = valEnd + 1;
            }
            return new AttrsResult(attrs, i, selfClosing);
        }

        private record ParseResult(Node node, int end) {}

        private static ParseResult parseElement(String s, int i) {
            int nameStart = i + 1;
            int nameEnd = nameStart;
            while (nameEnd < s.length() && " \t\n\r>/".indexOf(s.charAt(nameEnd)) < 0) nameEnd++;
            String name = s.substring(nameStart, nameEnd);
            AttrsResult attrsResult = parseAttrs(s, nameEnd);
            if (attrsResult.selfClosing()) {
                return new ParseResult(new Node(name, attrsResult.attrs(), List.of()), attrsResult.end());
            }
            List<Node> children = new ArrayList<>();
            int pos = attrsResult.end();
            while (true) {
                pos = skipWs(s, pos);
                int lt = s.indexOf('<', pos);
                if (lt < 0) {
                    throw new IllegalStateException("Malformed xfconf export XML: unterminated element " + name);
                }
                if (s.charAt(lt + 1) == '/') {
                    int closeEnd = s.indexOf('>', lt);
                    pos = closeEnd + 1;
                    break;
                }
                ParseResult child = parseElement(s, lt);
                children.add(child.node());
                pos = child.end();
            }
            return new ParseResult(new Node(name, attrsResult.attrs(), children), pos);
        }

        /** Parses an xfce-perchannel-xml file. Returns the root <channel> node, skipping
         * the <?xml ...?> prolog. */
        static Node loadXfconfXml(Path path) throws IOException {
            String text = Files.readString(path);
            int start = text.indexOf('<');
            if (start >= 0 && start + 1 < text.length() && text.charAt(start + 1) == '?') {
                int end = text.indexOf("?>", start);
                start = text.indexOf('<', end + 2);
            }
            return parseElement(text, start).node();
        }

        /** Accumulates SETTINGS by walking NODE's own value (if any) and recursing into
         * every nested <property> child, extending PATH with each child's name. */
        private static void walkXfconfNode(Node node, String path, List<Setting> settings) {
            String type = node.attr("type");
            String value = node.attr("value");
            if (type != null && type.equals("array")) {
                List<String[]> values = new ArrayList<>();
                for (Node c : node.children()) {
                    if (c.name().equals("value")) {
                        values.add(new String[]{c.attr("type"), c.attr("value")});
                    }
                }
                if (!values.isEmpty()) {
                    settings.add(new Setting.Array(path, values.get(0)[0], values.stream().map(v -> v[1]).toList()));
                }
            } else if (type != null && !type.equals("empty") && value != null) {
                settings.add(new Setting.Scalar(path, type, value));
            }
            for (Node c : node.children()) {
                if (c.name().equals("property")) {
                    walkXfconfNode(c, path + "/" + c.attr("name"), settings);
                }
            }
        }

        record Extracted(String channelName, List<Setting> settings) {}

        static Extracted extractXfconfSettings(Node channelNode) {
            List<Setting> settings = new ArrayList<>();
            for (Node c : channelNode.children()) {
                if (c.name().equals("property")) {
                    walkXfconfNode(c, "/" + c.attr("name"), settings);
                }
            }
            return new Extracted(channelNode.attr("name"), settings);
        }

        static void applyExportedXfconfFile(Path path, boolean dryRun, boolean verbose) {
            try {
                Extracted extracted = extractXfconfSettings(loadXfconfXml(path));
                for (Setting s : extracted.settings()) {
                    if (s instanceof Setting.Scalar sc) {
                        Xfconf.setXfconf(extracted.channelName(), sc.path(), sc.type(), sc.value(), dryRun);
                    } else if (s instanceof Setting.Array arr) {
                        Xfconf.setXfconfArray(extracted.channelName(), arr.path(), arr.type(), arr.values(), dryRun, verbose);
                    }
                }
            } catch (IOException e) {
                System.err.println("[FAIL] Failed parsing exported xfconf file " + path + ": " + e.getMessage());
            }
        }

        /** Lists every xfce-perchannel-xml export (*.xml) under ROOT's config/ tree. */
        static List<Path> findExportedXfconfFiles(Path root) {
            List<Path> out = new ArrayList<>();
            Path configDir = root.resolve("config");
            if (!Files.isDirectory(configDir)) return out;
            try (var stream = Files.walk(configDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            int dot = name.lastIndexOf('.');
                            return dot >= 0 && name.substring(dot + 1).equalsIgnoreCase("xml");
                        })
                        .forEach(out::add);
            } catch (IOException ignored) {
                // nothing to apply
            }
            return out;
        }

        static void applyExportedXfconfFiles(Path root, boolean dryRun, boolean verbose) {
            for (Path f : findExportedXfconfFiles(root)) {
                if (verbose) System.out.println("Applying exported xfconf settings: " + f);
                applyExportedXfconfFile(f, dryRun, verbose);
            }
        }
    }

    // ------------------------------------------------------------------
    // Xfconf — XFCE / GNOME desktop settings
    // ------------------------------------------------------------------

    static final class Xfconf {
        private Xfconf() {}

        static final List<XfceSetting> XFCE_SETTINGS = List.of(
                // GTK and Theme Configuration (Adwaita-dark / imp98)
                new XfceSetting("xsettings", "/Net/ThemeName", "string", "Adwaita-dark"),
                new XfceSetting("xsettings", "/Net/IconThemeName", "string", "imp98"),
                new XfceSetting("xsettings", "/Gtk/CursorThemeSize", "int", "24"),
                new XfceSetting("xsettings", "/Gtk/FontName", "string", "W95FA 10"),
                new XfceSetting("xsettings", "/Gtk/ApplicationPreferDarkTheme", "bool", "true"),

                // Notification Daemon Styling (xfce4-notifyd: 100% solid opacity)
                new XfceSetting("xfce4-notifyd", "/initial-opacity", "double", "1.0"),
                new XfceSetting("xfce4-notifyd", "/notify-location", "int", "2"),

                // Window Manager Theme & Behavior (Windows 98)
                new XfceSetting("xfwm4", "/general/theme", "string", "imp98"),
                new XfceSetting("xfwm4", "/general/title_font", "string", "W95FA Bold 10"),
                new XfceSetting("xfwm4", "/general/button_layout", "string", "O|HMC"),
                new XfceSetting("xfwm4", "/general/button_spacing", "int", "2"),
                new XfceSetting("xfwm4", "/general/button_offset", "int", "3"),
                new XfceSetting("xfwm4", "/general/title_alignment", "string", "left"),
                new XfceSetting("xfwm4", "/general/full_width_title", "bool", "true"),
                new XfceSetting("xfwm4", "/general/borderless_maximize", "bool", "true"),
                new XfceSetting("xfwm4", "/general/easy_click", "string", "Super"),
                new XfceSetting("xfwm4", "/general/snap_to_windows", "bool", "true"),
                new XfceSetting("xfwm4", "/general/snap_to_border", "bool", "true"),
                new XfceSetting("xfwm4", "/general/snap_width", "int", "10"),

                // Compositor: Hard-edged drop shadow (offset 2 2, 95% opacity), 100% opacity, zero animations
                new XfceSetting("xfwm4", "/general/use_compositing", "bool", "true"),
                new XfceSetting("xfwm4", "/general/shadow_delta_x", "int", "2"),
                new XfceSetting("xfwm4", "/general/shadow_delta_y", "int", "2"),
                new XfceSetting("xfwm4", "/general/shadow_opacity", "int", "95"),
                new XfceSetting("xfwm4", "/general/shadow_delta_width", "int", "0"),
                new XfceSetting("xfwm4", "/general/shadow_delta_height", "int", "0"),
                new XfceSetting("xfwm4", "/general/show_frame_shadow", "bool", "true"),
                new XfceSetting("xfwm4", "/general/show_popup_shadow", "bool", "true"),
                new XfceSetting("xfwm4", "/general/show_dock_shadow", "bool", "false"),
                new XfceSetting("xfwm4", "/general/frame_opacity", "int", "100"),
                new XfceSetting("xfwm4", "/general/inactive_opacity", "int", "100"),

                // Desktop Screen Margins: strictly 0px to ensure flush window tiling and full maximization
                new XfceSetting("xfwm4", "/general/margin_bottom", "int", "0"),
                new XfceSetting("xfwm4", "/general/margin_left", "int", "0"),
                new XfceSetting("xfwm4", "/general/margin_right", "int", "0"),
                new XfceSetting("xfwm4", "/general/margin_top", "int", "0"),

                // NOTE: xfce4-panel settings (panel geometry + all plugin-N properties) are
                // not listed here — they're auto-applied from the exported
                // config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml via
                // XfconfXml.applyExportedXfconfFiles. Edit that XML (or just use the XFCE
                // panel UI and re-export it) instead of this list.

                // Hyprland-adapted Keybindings (Super+Return, Super+Q, Super+F, Workspaces 1-4)
                new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/<Super>Return", "string", "xfce4-terminal"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/<Primary><Alt>t", "string", "xfce4-terminal"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/<Super>e", "string", "thunar"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/<Shift><Super>s", "string", "xfce4-screenshooter -r"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>q", "string", "close_window_key"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>f", "string", "fullscreen_key"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>1", "string", "workspace_1_key"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>2", "string", "workspace_2_key"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>3", "string", "workspace_3_key"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>4", "string", "workspace_4_key"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Shift><Super>1", "string", "move_window_workspace_1_key"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Shift><Super>2", "string", "move_window_workspace_2_key"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Shift><Super>3", "string", "move_window_workspace_3_key"),
                new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Shift><Super>4", "string", "move_window_workspace_4_key")
        );

        static final List<GnomeSetting> GNOME_SETTINGS = List.of(
                new GnomeSetting("org.gnome.desktop.interface", "icon-theme", "imp98"),
                new GnomeSetting("org.gnome.desktop.interface", "gtk-theme", "Adwaita-dark"),
                new GnomeSetting("org.gnome.desktop.interface", "font-name", "W95FA 10"),
                new GnomeSetting("org.gnome.desktop.interface", "color-scheme", "prefer-dark"),
                new GnomeSetting("org.gnome.desktop.wm.preferences", "titlebar-font", "W95FA Bold 10"),
                new GnomeSetting("org.gnome.desktop.wm.preferences", "button-layout", "close:maximize")
        );

        /** Expands a leading ~/ to $HOME. xfconf-query passes values straight through to
         * the consuming app/library with no shell in between, so a literal ~/ never expands
         * on its own and silently fails to resolve. */
        static String expandHomeTilde(String valStr) {
            if (valStr.length() >= 2 && valStr.startsWith("~/")) {
                String home = Paths_.userHomeDirectory().toString();
                String trimmed = home.endsWith("/") ? home.substring(0, home.length() - 1) : home;
                return trimmed + valStr.substring(1);
            }
            return valStr;
        }

        static void setXfconf(String channel, String property, String type, String value, boolean dryRun) {
            String valStr = expandHomeTilde(value);
            if (dryRun) {
                System.out.printf("[DRY-RUN] xfconf-query -c %s -p %s -t %s -s %s%n", channel, property, type, valStr);
            } else {
                Shell.run(List.of("xfconf-query", "-c", channel, "-p", property, "-s", valStr, "--create", "-t", type));
            }
        }

        static void setXfconfArray(String channel, String property, String type, List<String> values,
                                    boolean dryRun, boolean verbose) {
            List<String> cmd = new ArrayList<>(List.of("xfconf-query", "-c", channel, "-p", property, "--create", "-a"));
            for (String v : values) {
                cmd.add("-t");
                cmd.add(type);
                cmd.add("-s");
                cmd.add(expandHomeTilde(v));
            }
            if (dryRun) {
                System.out.println("[DRY-RUN] " + String.join(" ", cmd));
            } else {
                if (verbose) System.out.println("[OK] Setting " + channel + " " + property + " to " + values);
                Shell.run(cmd);
            }
        }

        /** Detects the current display resolution, sets the panel to 44px height
         * and enforces 0px window manager margins via xfconf. */
        static void applyDynamicResolutionScaling(boolean dryRun, boolean verbose) {
            PrimaryResolution res = Display.determinePrimaryResolution();
            if (verbose) {
                System.out.printf("Display detection: %dx%d%s -> Panel: 44px, WM margins: 0px%n",
                        res.width(), res.height(), res.output() != null ? " (" + res.output() + ")" : "");
            }
            // 43 renders as exactly 44px.
            setXfconf("xfce4-panel", "/panels/panel-1/size", "uint", "43", dryRun);
            setXfconf("xfwm4", "/general/margin_bottom", "int", "0", dryRun);
            setXfconf("xfwm4", "/general/margin_left", "int", "0", dryRun);
            setXfconf("xfwm4", "/general/margin_right", "int", "0", dryRun);
            setXfconf("xfwm4", "/general/margin_top", "int", "0", dryRun);
        }

        static void removePanelDock(boolean dryRun, boolean verbose) {
            if (verbose) System.out.println("Ensuring bottom dock panel is removed...");
            if (dryRun) {
                System.out.println("[DRY-RUN] xfconf-query -c xfce4-panel -p /panels -a -t int -s 1");
            } else {
                Shell.run(List.of("xfconf-query", "-c", "xfce4-panel", "-p", "/panels", "-a", "-t", "int", "-s", "1"));
                Shell.run(List.of("xfconf-query", "-c", "xfce4-panel", "-p", "/panels/panel-2", "-r", "-R"));
            }
        }

        private static void runGsettings(String schema, String key, String value, boolean dryRun) {
            if (dryRun) {
                System.out.printf("[DRY-RUN] gsettings set %s %s '%s'%n", schema, key, value);
            } else {
                Shell.run(List.of("gsettings", "set", schema, key, value));
            }
        }

        static void applyGnomeSettings(boolean dryRun, boolean verbose) {
            if (!Paths_.commandExists("gsettings")) return;
            if (verbose) System.out.println("Syncing GSettings for GNOME/GTK apps...");
            for (GnomeSetting s : GNOME_SETTINGS) {
                runGsettings(s.schema(), s.key(), s.value(), dryRun);
            }
        }

        static void applyXfceSettings(Path root, boolean dryRun, boolean verbose) {
            if (!Paths_.commandExists("xfconf-query")) {
                if (verbose) System.out.println("[SKIP] xfconf-query not found, skipping desktop theme configuration.");
                return;
            }
            if (verbose) System.out.println("Applying XFCE panel and retro theme settings...");
            removePanelDock(dryRun, verbose);
            for (XfceSetting s : XFCE_SETTINGS) {
                setXfconf(s.channel(), s.property(), s.type(), s.value(), dryRun);
            }
            // Replay every exported xfce-perchannel-xml file found under config/ (e.g.
            // xfce4-panel.xml) — see XfconfXml.
            XfconfXml.applyExportedXfconfFiles(root, dryRun, verbose);
            applyDynamicResolutionScaling(dryRun, verbose);
            applyGnomeSettings(dryRun, verbose);
        }
    }

    // ------------------------------------------------------------------
    // Orchestrator — deployment orchestration
    // ------------------------------------------------------------------

    static final class Orchestrator {
        private Orchestrator() {}

        private static final List<List<String>> RELOAD_COMMANDS = List.of(
                List.of("xfsettingsd", "--replace"),
                List.of("xfce4-panel", "-r"),
                List.of("xfwm4", "--replace"),
                List.of("pkill", "-f", "xfce4-notifyd"),
                List.of("thunar", "-q")
        );

        /** Reloads active XFCE desktop components if running in an X11 session. */
        static void reloadDesktopServices(boolean dryRun, boolean verbose) {
            if (System.getenv("DISPLAY") == null) {
                if (verbose) System.out.println("[SKIP] No DISPLAY available, skipping desktop reload.");
                return;
            }
            if (verbose) System.out.println("Reloading XFCE services...");
            for (List<String> args : RELOAD_COMMANDS) {
                String binary = args.get(0);
                if (!Paths_.commandExists(binary)) {
                    if (verbose) System.out.println("[SKIP] " + binary + " not found, skipping.");
                } else if (dryRun) {
                    System.out.println("[DRY-RUN] Would execute: " + String.join(" ", args));
                } else {
                    Shell.spawnDetached(args);
                }
            }
        }

        static DeployResult deploy(boolean dryRun, boolean verbose, boolean reload,
                                    boolean applySettings, boolean generateConfigs) {
            if (verbose) System.out.println("=== Deploying Abyssal Biopunk / Infernal Retro Dotfiles ===");
            Path root = Paths_.findDotfilesRoot();
            Path home = Paths_.userHomeDirectory();
            int failures = 0;
            int successes = 0;
            if (verbose) {
                System.out.println("Root:   " + root);
                System.out.println("Target: " + home);
            }
            if (generateConfigs) {
                if (Generators.generateAllConfigs(verbose)) successes++; else failures++;
            }
            for (Mapping mapping : Linker.collectAllMappings(root)) {
                Result<Void> r = Linker.linkFile(mapping.from(), mapping.to(), root, home, dryRun, verbose);
                if (r instanceof Result.Ok<Void>) successes++; else failures++;
            }
            if (applySettings) {
                Xfconf.applyXfceSettings(root, dryRun, verbose);
            }
            if (reload && applySettings) {
                reloadDesktopServices(dryRun, verbose);
            }
            if (verbose) {
                if (failures == 0) {
                    System.out.println("Deployment finished successfully.");
                } else {
                    System.err.println("Deployment finished with " + failures + " failure(s).");
                }
            }
            return new DeployResult(failures == 0, failures, successes);
        }

        static UninstallResult uninstall(boolean dryRun, boolean verbose) {
            if (verbose) System.out.println("=== Removing Abyssal Biopunk / Infernal Retro Dotfiles Symlinks ===");
            Path root = Paths_.findDotfilesRoot();
            Path home = Paths_.userHomeDirectory();
            int failures = 0;
            int removedCount = 0;
            if (verbose) System.out.println("Target: " + home);
            for (Mapping mapping : Linker.collectAllMappings(root)) {
                UnlinkOutcome outcome = Linker.unlinkFile(mapping.to(), home, dryRun, verbose);
                if (outcome == UnlinkOutcome.REMOVED) removedCount++;
                else if (outcome == UnlinkOutcome.FAILED) failures++;
            }
            if (verbose) {
                if (dryRun) {
                    System.out.println("Dry-run complete. " + removedCount + " symlink(s) would be removed.");
                } else {
                    String failureSuffix = failures > 0 ? ", " + failures + " failure(s)" : "";
                    System.out.println("Uninstallation complete. " + removedCount + " symlink(s) removed" + failureSuffix + ".");
                }
            }
            return new UninstallResult(failures == 0, failures, removedCount);
        }
    }

    // ------------------------------------------------------------------
    // CLI
    // ------------------------------------------------------------------

    static final class Cli {
        private Cli() {}

        static final String VERSION = "1.2.0";
        static final String PROGRAM_NAME = "invoker";

        private static void printHelp() {
            System.out.printf("Usage: %s [COMMAND|OPTION]...%n%n", PROGRAM_NAME);
            System.out.println("Deploy Abyssal Biopunk dotfiles symlinks and configure desktop settings.\n");
            System.out.println("Commands:");
            System.out.println("  deploy            perform full deployment (generate, link, configure, reload) [default]");
            System.out.println("  uninstall         remove all deployed dotfile symlinks safely");
            System.out.println("  scale             query display resolution and reset panel height and WM margins");
            System.out.println("  generate          generate and verify templated configuration assets\n");
            System.out.println("Options:");
            System.out.println("  -u, --uninstall   remove all deployed dotfile symlinks safely");
            System.out.println("  -s, --scale       detect resolution and reset panel height and WM margins");
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
                    UninstallResult result = Orchestrator.uninstall(dryRun, verbose);
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
                    DeployResult result = Orchestrator.deploy(dryRun, verbose, reload, applySettings, generateConfigs);
                    if (!result.ok()) System.exit(1);
                }
            }
            System.exit(0);
        }
    }

    public static void main(String[] args) {
        Cli.main(args);
    }
}
