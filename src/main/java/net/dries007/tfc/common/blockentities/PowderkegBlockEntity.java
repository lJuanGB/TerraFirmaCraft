/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.blockentities;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.client.TFCSounds;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.devices.PowderkegBlock;
import net.dries007.tfc.common.blocks.devices.SealableDeviceBlock;
import net.dries007.tfc.common.capabilities.InventoryItemHandler;
import net.dries007.tfc.common.capabilities.PartialItemHandler;
import net.dries007.tfc.common.component.TFCComponents;
import net.dries007.tfc.common.component.item.ItemListComponent;
import net.dries007.tfc.common.container.PowderkegContainer;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.PowderKegExplosion;
import net.dries007.tfc.util.advancements.TFCAdvancements;

public class PowderkegBlockEntity extends TickableInventoryBlockEntity<PowderkegBlockEntity.PowderkegInventory>
{
    public static final int SLOTS = 12;
    public static final int MAX_STRENGTH = 64;

    public static void serverTick(Level level, BlockPos pos, BlockState state, PowderkegBlockEntity powderkeg)
    {
        if (powderkeg.isLit)
        {
            --powderkeg.fuse;

            if (powderkeg.fuse <= 0)
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
                explode(powderkeg);
            }
        }
    }

    public static int getStrength(PowderkegBlockEntity powderkeg)
    {
        return getStrength(powderkeg.inventory);
    }

    public static int getStrength(IItemHandler inventory)
    {
        int count = 0;
        for (int i = 0; i < inventory.getSlots(); i++)
        {
            count += inventory.getStackInSlot(i).getCount();
        }
        return Math.min(MAX_STRENGTH, Mth.floor(TFCConfig.SERVER.powderKegStrengthModifier.get() * count / SLOTS));
    }

    private static void explode(PowderkegBlockEntity powderkeg)
    {
        assert powderkeg.level != null;
        PowderKegExplosion explosion = new PowderKegExplosion(powderkeg.level, powderkeg.igniter, powderkeg.worldPosition.getX(), powderkeg.worldPosition.getY(), powderkeg.worldPosition.getZ(), getStrength(powderkeg));
        explosion.explode();
        explosion.finalizeExplosion(true);
    }

    private int fuse = -1;
    private boolean isLit = false;
    private @Nullable Entity igniter;

    public PowderkegBlockEntity(BlockPos pos, BlockState state)
    {
        super(TFCBlockEntities.POWDERKEG.get(), pos, state, PowderkegBlockEntity.PowderkegInventory::new);

        if (TFCConfig.SERVER.powderKegEnableAutomation.get())
        {
            sidedInventory.on(new PartialItemHandler(inventory).insertAll(), Direction.UP);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput components)
    {
        final List<ItemStack> content = components.getOrDefault(TFCComponents.CONTENTS, ItemListComponent.EMPTY).contents();
        Helpers.copyFrom(content, inventory);
        super.applyImplicitComponents(components);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder)
    {
        if (getBlockState().getValue(SealableDeviceBlock.SEALED))
        {
            builder.set(TFCComponents.CONTENTS, ItemListComponent.of(inventory));
        }
        super.collectImplicitComponents(builder);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
    {
        return PowderkegContainer.create(this, player.getInventory(), containerId);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack)
    {
        return Helpers.isItem(stack, TFCTags.Items.POWDER_KEG_FUEL);
    }

    public void onSeal()
    {
        markForSync();
    }

    public void onUnseal()
    {
        markForSync();
    }

    public int getFuse()
    {
        return fuse;
    }

    public boolean isLit()
    {
        return isLit;
    }

    public void setLit(boolean lit, @Nullable Entity igniter)
    {
        if (!TFCConfig.SERVER.powderKegEnabled.get())
        {
            if (igniter instanceof Player player)
                player.displayClientMessage(Component.translatable("tfc.tooltip.powderkeg.disabled"), true);
            return;
        }
        isLit = lit;
        assert level != null;
        level.setBlockAndUpdate(worldPosition, level.getBlockState(worldPosition).setValue(PowderkegBlock.LIT, lit));
        if (lit)
        {
            Helpers.playSound(level, worldPosition, SoundEvents.TNT_PRIMED);
            fuse = TFCConfig.SERVER.powderKegFuseTime.get();
            this.igniter = igniter;
            if (igniter instanceof ServerPlayer serverPlayer && getStrength(this) >= MAX_STRENGTH)
            {
                TFCAdvancements.FULL_POWDERKEG.trigger(serverPlayer);
            }
        }
        else
        {
            Helpers.playSound(level, worldPosition, TFCSounds.ITEM_COOL.get());
            fuse = -1;
        }
        markForSync();
    }

    public static class PowderkegInventory extends InventoryItemHandler
    {
        private final PowderkegBlockEntity powderkeg;
        private final InventoryItemHandler inventory;

        PowderkegInventory(InventoryBlockEntity<?> entity)
        {
            super(entity, SLOTS);
            powderkeg = (PowderkegBlockEntity) entity;
            inventory = new InventoryItemHandler(entity, SLOTS);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate)
        {
            return canModify() ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate)
        {
            return canModify() ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        private boolean canModify()
        {
            return !powderkeg.getBlockState().getValue(PowderkegBlock.SEALED);
        }
    }
}
