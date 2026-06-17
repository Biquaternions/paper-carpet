package me.biquaternions.carpet.listener;

import me.biquaternions.carpet.helper.HopperCounter;
import me.biquaternions.carpet.utils.WoolTool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.bukkit.DyeColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class HopperListener implements Listener {

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Hopper pulling
        if (!(event.getDestination() instanceof CraftInventory inventory && inventory.getInventory() instanceof HopperBlockEntity hopper)) {
            return;
        }

        if (!(hopper.getLevel() instanceof ServerLevel level)) {
            return;
        }

        ItemStack item = event.getItem();
        if (this.handleHopperPull(item, hopper, level)) {
            item.setAmount(0);
        }
    }

    @EventHandler
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        // Hopper pulling
        if (!(event.getInventory() instanceof CraftInventory inventory && inventory.getInventory() instanceof HopperBlockEntity hopper)) {
            return;
        }

        if (!(hopper.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Item itemEntity = event.getItem();
        if (this.handleHopperPull(itemEntity.getItemStack(), hopper, level)) {
            itemEntity.remove();
        }
    }

    private boolean handleHopperPull(final ItemStack item, final HopperBlockEntity hopper, final ServerLevel level) {
        BlockPos position = hopper.getBlockPos();
        Direction direction = level.getBlockState(position).getValue(HopperBlock.FACING);
        Block block = CraftBlock.at(level, position.relative(direction));

        DyeColor color = WoolTool.getWoolColor(block);
        if (color == null) {
            return false;
        }

        World world = level.getWorld();
        HopperCounter counter = HopperCounter.getCounter(world, color);
        if (counter == null) {
            return false;
        }

        counter.add(world, item);
        return true;
    }

}
