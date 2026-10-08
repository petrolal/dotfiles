// XfconfXml.java --- Hand-rolled xfce-perchannel-xml parser
// License: GPL-3.0-or-later
//
// XFCE's own live settings store already writes one XML file per channel
// at ~/.config/xfce4/xfconf/xfce-perchannel-xml/<channel>.xml. Dropping a
// copy of one of those files under config/<app>/xfconf/xfce-perchannel-xml/
// in the repo (see Linker.xfconfExportDirP) is enough to manage that
// channel declaratively: this file parses it and replays every property
// via xfconf-query, with no per-property Java code required.
package dev.petrolal.dotfiles.conf;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class XfconfXml {
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
