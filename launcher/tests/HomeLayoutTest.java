package com.gathin.tvhome;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Run with javac/java, no Android runtime or third-party test dependency. */
public final class HomeLayoutTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        List<String> pinned = Arrays.asList("missing", "c", "a", "c");
        List<String> ordered = HomeLayout.order(Arrays.asList("a", "b", "c", "d"), pinned,
                new HashSet<>(Arrays.asList("a")));
        check(ordered.equals(Arrays.asList("c", "b", "d")));
        check(HomeLayout.dockCount(ordered, pinned, 6) == 1);
        check(HomeLayout.dockCount(ordered, pinned, 0) == 0);
        check(HomeLayout.order(Arrays.asList("a", "b"), pinned,
                new HashSet<>(Arrays.asList("a", "b"))).isEmpty());
        check(HomeLayout.vertical(0, true, 0, 0, 4) == -1);
        check(HomeLayout.vertical(0, true, 10, 3, 4) == -1);
        check(HomeLayout.vertical(2, false, 10, 3, 4) == 5);
        check(HomeLayout.vertical(6, true, 10, 3, 4) == 2);
        check(HomeLayout.vertical(6, false, 10, 3, 4) == 9);
        check(HomeLayout.vertical(8, false, 10, 3, 4) == 8);
        check(HomeLayout.horizontal(3, true, 10, 3, 4) == 3);
        check(HomeLayout.horizontal(6, false, 10, 3, 4) == 6);
        check(HomeLayout.vertical(0, true, 3, 0, 4) == -1);
        check(HomeLayout.vertical(0, false, 3, 3, 4) == 0);
        // Exhaust every dock size / column count / partial last row. No key may escape the grid.
        for (int count = 1; count <= 40; count++) for (int dock = 0; dock <= Math.min(6, count); dock++)
            for (int columns = 3; columns <= 5; columns++) for (int pos = 0; pos < count; pos++) {
                int up = HomeLayout.vertical(pos, true, count, dock, columns);
                int down = HomeLayout.vertical(pos, false, count, dock, columns);
                int left = HomeLayout.horizontal(pos, true, count, dock, columns);
                int right = HomeLayout.horizontal(pos, false, count, dock, columns);
                check(up >= -1 && up < count && down >= 0 && down < count);
                check(left >= 0 && left <= pos && right >= pos && right < count);
            }
        System.out.println("HomeLayout tests passed (all dock, column and edge combinations)");
    }
}
