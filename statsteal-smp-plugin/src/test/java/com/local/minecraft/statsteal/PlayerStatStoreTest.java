package com.local.minecraft.statsteal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PlayerStatStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void pendingLossSurvivesSaveAndIsDrainedOnce() throws Exception {
        File file = tempDirectory.resolve("player-stats.yml").toFile();
        UUID playerId = UUID.randomUUID();

        PlayerStatStore writer = new PlayerStatStore(file);
        writer.addPendingLoss(playerId, new PendingStatLoss("armor", -1, "DragonXander"));
        writer.save();

        PlayerStatStore reader = new PlayerStatStore(file);
        reader.load();
        assertEquals(
                List.of(new PendingStatLoss("armor", -1, "DragonXander")),
                reader.drainPendingLosses(playerId));
        assertTrue(reader.drainPendingLosses(playerId).isEmpty());
    }
}
