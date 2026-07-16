package dev.whisperlyric.anotherinventorysort;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages locked slot state for the sorting feature.
 * Locked slots are skipped during sorting.
 * State is persisted per-server in the mod's config directory.
 */
public class LockSlotManager {

    private static final Set<Integer> lockedSlots = new HashSet<>();
    private static Path configDir;
    private static String currentServerId = "singleplayer";

    // Flag to indicate that the mod itself is performing locked slot operations
    // (e.g., auto-pickup protection moving items out of locked slots).
    // When true, the Mixin interceptors should allow the operation.
    private static boolean processingLockedPickups = false;

    private LockSlotManager() {}

    public static void init(Path dir) {
        configDir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            // ignore
        }
    }

    public static void setServerId(String serverId) {
        if (!currentServerId.equals(serverId)) {
            save();
            currentServerId = serverId;
            load();
        }
    }

    /**
     * Check if an inventory slot index is lockable.
     * Lockable slots: hotbar (0-8), main inventory (9-35), armor (36-39), offhand (40).
     * Not lockable: crafting result (-1), etc.
     */
    public static boolean isLockableSlot(int invSlot) {
        return (invSlot >= 0 && invSlot <= 39) || invSlot == 40;
    }

    public static boolean isSlotLocked(int invSlot) {
        return lockedSlots.contains(invSlot);
    }

    public static void toggleSlot(int invSlot) {
        if (lockedSlots.contains(invSlot)) {
            lockedSlots.remove(invSlot);
        } else {
            lockedSlots.add(invSlot);
        }
        save();
    }

    public static void lockSlot(int invSlot) {
        lockedSlots.add(invSlot);
        save();
    }

    public static void unlockSlot(int invSlot) {
        lockedSlots.remove(invSlot);
        save();
    }

    public static Set<Integer> getLockedSlots() {
        return Collections.unmodifiableSet(lockedSlots);
    }

    public static boolean isProcessingLockedPickups() {
        return processingLockedPickups;
    }

    public static void setProcessingLockedPickups(boolean processing) {
        processingLockedPickups = processing;
    }

    public static void clearAll() {
        lockedSlots.clear();
        save();
    }

    // Persistence

    private static Path getConfigFile() {
        if (configDir == null) return null;
        String safeName = currentServerId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return configDir.resolve("locked_slots_" + safeName + ".txt");
    }

    public static void save() {
        Path file = getConfigFile();
        if (file == null) return;

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            for (int slot : lockedSlots) {
                writer.write(String.valueOf(slot));
                writer.newLine();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public static void load() {
        lockedSlots.clear();
        Path file = getConfigFile();
        if (file == null || !Files.exists(file)) return;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    try {
                        lockedSlots.add(Integer.parseInt(line));
                    } catch (NumberFormatException e) {
                        // skip invalid lines
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
    }
}
