package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure layout rules shared by the adapter, D-pad navigation and host-side tests. */
final class HomeLayout {
    private HomeLayout() {}

    static List<String> order(List<String> installed, List<String> pinned, Set<String> hidden) {
        List<String> result = new ArrayList<>();
        Set<String> available = new HashSet<>(installed);
        for (String pkg : pinned) {
            if (available.contains(pkg) && !hidden.contains(pkg) && !result.contains(pkg)) result.add(pkg);
        }
        for (String pkg : installed) {
            if (!hidden.contains(pkg) && !result.contains(pkg)) result.add(pkg);
        }
        return result;
    }

    static int dockCount(List<String> ordered, List<String> pinned, int slots) {
        int count = 0;
        for (String pkg : ordered) {
            if (!pinned.contains(pkg) || count == slots) break;
            count++;
        }
        return count;
    }

    /** -1 means the top bar; edge keys stay on the same tile. */
    static int vertical(int pos, boolean up, int count, int dock, int columns) {
        if (count == 0) return -1;
        if (pos < dock) {
            if (up) return -1;
            return dock < count ? Math.min(dock + pos * columns / dock, count - 1) : pos;
        }
        int index = pos - dock;
        if (up) {
            if (index >= columns) return pos - columns;
            return dock == 0 ? -1 : Math.min(index * dock / columns, dock - 1);
        }
        int nextRow = dock + (index / columns + 1) * columns;
        return nextRow >= count ? pos : Math.min(pos + columns, count - 1);
    }

    static int horizontal(int pos, boolean left, int count, int dock, int columns) {
        int start = pos < dock ? 0 : dock + ((pos - dock) / columns) * columns;
        int end = Math.min(count, start + (pos < dock ? dock : columns)) - 1;
        return left ? Math.max(start, pos - 1) : Math.min(end, pos + 1);
    }
}
