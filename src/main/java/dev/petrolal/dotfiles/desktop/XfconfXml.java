// XfconfXml.java --- Standard StAX-based xfce-perchannel-xml parser
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

final class XfconfXml {
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

    private static class NodeBuilder {
        final String name;
        final Map<String, String> attrs;
        final List<Node> children = new ArrayList<>();

        NodeBuilder(String name, Map<String, String> attrs) {
            this.name = name;
            this.attrs = attrs;
        }

        Node build() {
            return new Node(name, attrs, List.copyOf(children));
        }
    }

    /** Parses an xfce-perchannel-xml file into a Node tree using standard StAX. */
    static Node loadXfconfXml(Path path) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        try (InputStream in = Files.newInputStream(path)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            Deque<NodeBuilder> stack = new ArrayDeque<>();
            Node root = null;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    Map<String, String> attrs = new LinkedHashMap<>();
                    int attrCount = reader.getAttributeCount();
                    for (int i = 0; i < attrCount; i++) {
                        attrs.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                    }
                    NodeBuilder builder = new NodeBuilder(name, attrs);
                    stack.push(builder);
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (!stack.isEmpty()) {
                        NodeBuilder builder = stack.pop();
                        Node node = builder.build();
                        if (stack.isEmpty()) {
                            root = node;
                        } else {
                            stack.peek().children.add(node);
                        }
                    }
                }
            }
            if (root == null) {
                throw new IOException("Empty or invalid xfconf XML: " + path);
            }
            return root;
        } catch (XMLStreamException e) {
            throw new IOException("Malformed xfconf XML in " + path + ": " + e.getMessage(), e);
        }
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

    /** Returns the number of settings in PATH that failed to apply (including
     * 1 if the file itself couldn't be parsed). */
    static int applyExportedXfconfFile(Path path, boolean dryRun, boolean verbose) {
        try {
            Extracted extracted = extractXfconfSettings(loadXfconfXml(path));
            int failures = 0;
            for (Setting s : extracted.settings()) {
                if (s instanceof Setting.Scalar sc) {
                    if (!Xfconf.setXfconf(extracted.channelName(), sc.path(), sc.type(), sc.value(), dryRun)) failures++;
                } else if (s instanceof Setting.Array arr) {
                    if (!Xfconf.setXfconfArray(extracted.channelName(), arr.path(), arr.type(), arr.values(), dryRun, verbose)) failures++;
                }
            }
            return failures;
        } catch (IOException e) {
            System.err.println("[FAIL] Failed parsing exported xfconf file " + path + ": " + e.getMessage());
            return 1;
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

    static int applyExportedXfconfFiles(Path root, boolean dryRun, boolean verbose) {
        int failures = 0;
        for (Path f : findExportedXfconfFiles(root)) {
            if (verbose) System.out.println("Applying exported xfconf settings: " + f);
            failures += applyExportedXfconfFile(f, dryRun, verbose);
        }
        return failures;
    }
}
