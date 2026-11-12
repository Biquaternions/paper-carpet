package me.biquaternions.carpet.script.utils;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementations of the methods CraftBukkit adds to {@link Container}, for scarpet's virtual containers.
 */
public interface PaperContainer extends Container {

    @Override
    default int getMaxStackSize() {
        return Container.MAX_STACK;
    }

    @Override
    default List<ItemStack> getContents() {
        List<ItemStack> contents = new ArrayList<>(this.getContainerSize());
        for (int i = 0; i < this.getContainerSize(); i++) {
            contents.add(this.getItem(i));
        }
        return contents;
    }

    @Override
    default void onOpen(CraftHumanEntity player) {
    }

    @Override
    default void onClose(CraftHumanEntity player) {
    }

    @Override
    default List<HumanEntity> getViewers() {
        return List.of();
    }

    @Override
    default @Nullable InventoryHolder getOwner() {
        return null;
    }

    @Override
    default void setMaxStackSize(int size) {
    }

    @Override
    default @Nullable Location getLocation() {
        return null;
    }

}
