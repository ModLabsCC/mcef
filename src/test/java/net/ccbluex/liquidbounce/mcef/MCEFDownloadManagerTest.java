package net.ccbluex.liquidbounce.mcef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MCEFDownloadManagerTest {

    @TempDir
    Path directory;

    @Test
    void usesModLabsReleaseAssetsForEveryPlatform() {
        var commit = "16324a67197b07e1aab95b6db470ebeab194730b";
        var host = new MCEFSettings().getHosts().getFirst();
        for (var platform : MCEFPlatform.values()) {
            var manager = new MCEFDownloadManager(new String[]{host}, commit, platform, directory.toFile());
            var base = "https://github.com/ModLabsCC/mcef/releases/download/jcef-" + commit + "/"
                    + platform.getNormalizedName() + ".tar.gz";
            assertEquals(base, manager.getJavaCefDownloadUrl());
            assertEquals(base + ".sha256", manager.getJavaCefChecksumDownloadUrl());
        }
    }
}
