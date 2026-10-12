package dev.petrolal.dotfiles.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XfconfXmlTest {

    @Test
    void parsesScalarAndArrayProperties(@TempDir Path tempDir) throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <channel name="xfce4-panel" version="1.0">
                  <property name="panels" type="empty">
                    <property name="panel-1" type="empty">
                      <property name="size" type="uint" value="32"/>
                      <property name="plugin-ids" type="array">
                        <value type="int" value="1"/>
                        <value type="int" value="2"/>
                      </property>
                    </property>
                  </property>
                </channel>
                """;
        Path xmlFile = tempDir.resolve("xfce4-panel.xml");
        Files.writeString(xmlFile, xml);

        XfconfXml.Node rootNode = XfconfXml.loadXfconfXml(xmlFile);
        assertEquals("channel", rootNode.name());
        assertEquals("xfce4-panel", rootNode.attr("name"));

        XfconfXml.Extracted extracted = XfconfXml.extractXfconfSettings(rootNode);
        assertEquals("xfce4-panel", extracted.channelName());

        List<XfconfXml.Setting> settings = extracted.settings();
        assertEquals(2, settings.size());

        assertTrue(settings.stream().anyMatch(s -> s instanceof XfconfXml.Setting.Scalar sc
                && sc.path().equals("/panels/panel-1/size")
                && sc.type().equals("uint")
                && sc.value().equals("32")));

        assertTrue(settings.stream().anyMatch(s -> s instanceof XfconfXml.Setting.Array arr
                && arr.path().equals("/panels/panel-1/plugin-ids")
                && arr.type().equals("int")
                && arr.values().equals(List.of("1", "2"))));
    }
}
