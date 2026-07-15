package dev.whisperlyric.anotherinventorysort.sort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Obstacle-aware group calculator (ported from IPN GroupInColumnsCalculator + locked-slot awareness).
 *
 * ROW mode: each type occupies consecutive whole rows, arranged left-to-right within a row.
 * COLUMN mode: each type occupies consecutive whole columns, arranged top-to-bottom (via transpose).
 * DEFAULT mode: row-major contiguous fill.
 *
 * Locked slots are treated as obstacles: they do not participate in allocation;
 * Cell.room only counts non-locked slots.
 */
public final class GroupCalculator {

    /**
     * Compute target slot allocation.
     *
     * @param groupSizes  list of stack counts per type (already sorted)
     * @param width       container width (column count)
     * @param height      container height (row count)
     * @param lockedSlots set of locked slot indices (within the width×height space)
     * @param mode        sort mode
     * @return list of target slot indices per type; null if allocation fails (caller falls back to DEFAULT)
     */
    public static List<List<Integer>> calculate(List<Integer> groupSizes, int width, int height,
                                                 Set<Integer> lockedSlots, SortMode mode) {
        if (groupSizes.isEmpty() || width <= 0 || height <= 0) {
            return new ArrayList<>();
        }

        return switch (mode) {
            case ROW -> calculateGroup(groupSizes, width, height, lockedSlots, false);
            case COLUMN ->
                // COLUMN = ROW in transposed space + transpose indices back
                    calculateGroup(groupSizes, height, width, lockedSlots, true);
            default -> calculateDefault(groupSizes, width, height, lockedSlots);
        };
    }

    /**
     * DEFAULT mode: row-major contiguous fill, skipping locked slots.
     */
    private static List<List<Integer>> calculateDefault(List<Integer> groupSizes, int width, int height,
                                                         Set<Integer> lockedSlots) {
        List<Integer> available = getAvailableSlots(width, height, lockedSlots);
        List<List<Integer>> result = new ArrayList<>();
        int idx = 0;
        for (int size : groupSizes) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < size && idx < available.size(); i++) {
                slots.add(available.get(idx++));
            }
            result.add(slots);
        }
        return result;
    }

    /**
     * Obstacle-aware grouping core (IPN GroupInColumnsCalculator port + locked-slot awareness).
     *
     * @param transpose true = COLUMN mode (compute in transposed space, then transpose back)
     */
    private static List<List<Integer>> calculateGroup(List<Integer> groupSizes, int width, int height,
                                                       Set<Integer> lockedSlots, boolean transpose) {
        // In transposed mode, locked slots must also be transposed
        Set<Integer> effectiveLocked = transpose
                ? transposeLockedSlots(lockedSlots, width, height)
                : lockedSlots;

        int minRows = groupSizes.size();
        if (minRows == 0) return new ArrayList<>();

        // Try columnsCount from 1 to width, pick the one with the fewest brokenGroups.
        // When transpose=true, width/height are the transposed-space dimensions (already swapped).
        ColumnsCandidate best = null;
        for (int columnsCount = 1; columnsCount <= width; columnsCount++) {
            if (minRows > height * columnsCount) continue;

            int[] columnWidths = SortUtils.distribute(width, columnsCount);
            ColumnsCandidate cc = new ColumnsCandidate(groupSizes, columnWidths, height,
                    width, effectiveLocked);
            if (cc.succeeded) {
                if (cc.brokenGroups == 0) {
                    return transposeResult(cc.apply(), width, height, transpose);
                }
                if (best == null || cc.brokenGroups < best.brokenGroups) {
                    best = cc;
                }
            }
        }
        if (best != null) {
            return transposeResult(best.apply(), width, height, transpose);
        }
        // Fallback to DEFAULT using the original-space dimensions
        int origW = transpose ? height : width;
        int origH = transpose ? width : height;
        return calculateDefault(groupSizes, origW, origH, lockedSlots);
    }

    /**
     * Transpose result indices (COLUMN mode: from transposed space back to original space).
     */
    private static List<List<Integer>> transposeResult(List<List<Integer>> result,
                                                        int width, int height, boolean transpose) {
        if (!transpose) return result;
        List<List<Integer>> transposed = new ArrayList<>();
        for (List<Integer> slots : result) {
            List<Integer> tSlots = new ArrayList<>();
            for (int idx : slots) {
                tSlots.add(SortUtils.transposedIndex(width, height, idx));
            }
            transposed.add(tSlots);
        }
        return transposed;
    }

    /**
     * Get the list of non-locked slot indices (row-major order).
     */
    private static List<Integer> getAvailableSlots(int width, int height, Set<Integer> lockedSlots) {
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < width * height; i++) {
            if (!lockedSlots.contains(i)) {
                available.add(i);
            }
        }
        return available;
    }

    /**
     * Transpose locked-slot indices.
     * Index i in original space (w, h) → transposedIndex(w, h, i) in transposed space (h, w).
     */
    private static Set<Integer> transposeLockedSlots(Set<Integer> lockedSlots, int width, int height) {
        Set<Integer> result = new HashSet<>();
        for (int idx : lockedSlots) {
            result.add(SortUtils.transposedIndex(width, height, idx));
        }
        return result;
    }

    /**
     * Column-candidate scheme (IPN ColumnsCandidate port + obstacle awareness).
     */
    private static class ColumnsCandidate {
        final List<Integer> groupSizes;
        final int[] columnWidths;
        final int height;
        final int width;          // total width of the compute space
        final Set<Integer> lockedSlots;
        int brokenGroups = 0;
        final boolean succeeded;

        final List<Cell> cells;
        int cellIndex = 0;
        boolean allowBroken = false;
        final List<List<Cell>> eachCellsList = new ArrayList<>();

        ColumnsCandidate(List<Integer> groupSizes, int[] columnWidths, int height,
                         int width, Set<Integer> lockedSlots) {
            this.groupSizes = groupSizes;
            this.columnWidths = columnWidths;
            this.height = height;
            this.width = width;
            this.lockedSlots = lockedSlots;

            // Build cells: arranged column-major (col0row0, col0row1, ..., col1row0, ...)
            List<Cell> cellList = new ArrayList<>();
            // cells arranged row-major (correct for ROW mode; for COLUMN mode row-major in
            // transposed space = column-major in original space)
            for (int row = 0; row < height; row++) {
                int colOffset = 0;
                for (int col = 0; col < columnWidths.length; col++) {
                    int colWidth = columnWidths[col];
                    int slotIndex = row * width + colOffset;
                    // Obstacle awareness: collect non-locked slots within this range
                    List<Integer> slotIndices = new ArrayList<>();
                    for (int i = 0; i < colWidth; i++) {
                        int idx = slotIndex + i;
                        if (!lockedSlots.contains(idx)) {
                            slotIndices.add(idx);
                        }
                    }
                    cellList.add(new Cell(slotIndices, row, col));
                    colOffset += colWidth;
                }
            }
            this.cells = cellList;
            this.succeeded = initAll();
        }

        private boolean initAll() {
            for (int i = 0; i < groupSizes.size(); i++) {
                if (!addCellsForIndex(i)) return false;
            }
            return true;
        }

        private boolean addCellsForIndex(int index) {
            List<Cell> result = findCellsForRoom(groupSizes.get(index));
            if (result.isEmpty()) return false;
            result.forEach(c -> c.occupied = true);
            eachCellsList.add(result);
            if (!connected(result)) brokenGroups++;
            return true;
        }

        private List<Cell> findCellsForRoom(int slotCount) {
            if (!findEmptyCell()) return new ArrayList<>();
            if (!allowBroken) {
                // First pass: try not to split the group (row-major arrangement, check same row)
                int initRowIndex = cells.get(cellIndex).rowIndex;
                int columnsCount = columnWidths.length;
                int totalRoom = 0;
                int neededCells = 0;
                for (int i = cellIndex; i < cells.size(); i++) {
                    totalRoom += cells.get(i).room();
                    neededCells++;
                    if (totalRoom >= slotCount) {
                        // Doesn't fit in one row (neededCells > columnsCount) or still on the same row
                        if (neededCells > columnsCount || cells.get(i).rowIndex == initRowIndex) {
                            return new ArrayList<>(cells.subList(cellIndex, cellIndex + neededCells));
                        } else {
                            // Restart from the next row
                            cellIndex = (initRowIndex + 1) * columnsCount;
                        }
                        break;
                    }
                }
            }
            // Second pass: allow splitting
            List<Cell> result = new ArrayList<>();
            int remaining = slotCount;
            while (remaining > 0) {
                if (!findEmptyCell()) return new ArrayList<>();
                Cell cell = cells.get(cellIndex);
                remaining -= cell.room();
                result.add(cell);
                cell.occupied = true;
            }
            return result;
        }

        private boolean findEmptyCell() {
            while (cellIndex < cells.size()) {
                if (!cells.get(cellIndex).occupied) return true;
                cellIndex++;
            }
            if (allowBroken) return false;
            allowBroken = true;
            cellIndex = 0;
            return findEmptyCell();
        }

        /**
         * Apply the allocation: each type → list of target slot indices.
         */
        List<List<Integer>> apply() {
            List<List<Integer>> result = new ArrayList<>();
            for (int i = 0; i < groupSizes.size(); i++) {
                List<Integer> slots = new ArrayList<>();
                for (Cell cell : eachCellsList.get(i)) {
                    slots.addAll(cell.slotIndices);
                }
                // Take only the first groupSizes[i] slots
                int size = Math.min(slots.size(), groupSizes.get(i));
                result.add(new ArrayList<>(slots.subList(0, size)));
            }
            return result;
        }

        /**
         * Check whether the Cell list is connected (ported from IPN connected function).
         */
        private boolean connected(List<Cell> cellList) {
            if (cellList.isEmpty()) return true;
            Set<Long> active = new HashSet<>();
            for (Cell cell : cellList) {
                active.add((long) cell.rowIndex << 32 | (cell.columnIndex & 0xFFFFFFFFL));
            }
            Set<Long> visited = new HashSet<>();
            List<Long> queue = new ArrayList<>();
            // BFS
            long first = active.iterator().next();
            queue.add(first);
            while (!queue.isEmpty()) {
                long point = queue.removeFirst();
                if (active.contains(point) && !visited.contains(point)) {
                    visited.add(point);
                    int row = (int) (point >> 32);
                    int col = (int) point;
                    queue.add(((long) (row - 1) << 32) | (col & 0xFFFFFFFFL));
                    queue.add(((long) (row + 1) << 32) | (col & 0xFFFFFFFFL));
                    queue.add(((long) row << 32) | ((col - 1) & 0xFFFFFFFFL));
                    queue.add(((long) row << 32) | ((col + 1) & 0xFFFFFFFFL));
                }
            }
            return visited.size() == active.size();
        }
    }

    /**
     * Cell: a row×column block region.
     * Obstacle awareness: slotIndices contains only non-locked slots; room = slotIndices.size().
     */
    private static class Cell {
        final List<Integer> slotIndices;
        final int rowIndex;
        final int columnIndex;
        boolean occupied = false;

        Cell(List<Integer> slotIndices, int rowIndex, int columnIndex) {
            this.slotIndices = slotIndices;
            this.rowIndex = rowIndex;
            this.columnIndex = columnIndex;
        }

        int room() {
            return slotIndices.size();
        }
    }
}
