package dev.petrolal.dotfiles.overlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Arranges {@link AppEntry} nodes into a centered grid in world space.
 * No scrolling/pagination yet -- that's its own future concern, not part of
 * wiring the real data in (see docs/checklist.md) -- so this caps at
 * {@link #MAX_NODES}, sorted by name, to keep the first real render legible
 * instead of cramming in however many hundred `.desktop` files a host has.
 */
final class NodeLayout {

    static final int MAX_NODES = 24;
    static final float SPACING = 1.5f;
    static final float NODE_SIZE = 1f;

    record PositionedEntry(AppEntry entry, float x, float y) {}

    /** {@code columns} is needed by keyboard navigation (up/down moves by a full row). */
    record Grid(List<PositionedEntry> nodes, int columns) {}

    private NodeLayout() {
    }

    static Grid grid(List<AppEntry> entries) {
        List<AppEntry> sorted = entries.stream()
                .sorted(Comparator.comparing(AppEntry::name, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_NODES)
                .toList();

        int count = sorted.size();
        if (count == 0) {
            return new Grid(List.of(), 0);
        }

        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil(count / (double) columns);

        List<PositionedEntry> positioned = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int row = i / columns;
            int col = i % columns;
            float x = (col - (columns - 1) / 2f) * SPACING;
            float y = ((rows - 1) / 2f - row) * SPACING; // row 0 at the top
            positioned.add(new PositionedEntry(sorted.get(i), x, y));
        }
        return new Grid(positioned, columns);
    }
}
