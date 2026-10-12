package dev.petrolal.dotfiles.linker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorsTest {

    @Test
    void ensureFileContentGeneratesFileAndSkipsIfUnchanged(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("subdir/test.conf");
        String content = "key=value\n";

        // Dry-run should not create the file
        var dryRunResult = Generators.ensureFileContent(target, content, true, false);
        assertTrue(dryRunResult.isOk());
        assertFalse(Files.exists(target));

        // Real run should create the file
        var realResult = Generators.ensureFileContent(target, content, false, false);
        assertTrue(realResult.isOk());
        assertTrue(Files.exists(target));
        assertEquals(content, Files.readString(target));

        // Re-running with unchanged content should succeed without error
        var reRunResult = Generators.ensureFileContent(target, content, false, false);
        assertTrue(reRunResult.isOk());
        assertEquals(content, Files.readString(target));
    }
}
