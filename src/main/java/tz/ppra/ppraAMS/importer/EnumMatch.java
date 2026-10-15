package tz.ppra.ppraAMS.importer;

import java.util.Map;

public final class EnumMatch {
    private EnumMatch() {}

    private static final Map<String, String> SYN = Map.ofEntries(
            Map.entry("theft", "stol"), Map.entry("stolen", "stol"), Map.entry("missing", "stol"),
            Map.entry("free", "avail"), Map.entry("unassigned", "avail"), Map.entry("unallocated", "avail"),
            Map.entry("spare", "avail"), Map.entry("available", "avail"),
            Map.entry("allocated", "assign"), Map.entry("issued", "assign"), Map.entry("in use", "assign"),
            Map.entry("assigned", "assign"), Map.entry("brand new", "new"),
            Map.entry("broken", "damag"), Map.entry("faulty", "damag"), Map.entry("bad", "poor"));

    public static String norm(String s) {
        if (s == null) return "";
        String k = s.trim().toLowerCase();
        return SYN.getOrDefault(k, k).replaceAll("[^a-z]", "");
    }

    public static <E extends Enum<E>> E resolve(String input, E[] values) {
        String key = norm(input);
        if (key.isEmpty()) return null;
        for (E v : values) {
            String n = v.name().toLowerCase().replaceAll("[^a-z]", "");
            if (n.equals(key)) return v;
        }
        for (E v : values) {
            String n = v.name().toLowerCase().replaceAll("[^a-z]", "");
            if (n.contains(key) || key.contains(n)) return v;
        }
        return null;
    }
}
