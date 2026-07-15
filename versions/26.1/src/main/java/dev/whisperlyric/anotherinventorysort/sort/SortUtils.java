package dev.whisperlyric.anotherinventorysort.sort;

import java.util.ArrayList;
import java.util.List;

/**
 * Sort utility functions: pack / distribute / transpose.
 */
public final class SortUtils {
    private SortUtils() {}

    /**
     * Pack a total count into a list of stack sizes.
     * pack(100, 64) → [64, 36]
     * pack(0, 64) → []
     */
    public static List<Integer> pack(int total, int maxCount) {
        List<Integer> result = new ArrayList<>();
        while (total > 0) {
            int stack = Math.min(total, maxCount);
            result.add(stack);
            total -= stack;
        }
        return result;
    }

    /**
     * IPN's distribute algorithm: allocate width into columnsCount column blocks.
     * distribute(9, 2) → [5, 4]
     * distribute(9, 3) → [3, 3, 3]
     */
    public static int[] distribute(int width, int columnsCount) {
        if (columnsCount <= 0) return new int[0];
        int[] result = new int[columnsCount];
        int base = width / columnsCount;
        int remainder = width % columnsCount;
        for (int i = 0; i < columnsCount; i++) {
            result[i] = base + (i < remainder ? 1 : 0);
        }
        return result;
    }

    /**
     * Transpose index: row-major → column-major.
     * transposedIndex(w, h, i) = (i % w) * h + (i / w)
     */
    public static int transposedIndex(int width, int height, int index) {
        return (index % width) * height + (index / width);
    }
}
