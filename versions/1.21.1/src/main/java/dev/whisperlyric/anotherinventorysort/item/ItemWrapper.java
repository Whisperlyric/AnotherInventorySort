package dev.whisperlyric.anotherinventorysort.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * ItemStack wrapper providing consistent comparison interface
 */
public class ItemWrapper {

    private final ItemStack stack;

    public ItemWrapper(ItemStack stack) {
        this.stack = stack;
    }

    public ItemStack getStack() {
        return stack;
    }

    public String getId() {
        if (stack.isEmpty()) return "minecraft:air";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public String getName() {
        return stack.getHoverName().getString();
    }

    public int getCount() {
        return stack.getCount();
    }

    public int getMaxCount() {
        return stack.getMaxStackSize();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public boolean isDamageable() {
        return stack.isDamageableItem();
    }

    public int getDamage() {
        return stack.getDamageValue();
    }

    public int getMaxDamage() {
        return stack.getMaxDamage();
    }

    public int getDurabilityPercentage() {
        if (!isDamageable()) return 100;
        int maxDamage = getMaxDamage();
        if (maxDamage <= 0) return 100;
        return Math.max(0, 100 - (getDamage() * 100 / maxDamage));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ItemWrapper other = (ItemWrapper) obj;
        return getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
}