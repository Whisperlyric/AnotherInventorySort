package dev.whisperlyric.anotherinventorysort.sort;

import dev.whisperlyric.anotherinventorysort.item.ItemWrapper;

/**
 * Default sort rule
 */
public enum DefaultSortRule {
    INSTANCE;

    public int compare(ItemWrapper a, ItemWrapper b) {
        if (a.isEmpty() && b.isEmpty()) return 0;
        if (a.isEmpty()) return 1;
        if (b.isEmpty()) return -1;

        int idCompare = a.getId().compareTo(b.getId());
        if (idCompare != 0) return idCompare;

        int nameCompare = a.getName().compareTo(b.getName());
        if (nameCompare != 0) return nameCompare;

        if (a.isDamageable() && b.isDamageable()) {
            int durabilityCompare = Integer.compare(b.getDurabilityPercentage(), a.getDurabilityPercentage());
            if (durabilityCompare != 0) return durabilityCompare;
        }

        return Integer.compare(b.getCount(), a.getCount());
    }
}