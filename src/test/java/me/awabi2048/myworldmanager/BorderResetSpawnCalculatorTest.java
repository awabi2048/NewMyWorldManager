package me.awabi2048.myworldmanager;

import me.awabi2048.myworldmanager.util.BorderResetBounds;
import me.awabi2048.myworldmanager.util.BorderResetSpawnCalculator;
import me.awabi2048.myworldmanager.util.BorderResetSpawnChanges;
import me.awabi2048.myworldmanager.util.BorderResetSpawnPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class BorderResetSpawnCalculatorTest {
    private final BorderResetBounds border = BorderResetBounds.Companion.centeredAt(0.5, 0.5, 100.0);
    private final BorderResetSpawnPosition defaultPosition = new BorderResetSpawnPosition(0.5, 72.0, 0.5);

    @Test
    void outsideSavedSpawnsMoveToDefaultCoordinates() {
        BorderResetSpawnChanges changes = calculate(
                new BorderResetSpawnPosition(120.5, 80.0, 0.5),
                new BorderResetSpawnPosition(0.5, 80.0, -120.5),
                List.of(72)
        );

        assertEquals(defaultPosition, changes.getGuest().getReplacement());
        assertEquals(defaultPosition, changes.getMember().getReplacement());
    }

    @Test
    void insideAndUnsetSavedSpawnsAreNotChanged() {
        BorderResetSpawnChanges changes = calculate(
                new BorderResetSpawnPosition(10.5, 80.0, 0.5),
                null,
                List.of(72)
        );

        assertNull(changes.getGuest());
        assertNull(changes.getMember());
        assertFalse(changes.getHasChanges());
    }

    @Test
    void firstSafeYKeepsDefaultXAndZ() {
        BorderResetSpawnChanges changes = calculate(
                new BorderResetSpawnPosition(120.5, 80.0, 0.5),
                null,
                List.of(68, 70)
        );

        assertEquals(new BorderResetSpawnPosition(0.5, 68.0, 0.5), changes.getGuest().getReplacement());
    }

    @Test
    void replacementIsClampedInsideResultingBorder() {
        BorderResetSpawnPosition outsideDefault = new BorderResetSpawnPosition(120.5, 72.0, -120.5);
        BorderResetSpawnChanges changes = BorderResetSpawnCalculator.INSTANCE.calculate(
                new BorderResetSpawnPosition(200.5, 80.0, 0.5),
                null,
                outsideDefault,
                border,
                List.of(72)
        );

        assertEquals(new BorderResetSpawnPosition(49.5, 72.0, -48.5), changes.getGuest().getReplacement());
    }

    @Test
    void replacementKeepsOneBlockMarginFromBorderLine() {
        BorderResetBounds narrowBorder = BorderResetBounds.Companion.centeredAt(0.0, 0.0, 4.0);
        BorderResetSpawnChanges changes = BorderResetSpawnCalculator.INSTANCE.calculate(
                new BorderResetSpawnPosition(10.0, 80.0, 0.0),
                null,
                new BorderResetSpawnPosition(10.0, 72.0, 0.0),
                narrowBorder,
                List.of(72)
        );

        assertEquals(new BorderResetSpawnPosition(1.0, 72.0, 0.0), changes.getGuest().getReplacement());
    }

    @Test
    void outsideSavedSpawnsRemainUnchangedWhenNoSafeYExists() {
        BorderResetSpawnChanges changes = calculate(
                new BorderResetSpawnPosition(120.5, 80.0, 0.5),
                null,
                List.of()
        );

        assertNull(changes.getGuest());
    }

    private BorderResetSpawnChanges calculate(
            BorderResetSpawnPosition guest,
            BorderResetSpawnPosition member,
            List<Integer> safeYCoordinates
    ) {
        return BorderResetSpawnCalculator.INSTANCE.calculate(
                guest,
                member,
                defaultPosition,
                border,
                safeYCoordinates
        );
    }
}
