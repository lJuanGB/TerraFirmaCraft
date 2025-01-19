/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.machinezoo.noexception.throwing.ThrowingRunnable;
import com.machinezoo.noexception.throwing.ThrowingSupplier;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import net.dries007.tfc.client.ClientHelpers;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.InventoryBlockEntity;
import net.dries007.tfc.common.blocks.ISlowEntities;
import net.dries007.tfc.common.component.food.FoodCapability;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.component.heat.IHeat;
import net.dries007.tfc.common.component.size.IItemSize;
import net.dries007.tfc.common.component.size.ItemSizeManager;
import net.dries007.tfc.common.component.size.Size;
import net.dries007.tfc.common.component.size.Weight;
import net.dries007.tfc.common.effect.TFCEffects;
import net.dries007.tfc.common.entities.ai.prey.PestAi;
import net.dries007.tfc.common.entities.prey.Pest;
import net.dries007.tfc.util.data.FluidHeat;
import net.dries007.tfc.util.tooltip.Tooltips;

import static net.dries007.tfc.TerraFirmaCraft.*;

public final class Helpers
{
    public static final Direction[] DIRECTIONS = Direction.values();
    public static final DyeColor[] DYE_COLORS = DyeColor.values();
    public static final DyeColor[] DYE_COLORS_NOT_WHITE = Arrays.stream(DYE_COLORS).filter(e -> e != DyeColor.WHITE).toArray(DyeColor[]::new);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PRIME_X = 501125321;
    private static final int PRIME_Y = 1136930381;

    @Nullable private static RecipeManager CACHED_RECIPE_MANAGER = null;

    /**
     * @return A {@link ResourceLocation} with the {@code tfc} namespace.
     */
    public static ResourceLocation identifier(String name)
    {
        return resourceLocation(MOD_ID, name);
    }

    /**
     * @return A {@link ResourceLocation} with the {@code minecraft} namespace.
     */
    public static ResourceLocation identifierMC(String name)
    {
        return resourceLocation("minecraft", name);
    }

    /**
     * @return A {@link ResourceLocation} with an inferred namespace. If present, the namespace will be used, otherwise
     * {@code minecraft} will be used.
     */
    public static ResourceLocation resourceLocation(String name)
    {
        return ResourceLocation.parse(name);
    }

    /**
     * @return A {@link ResourceLocation} with an explicit namespace and path.
     */
    public static ResourceLocation resourceLocation(String domain, String path)
    {
        return ResourceLocation.fromNamespaceAndPath(domain, path);
    }

    @Nullable
    public static <T, C> T getCapability(BlockCapability<T, @Nullable C> capability, Level level, BlockPos pos)
    {
        return level.getCapability(capability, pos, null);
    }

    @Nullable
    public static <T, C> T getCapability(BlockCapability<T, C> capability, BlockEntity entity)
    {
        return getCapability(capability, entity, null);
    }

    @Nullable
    @SuppressWarnings("DataFlowIssue") // BlockEntity.level is in practice never null, and the @Nullable C is not picked up correctly w.r.t getCapability()
    public static <T, C> T getCapability(BlockCapability<T, @Nullable C> capability, BlockEntity entity, @Nullable C context)
    {
        return entity.getLevel().getCapability(capability, entity.getBlockPos(), entity.getBlockState(), entity, context);
    }

    /**
     * Tests if a stack *might* have a capability, either by virtue of it already having said capability, <strong>or</strong> if a single
     * item spliced off of the stack would have that capability. This is necessary because there's a lot of places where we need to only accept
     * items with a certain capability, for instances, "all items that are heatable" are valid in most heating devices.
     * <p>
     * However, when we're in an inventory or container, there's a lot of code that is completely unaware of this restriction, for example {@link net.neoforged.neoforge.items.SlotItemHandler#getMaxStackSize(ItemStack)}.
     * This method will try and determine the stack size, by inserting a maximum size stack... which means i.e. if you try and insert a stack of 16 x empty molds, you will discover they don't, in fact, have a heat capability and as a result cannot be heated.
     * <p>
     * N.B. The requirement that item stack capabilities only return a capability with stack size == 1 is essential to prevent duplication glitches
     * or other inaccuracies in other, external code that isn't aware of the intricacies of how our capabilities work.
     */
    public static <T> boolean mightHaveCapability(ItemStack stack, ItemCapability<T, Void> capability)
    {
        return stack.copyWithCount(1).getCapability(capability) != null;
    }

    /**
     * Creates a map of each enum constant to the value as provided by the value mapper.
     * @return A {@code Map<E, V>}, with consistent iteration order.
     */
    public static <E extends Enum<E>, V> Map<E, V> mapOf(Class<E> enumClass, Function<E, V> valueMapper)
    {
        return mapOf(enumClass, key -> true, valueMapper);
    }

    /**
     * Creates a map of each enum constant to the value as provided by the value mapper, only using enum constants that match the provided predicate.
     * @return A {@code Map<E, V>}, with consistent iteration order.
     */
    public static <E extends Enum<E>, V> Map<E, V> mapOf(Class<E> enumClass, Predicate<E> keyPredicate, Function<E, V> valueMapper)
    {
        return Arrays.stream(enumClass.getEnumConstants())
            .filter(keyPredicate)
            .collect(Collectors.toMap(Function.identity(), valueMapper, (v, v2) -> Helpers.throwAsUnchecked(new AssertionError("Merging elements not allowed!")), () -> new EnumMap<>(enumClass)));
    }

    /**
     * Given a map of {@code K -> V1}, applies the mapping function {@code func: V1 -> V2} to each value, and returns a new, immutable map
     * consisting of {@code K -> V2}
     */
    public static <K, V1, V2> Map<K, V2> mapValue(Map<K, V1> map, Function<V1, V2> func)
    {
        final ImmutableMap.Builder<K, V2> builder = ImmutableMap.builderWithExpectedSize(map.size());
        for (Map.Entry<K, V1> entry : map.entrySet())
            builder.put(entry.getKey(), func.apply(entry.getValue()));
        return builder.build();
    }

    public static <K, V> V getRandomValue(Map<K, V> map, RandomSource random)
    {
        return Iterators.get(map.values().iterator(), random.nextInt(map.size()));
    }

    public static MutableComponent translateEnum(Enum<?> anEnum)
    {
        return Component.translatable(getEnumTranslationKey(anEnum));
    }

    public static MutableComponent translateEnum(Enum<?> anEnum, String enumName)
    {
        return Component.translatable(getEnumTranslationKey(anEnum, enumName));
    }

    /**
     * @return the translation key name for an enum. For instance, {@code Metal.UNKNOWN} would map to {@code "tfc.enum.metal.unknown"}.
     */
    public static String getEnumTranslationKey(Enum<?> anEnum)
    {
        return getEnumTranslationKey(anEnum, anEnum.getDeclaringClass().getSimpleName());
    }

    /**
     * Gets the translation key name for an enum, using a custom name instead of the enum class name
     */
    public static String getEnumTranslationKey(Enum<?> anEnum, String enumName)
    {
        return String.join(".", MOD_ID, "enum", enumName, anEnum.name()).toLowerCase(Locale.ROOT);
    }

    @Nullable
    @SuppressWarnings("deprecation")
    public static Level getUnsafeLevel(Object maybeLevel)
    {
        if (maybeLevel instanceof Level level)
        {
            return level; // Most obvious case, if we can directly cast up to level.
        }
        if (maybeLevel instanceof WorldGenRegion level)
        {
            return level.getLevel(); // Special case for world gen, when we can access the level unsafely
        }
        return null; // A modder has done a strange ass thing
    }

    /**
     * Reimplementation of {@link Entity#checkInsideBlocks()} which applies custom movement slowing affects. This is for two reasons:
     * <ul>
     *     <li>The existing movement slow affects via block do not work, as they affect vertical movement (jumping, falling) in ways we dislike.</li>
     *     <li>Applying these effects within {@link Block#entityInside(BlockState, Level, BlockPos, Entity)} applies them multiplicatively, for each block intersecting, which is undesirable.</li>
     * </ul>
     * This looks for slowing effects defined by the {@link ISlowEntities}
     */
    @SuppressWarnings("deprecation")
    public static void slowEntityInsideBlocks(Entity entity)
    {
        final Level level = entity.level();
        final AABB box = entity.getBoundingBox();
        final BlockPos minPos = BlockPos.containing(box.minX + 1.0E-7D, box.minY + 1.0E-7D, box.minZ + 1.0E-7D);
        final BlockPos maxPos = BlockPos.containing(box.maxX - 1.0E-7D, box.maxY - 1.0E-7D, box.maxZ - 1.0E-7D);

        float factor = ISlowEntities.NO_SLOW;

        if (level.hasChunksAt(minPos, maxPos))
        {
            final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = minPos.getX(); x <= maxPos.getX(); ++x)
            {
                for (int y = minPos.getY(); y <= maxPos.getY(); ++y)
                {
                    for (int z = minPos.getZ(); z <= maxPos.getZ(); ++z)
                    {
                        cursor.set(x, y, z);

                        final BlockState state = level.getBlockState(cursor);

                        if (state.getBlock() instanceof ISlowEntities slow)
                        {
                            factor = Math.min(factor, slow.slowEntityFactor(state));
                        }
                    }
                }
            }
        }

        // Only apply the effect based on the worst slow factor
        if (factor < ISlowEntities.NO_SLOW)
        {
            slowEntityInBlock(entity, factor);
        }
    }

    private static void slowEntityInBlock(Entity entity, float factor)
    {
        final float fallDamageReduction = 5;
        final Vec3 motion = entity.getDeltaMovement();

        // Affect falling very slightly, and don't affect jumping
        entity.setDeltaMovement(motion.multiply(factor, motion.y < 0 ? 1 - 0.2f * (1 - factor) : 1, factor));
        if (entity.fallDistance > fallDamageReduction)
        {
            entity.causeFallDamage(entity.fallDistance - fallDamageReduction, 1.0f, entity.damageSources().fall());
        }
        entity.fallDistance = 0;
    }

    /**
     * This is the check in {@linkplain net.minecraft.world.level.block.PowderSnowBlock#entityInside(BlockState, Level, BlockPos, Entity)}
     */
    public static boolean hasMoved(Entity entity)
    {
        return entity.xOld != entity.getX() && entity.zOld != entity.getZ();
    }

    public static void rotateEntity(Level level, Entity entity, Vec3 origin, float speed)
    {
        if (!entity.onGround() || entity.getDeltaMovement().y > 0 || speed == 0f)
        {
            return;
        }
        final float rot = (entity.getYHeadRot() + speed) % 360f;
        entity.setYRot(rot);
        if (level.isClientSide && entity instanceof Player)
        {
            final Vec3 offset = entity.position().subtract(origin).normalize();
            final Vec3 movement = new Vec3(-offset.z, 0, offset.x).scale(speed / 48f);
            entity.setDeltaMovement(entity.getDeltaMovement().add(movement));
            entity.hurtMarked = true; // resync movement
            return;
        }

        if (entity instanceof LivingEntity living)
        {
            entity.setYHeadRot(rot);
            entity.setYBodyRot(rot);
            entity.setOnGround(false);
            living.setNoActionTime(20);
            living.hurtMarked = true;
        }
    }

    public static BlockState copyProperties(BlockState copyTo, BlockState copyFrom)
    {
        for (Property<?> property : copyFrom.getProperties())
        {
            copyTo = copyProperty(copyTo, copyFrom, property);
        }
        return copyTo;
    }

    public static <T extends Comparable<T>> BlockState copyProperty(BlockState copyTo, BlockState copyFrom, Property<T> property)
    {
        return copyTo.hasProperty(property) ? copyTo.setValue(property, copyFrom.getValue(property)) : copyTo;
    }

    public static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, T value)
    {
        return state.hasProperty(property) ? state.setValue(property, value) : state;
    }

    public static RecipeManager getUnsafeRecipeManager()
    {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null)
        {
            return server.getRecipeManager();
        }

        try
        {
            final RecipeManager client = ClientHelpers.tryGetSafeRecipeManager();
            if (client != null)
            {
                return client;
            }
        }
        catch (Throwable t)
        {
            LOGGER.info("^ This is fine - No client or server recipe manager present upon initial resource reload on physical server");
        }

        if (CACHED_RECIPE_MANAGER != null)
        {
            return CACHED_RECIPE_MANAGER;
        }

        throw new IllegalStateException("No recipe manager was present - tried server, client, and captured value. This will cause problems!");
    }

    public static void setCachedRecipeManager(RecipeManager manager)
    {
        CACHED_RECIPE_MANAGER = manager;
    }

    /**
     * Damages {@code stack} by one point, when held by {@code entity} in {@code slot}
     */
    public static void damageItem(ItemStack stack, LivingEntity entity, EquipmentSlot slot)
    {
        stack.hurtAndBreak(1, entity, slot);
    }

    /**
     * Damages {@code stack} by {@code amount}, when held by {@code entity} in {@code hand}
     */
    public static void damageItem(ItemStack stack, int amount, LivingEntity entity, InteractionHand hand)
    {
        stack.hurtAndBreak(amount, entity, LivingEntity.getSlotForHand(hand));
    }

    /**
     * Damages {@code stack} by one point, when held by {@code entity} in {@code hand}
     */
    public static void damageItem(ItemStack stack, LivingEntity entity, InteractionHand hand)
    {
        stack.hurtAndBreak(1, entity, LivingEntity.getSlotForHand(hand));
    }

    /**
     * Damages {@code stack} without an entity present.
     */
    public static void damageItem(ItemStack stack, Level level)
    {
        if (level instanceof ServerLevel serverLevel)
        {
            stack.hurtAndBreak(1, serverLevel, null, item -> {});
        }
    }

    /**
     * Damages {@code stack} without a level present. Note that this <strong>is not correct!</strong> as it doesn't account for enchantments,
     * but in this case it is the closest approximation we can do.
     * @deprecated Prefer using any other overload than this
     */
    @Deprecated
    public static ItemStack damageItem(ItemStack stack)
    {
        if (stack.isDamageableItem())
        {
            final int amount = stack.getItem().damageItem(stack, 1, null, item -> {});
            final int damage = stack.getDamageValue() + amount;

            stack.setDamageValue(damage);
            if (damage >= stack.getMaxDamage())
            {
                stack.shrink(1);
            }
        }
        return stack;
    }

    /**
     * {@link Level#removeBlock(BlockPos, boolean)} but with all flags available.
     */
    public static void removeBlock(LevelAccessor level, BlockPos pos, int flags)
    {
        level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), flags);
    }

    public static void tickInfestation(Level level, BlockPos pos, int infestation, @Nullable Player player)
    {
        infestation = Mth.clamp(infestation, 0, 5);
        if (infestation == 0)
        {
            return;
        }
        if (level.random.nextInt(120 - (20 * infestation)) == 0)
        {
            final float chanceBasedOnCurrentPests = 1f - Mth.clampedMap(level.getEntitiesOfClass(Pest.class, new AABB(pos).inflate(40d)).size(), 0, 8, 0f, 1f);
            if (level.random.nextFloat() > chanceBasedOnCurrentPests)
            {
                return;
            }
            Helpers.randomEntity(TFCTags.Entities.PESTS, level.random).ifPresent(type -> {
                final Entity entity = type.create(level);
                if (entity instanceof PathfinderMob mob && level instanceof ServerLevel serverLevel)
                {
                    mob.moveTo(new Vec3(pos.getX(), pos.getY(), pos.getZ()));
                    final Vec3 checkPos = LandRandomPos.getPos(mob, 15, 5);
                    if (checkPos != null)
                    {
                        mob.moveTo(checkPos);
                        EventHooks.finalizeMobSpawn(mob, serverLevel, serverLevel.getCurrentDifficultyAt(BlockPos.containing(checkPos)), MobSpawnType.EVENT, null);
                        serverLevel.addFreshEntity(mob);
                        if (mob instanceof Pest pest)
                        {
                            PestAi.setSmelledPos(pest, pos);
                        }
                        if (player != null)
                        {
                            player.displayClientMessage(Component.translatable("tfc.tooltip.infestation"), true);
                        }
                    }
                }
            });
        }
    }

    /**
     * The number of huge/heavy items a subject is carrying. One = exhausted, More than one = overburdened.
     */
    public enum CarryCount
    {
        NONE, ONE, MORE_THAN_ONE;

        public boolean isNonZero()
        {
            return this != NONE;
        }
    }

    /**
     * @return 0 (well-burdened), 1 (exhausted), 2 (overburdened, add potion effect)
     */
    public static CarryCount getCarryCount(Container container)
    {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++)
        {
            final ItemStack stack = container.getItem(i);
            if (!stack.isEmpty())
            {
                final IItemSize size = ItemSizeManager.get(stack);
                if (size.getWeight(stack) == Weight.VERY_HEAVY && size.getSize(stack) == Size.HUGE)
                {
                    count++;
                    if (count == 2)
                    {
                        break;
                    }
                }
            }
        }
        return switch (count)
        {
            case 0 -> CarryCount.NONE;
            case 1 -> CarryCount.ONE;
            default -> CarryCount.MORE_THAN_ONE;
        };
    }

    public static MobEffectInstance getOverburdened(boolean visible)
    {
        return new MobEffectInstance(TFCEffects.OVERBURDENED.holder(), 25, 0, false, visible);
    }

    public static MobEffectInstance getExhausted(boolean visible)
    {
        return new MobEffectInstance(TFCEffects.EXHAUSTED.holder(), 25, 0, false, visible);
    }

    /**
     * Iterate through all slots in an {@code inventory}.
     */
    public static Iterable<ItemStack> iterate(IItemHandler inventory)
    {
        return iterate(inventory, 0, inventory.getSlots());
    }

    /**
     * Iterate through all the slots in a {@code inventory}.
     */
    public static Iterable<ItemStack> iterate(RecipeInput inventory)
    {
        return () -> new Iterator<>()
        {
            private int slot = 0;

            @Override
            public boolean hasNext()
            {
                return slot < inventory.size();
            }

            @Override
            public ItemStack next()
            {
                return inventory.getItem(slot++);
            }
        };
    }

    /**
     * Iterate through all slots in an {@code inventory}.
     */
    public static Iterable<ItemStack> iterate(IItemHandler inventory, int startSlotInclusive, int endSlotExclusive)
    {
        return () -> new Iterator<>()
        {
            private int slot = startSlotInclusive;

            @Override
            public boolean hasNext()
            {
                return slot < endSlotExclusive;
            }

            @Override
            public ItemStack next()
            {
                return inventory.getStackInSlot(slot++);
            }

            @Override
            public void remove()
            {
                Helpers.removeStack(inventory, slot - 1); // Remove the previous slot = previous call to next()
            }
        };
    }

    public static ListTag writeItemStacksToNbt(HolderLookup.Provider provider, List<ItemStack> stacks)
    {
        final ListTag list = new ListTag();
        for (final ItemStack stack : stacks)
        {
            list.add(stack.saveOptional(provider));
        }
        return list;
    }

    public static void readItemStacksFromNbt(HolderLookup.Provider provider, List<ItemStack> stacks, ListTag list)
    {
        stacks.clear();
        for (int i = 0; i < list.size(); i++)
        {
            stacks.add(ItemStack.parseOptional(provider, list.getCompound(i)));
        }
    }

    public static void readFixedSizeItemStacksFromNbt(HolderLookup.Provider provider, List<ItemStack> stacks, ListTag list)
    {
        for (int i = 0; i < list.size(); i++)
        {
            stacks.set(i, ItemStack.parseOptional(provider, list.getCompound(i)));
        }
    }

    /**
     * Given a theoretical item stack, of count {@code totalCount}, splits it into optimally sized stacks, up to the stack size limit and feeds these new stacks to {@code consumer}
     */
    public static void consumeInStackSizeIncrements(ItemStack stack, int totalCount, Consumer<ItemStack> consumer)
    {
        while (totalCount > 0)
        {
            final ItemStack splitStack = stack.copy();
            final int splitCount = Math.min(splitStack.getMaxStackSize(), totalCount);
            splitStack.setCount(splitCount);
            totalCount -= splitCount;
            consumer.accept(splitStack);
        }
    }

    public static void gatherAndConsumeItems(Level level, AABB bounds, IItemHandler inventory, int minSlotInclusive, int maxSlotInclusive)
    {
        gatherAndConsumeItems(level.getEntitiesOfClass(ItemEntity.class, bounds, EntitySelector.ENTITY_STILL_ALIVE), inventory, minSlotInclusive, maxSlotInclusive, Integer.MAX_VALUE);
    }

    public static void gatherAndConsumeItems(Level level, AABB bounds, IItemHandler inventory, int minSlotInclusive, int maxSlotInclusive, int maxItemsOverride)
    {
        gatherAndConsumeItems(level.getEntitiesOfClass(ItemEntity.class, bounds, EntitySelector.ENTITY_STILL_ALIVE), inventory, minSlotInclusive, maxSlotInclusive, maxItemsOverride);
    }

    public static void gatherAndConsumeItems(Collection<ItemEntity> items, IItemHandler inventory, int minSlotInclusive, int maxSlotInclusive, int maxItemsOverride)
    {
        final List<ItemEntity> availableItemEntities = new ArrayList<>();
        int availableItems = 0;
        for (ItemEntity entity : items)
        {
            if (inventory.isItemValid(maxSlotInclusive, entity.getItem()))
            {
                availableItems += entity.getItem().getCount();
                availableItemEntities.add(entity);
            }
        }
        if (availableItems > maxItemsOverride)
        {
            availableItems = maxItemsOverride;
        }
        Helpers.safelyConsumeItemsFromEntitiesIndividually(availableItemEntities, availableItems, item -> Helpers.insertSlots(inventory, item, minSlotInclusive, 1 + maxSlotInclusive).isEmpty());
    }

    /**
     * Removes / Consumes item entities from a list up to a maximum number of items (taking into account the count of each item)
     * Passes each item stack, with stack size = 1, to the provided consumer
     * This method expects the consumption to always succeed (such as when simply adding the items to a list)
     */
    public static void consumeItemsFromEntitiesIndividually(Collection<ItemEntity> entities, int maximum, Consumer<ItemStack> consumer)
    {
        int consumed = 0;
        for (ItemEntity entity : entities)
        {
            final ItemStack stack = entity.getItem();
            while (consumed < maximum && !stack.isEmpty())
            {
                consumer.accept(stack.split(1));
                consumed++;
                if (stack.isEmpty())
                {
                    entity.discard();
                }
            }
        }
    }

    /**
     * Removes / Consumes item entities from a list up to a maximum number of items (taking into account the count of each item)
     * Passes each item stack, with stack size = 1, to the provided consumer
     *
     * @param consumer consumes each stack. Returns {@code true} if the stack was consumed, and {@code false} if it failed, in which case we stop trying.
     */
    public static void safelyConsumeItemsFromEntitiesIndividually(Collection<ItemEntity> entities, int maximum, Predicate<ItemStack> consumer)
    {
        int consumed = 0;
        for (ItemEntity entity : entities)
        {
            final ItemStack stack = entity.getItem();
            while (consumed < maximum && !stack.isEmpty())
            {
                final ItemStack offer = stack.copyWithCount(1);
                if (!consumer.test(offer))
                {
                    return;
                }
                consumed++;
                stack.shrink(1);
                if (stack.isEmpty())
                {
                    entity.discard();
                }
            }
        }
    }

    /**
     * Remove and return a stack in {@code slot}, replacing it with empty.
     */
    public static ItemStack removeStack(IItemHandler inventory, int slot)
    {
        return inventory.extractItem(slot, Integer.MAX_VALUE, false);
    }

    /**
     * Inserts {@code stack} into the inventory ignoring any difference in creation date.
     *
     * @param stack The stack to insert. Will be modified (and returned).
     * @return The remainder of {@code stack} after inserting.
     */
    public static ItemStack mergeInsertStack(IItemHandler inventory, int slot, ItemStack stack)
    {
        final ItemStack existing = removeStack(inventory, slot);
        final ItemStack remainder = stack.copy();
        final ItemStack merged = FoodCapability.mergeItemStacks(existing, remainder); // stack is now remainder
        inventory.insertItem(slot, merged, false); // Should be no remainder because we removed it all to start with
        return remainder;
    }

    /**
     * Attempts to insert a stack across all slots of an item handler
     *
     * @param stack The stack to be inserted
     * @return The remainder after the stack is inserted, if any
     */
    public static ItemStack insertAllSlots(IItemHandler inventory, ItemStack stack)
    {
        return insertSlots(inventory, stack, 0, inventory.getSlots());
    }

    public static ItemStack insertSlots(IItemHandler inventory, ItemStack stack, int slotStartInclusive, int slotEndExclusive)
    {
        for (int slot = slotStartInclusive; slot < slotEndExclusive; slot++)
        {
            stack = inventory.insertItem(slot, stack, false);
            if (stack.isEmpty())
            {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    public static boolean insertOne(Level level, BlockPos pos, Supplier<? extends BlockEntityType<? extends InventoryBlockEntity<?>>> type, ItemStack stack)
    {
        return level.getBlockEntity(pos, type.get()).map(entity -> insertOne(entity, stack)).orElse(false);
    }

    /**
     * Inserts one item of the provided {@code stack} to the inventory of the block entity {@code entity}. Note that this method
     * will not modify the input stack or consume another item!
     * @return {@code true} if the insertion was successful
     */
    public static boolean insertOne(InventoryBlockEntity<?> entity, ItemStack stack)
    {
        return insertAllSlots(entity.getInventory(), stack.copyWithCount(1)).isEmpty();
    }

    /**
     * @return {@code true} if every slot in the provided inventory is empty.
     */
    public static boolean isEmpty(Iterable<ItemStack> inventory)
    {
        for (ItemStack stack : inventory)
            if (!stack.isEmpty())
                return false;
        return true;
    }

    /**
     * Attempts to spread fire, in a half dome of max {@code radius}. Larger radii check more blocks.
     */
    public static void fireSpreaderTick(ServerLevel level, BlockPos pos, RandomSource random, int radius)
    {
        if (level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK))
        {
            for (int i = 0; i < radius; i++)
            {
                pos = pos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
                if (level.getRandom().nextFloat() < 0.25f)
                    pos = pos.above();
                final BlockState state = level.getBlockState(pos);
                if (!state.isAir())
                {
                    return;
                }
                if (hasFlammableNeighbours(level, pos))
                {
                    level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
                    return;
                }
            }
        }
    }

    private static boolean hasFlammableNeighbours(LevelReader level, BlockPos pos)
    {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (Direction direction : Helpers.DIRECTIONS)
        {
            mutable.setWithOffset(pos, direction);
            if (level.getBlockState(mutable).isFlammable(level, mutable, direction.getOpposite()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Copied from {@link Level#destroyBlock(BlockPos, boolean, Entity, int)}
     * Allows the loot context to be modified
     */
    public static void destroyBlockAndDropBlocksManually(ServerLevel level, BlockPos pos, Consumer<LootParams.Builder> builder)
    {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir())
        {
            FluidState fluidstate = level.getFluidState(pos);
            if (!(state.getBlock() instanceof BaseFireBlock))
            {
                level.levelEvent(2001, pos, Block.getId(state));
            }
            dropWithContext(level, state, pos, builder, true);
            level.setBlock(pos, fluidstate.createLegacyBlock(), 3, 512);
        }
    }

    public static void dropWithContext(ServerLevel level, BlockState state, BlockPos pos, Consumer<LootParams.Builder> consumer, boolean randomized)
    {
        BlockEntity tileEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

        // Copied from Block.getDrops()
        LootParams.Builder params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, tileEntity);
        consumer.accept(params);

        state.getDrops(params).forEach(stackToSpawn -> {
            if (randomized)
            {
                Block.popResource(level, pos, stackToSpawn);
            }
            else
            {
                spawnDropsAtExactCenter(level, pos, stackToSpawn);
            }
        });
        state.spawnAfterBreak(level, pos, ItemStack.EMPTY, false);
    }

    /**
     * {@link Block#popResource(Level, BlockPos, ItemStack)} but without randomness as to the velocity and position.
     */
    public static void spawnDropsAtExactCenter(Level level, BlockPos pos, ItemStack stack)
    {
        if (!level.isClientSide && !stack.isEmpty() && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) && !level.restoringBlockSnapshots)
        {
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack, 0D, 0D, 0D);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
    }

    /**
     * Plays the standard sound that is used when a block of a given state is placed.
     * @param state The state corresponding to the block or sound type that was placed.
     */
    public static void playPlaceSound(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state)
    {
        playPlaceSound(player, level, pos, state.getSoundType(level, pos, player));
    }

    /**
     * Plays the standard sound that is used when a block of a given sound type is placed.
     * @param player The player which is ignored on server, but plays for on client. This should either be invoked on server with {@code null}, or
     *               invoked on both sides with the same {@code player}.
     * @implNote The exact volume and pitch are copied from the sound in {@link BlockItem#place}.
     */
    public static void playPlaceSound(@Nullable Player player, LevelAccessor level, BlockPos pos, SoundType sound)
    {
        level.playSound(player, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }

    public static void playSound(Level level, BlockPos pos, SoundEvent sound)
    {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F + level.getRandom().nextFloat(), level.getRandom().nextFloat() + 0.7F + 0.3F);
    }

    public static boolean spawnItem(Level level, Vec3 pos, ItemStack stack)
    {
        return level.addFreshEntity(new ItemEntity(level, pos.x(), pos.y(), pos.z(), stack));
    }

    public static boolean spawnItem(Level level, BlockPos pos, ItemStack stack, double yOffset)
    {
        return level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + yOffset, pos.getZ() + 0.5D, stack));
    }

    public static boolean spawnItem(Level level, BlockPos pos, ItemStack stack)
    {
        return spawnItem(level, pos, stack, 0.5D);
    }

    public static FluidStack mergeOutputFluidIntoSlot(IItemHandlerModifiable inventory, FluidStack fluidStack, float temperature, int slot)
    {
        if (!fluidStack.isEmpty())
        {
            final ItemStack mergeStack = inventory.getStackInSlot(slot);
            final @Nullable IFluidHandlerItem fluidHandler = mergeStack.getCapability(Capabilities.FluidHandler.ITEM);
            if (fluidHandler != null)
            {
                final int filled = fluidHandler.fill(fluidStack, IFluidHandler.FluidAction.EXECUTE);
                if (filled > 0)
                {
                    final @Nullable IHeat mergeHeat = HeatCapability.get(mergeStack);
                    if (mergeHeat != null)
                    {
                        final FluidHeat heat = FluidHeat.getOrUnknown(fluidStack);
                        final float heatCapacity = heat.heatCapacity(filled);

                        mergeHeat.addTemperatureFromSourceWithHeatCapacity(temperature, heatCapacity);
                    }
                }
                final FluidStack remainder = fluidStack.copy();
                remainder.shrink(filled);
                return remainder;
            }
            return fluidStack;
        }
        return FluidStack.EMPTY;
    }

    /**
     * @see net.minecraft.core.QuartPos#toBlock(int)
     */
    public static BlockPos quartToBlock(int x, int y, int z)
    {
        return new BlockPos(x << 2, y << 2, z << 2);
    }

    /**
     * Rotates a VoxelShape 90 degrees. Assumes that the input facing is NORTH.
     */
    public static VoxelShape rotateShape(Direction direction, double x1, double y1, double z1, double x2, double y2, double z2)
    {
        return switch (direction)
        {
            case NORTH -> Block.box(x1, y1, z1, x2, y2, z2);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> throw new IllegalArgumentException("Not horizontal!");
        };
    }

    /**
     * Follows indexes for Direction#get2DDataValue()
     */
    public static VoxelShape[] computeHorizontalShapes(Function<Direction, VoxelShape> shapeGetter)
    {
        return new VoxelShape[] {shapeGetter.apply(Direction.SOUTH), shapeGetter.apply(Direction.WEST), shapeGetter.apply(Direction.NORTH), shapeGetter.apply(Direction.EAST)};
    }

    /**
     * Select N unique elements from a list, without having to shuffle the whole list.
     * This involves moving the selected elements to the end of the list. Note: this method will mutate the passed in list!
     * From <a href="https://stackoverflow.com/questions/4702036/take-n-random-elements-from-a-liste">Stack Overflow</a>
     */
    public static <T> List<T> uniqueRandomSample(List<T> list, int n, RandomSource r)
    {
        final int length = list.size();
        if (length < n)
        {
            throw new IllegalArgumentException("Cannot select n=" + n + " from a list of size = " + length);
        }
        for (int i = length - 1; i >= length - n; --i)
        {
            Collections.swap(list, i, r.nextInt(i + 1));
        }
        return list.subList(length - n, length);
    }

    /**
     * Given a list containing {@code [a0, ... aN]} and an element {@code aN+1}, returns a new, immutable list containing {@code [a0, ... aN, aN+1]},
     * in the most efficient manner that we can manage (a single data copy). This takes advantage that {@link ImmutableList}, along with its
     * builder, will not create copies if the builder is sized perfectly.
     * @return A new list containing the same elements plus the element to be appended.
     */
    public static <T> List<T> immutableAdd(List<T> list, T element)
    {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).addAll(list).add(element).build();
    }

    public static <T> List<T> immutableAddAll(List<T> list, List<T> others)
    {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + others.size())
            .addAll(list)
            .addAll(others)
            .build();
    }

    /**
     * Given a list containing {@code [a0, ... aN]} and an element {@code ai}, returns a new, immutable list containing {@code [a0, ... ai-1
     * , ai+1, ... aN]} in the most efficient manner (a single data copy).
     * @return A new list containing one fewer element than the original list
     * @throws IndexOutOfBoundsException if
     */
    public static <T> List<T> immutableRemove(List<T> list, T element)
    {
        final ImmutableList.Builder<T> builder = ImmutableList.builderWithExpectedSize(list.size() - 1);
        for (final T t : list)
            if (t != element)
                builder.add(t);
        return builder.build();
    }

    /**
     * Given a list containing {@code [a0, ... ai, ... aN}, an element {@code bi}, and an index {@code i}, returns a new, immutable list
     * containing {@code [a0, ... bi, ... aN]} in the most efficient manner (a single data copy). The new list will contain the same
     * references as the original list - they are assumed to be immutable!
     */
    public static <T> List<T> immutableSwap(List<T> list, T element, int index)
    {
        final ImmutableList.Builder<T> builder = ImmutableList.builderWithExpectedSize(list.size());
        for (int i = 0; i < list.size(); i++)
            builder.add(i == index ? element : list.get(i));
        return builder.build();
    }

    /**
     * Creates a new immutable list containing {@code n} new, separate instances of {@code T} produced by the given {@code factory}. This is unlike
     * {@link Collections#nCopies(int, Object)} in that it produces separate instance, and consumes memory proportional to O(n). However, in
     * the event the underlying elements are interior mutable, this creates a safe to modify list.
     * @see Collections#nCopies(int, Object)
     */
    public static <T> List<T> immutableCopies(int n, Supplier<T> factory)
    {
        final ImmutableList.Builder<T> builder = ImmutableList.builderWithExpectedSize(n);
        for (int i = 0; i < n; i++)
            builder.add(factory.get());
        return builder.build();
    }

    /**
     * Copies the contents of the inventory {@code inventory} into the builder.
     */
    public static void copyTo(ImmutableList.Builder<ItemStack> builder, IItemHandler inventory)
    {
        copyTo(builder, iterate(inventory));
    }

    /**
     * Copies the contents of the inventory {@code inventory} into the builder.
     */
    public static void copyTo(ImmutableList.Builder<ItemStack> builder, Iterable<ItemStack> stacks)
    {
        for (ItemStack stack : stacks)
            builder.add(stack.copy());
    }

    /**
     * Copies the contents of the inventory {@code inventory} into a list, clears the inventory, and returns the list.
     * @see #copyTo
     */
    public static List<ItemStack> copyToAndClear(IItemHandlerModifiable inventory)
    {
        final ImmutableList.Builder<ItemStack> builder = ImmutableList.builder();
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            builder.add(inventory.getStackInSlot(slot).copy());
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        return builder.build();
    }

    /**
     * Copies the contents of the contents list {@code list} into the inventory {@code inventory}. This will copy the minimum of
     * the slot count of the inventory, and the content of the list. If the list is empty, nothing will be copied.
     */
    public static void copyFrom(List<ItemStack> list, IItemHandlerModifiable inventory)
    {
        for (int i = 0; i < Math.min(list.size(), inventory.getSlots()); i++)
            inventory.setStackInSlot(i, list.get(i).copy());
    }

    /**
     * For when you want to ignore every possible safety measure in front of you
     */
    @SuppressWarnings("unchecked")
    public static <T> T uncheck(ThrowingSupplier<?> action)
    {
        try
        {
            return (T) action.get();
        }
        catch (Throwable e)
        {
            return throwAsUnchecked(e);
        }
    }

    public static void uncheck(ThrowingRunnable action)
    {
        try
        {
            action.run();
        }
        catch (Throwable e)
        {
            throwAsUnchecked(e);
        }
    }

    // Math Functions
    // Some are duplicated from Mth, but kept here as they might have slightly different parameter order or names

    /**
     * Linearly interpolates between [min, max].
     */
    public static float lerp(float delta, float min, float max)
    {
        return min + (max - min) * delta;
    }

    public static double lerp(double delta, double min, double max)
    {
        return min + (max - min) * delta;
    }

    /**
     * Linearly interpolates between four values on a unit square.
     */
    public static float lerp4(float value00, float value01, float value10, float value11, float delta0, float delta1)
    {
        final float value0 = lerp(delta1, value00, value01);
        final float value1 = lerp(delta1, value10, value11);
        return lerp(delta0, value0, value1);
    }

    /**
     * @return A t = inverseLerp(value, min, max) s.t. lerp(t, min, max) = value;
     */
    public static float inverseLerp(float value, float min, float max)
    {
        return (value - min) / (max - min);
    }

    public static int hash(long salt, BlockPos pos)
    {
        return hash(salt, pos.getX(), pos.getY(), pos.getZ());
    }

    public static int hash(long salt, int x, int y, int z)
    {
        long hash = salt ^ ((long) x * PRIME_X) ^ ((long) y * PRIME_Y) ^ z;
        hash *= 0x27d4eb2d;
        return (int) hash;
    }

    public static RandomSource fork(RandomSource random)
    {
        return new XoroshiroRandomSource(random.nextLong(), random.nextLong());
    }

    /**
     * A triangle function, with input {@code value} and parameters {@code amplitude, midpoint, frequency}.
     * A period T = 1 / frequency, with a sinusoidal shape. triangle(0) = midpoint, with triangle(+/-1 / (4 * frequency)) = the first peak.
     */
    public static float triangle(float amplitude, float midpoint, float frequency, float value)
    {
        return midpoint + amplitude * (Math.abs( 4f * frequency * value + 1f - 4f * Mth.floor(frequency * value + 0.75f)) - 1f);
    }

    /**
     * @return A random integer, uniformly distributed in the range [min, max).
     */
    public static int uniform(RandomSource random, int min, int max)
    {
        return min == max ? min : min + random.nextInt(max - min);
    }

    public static int uniform(Random random, int min, int max)
    {
        return min == max ? min : min + random.nextInt(max - min);
    }

    /**
     * @return A random float, uniformly distributed in the range [min, max).
     */
    public static float uniform(RandomSource random, float min, float max)
    {
        return random.nextFloat() * (max - min) + min;
    }

    public static double uniform(RandomSource random, double min, double max)
    {
        return random.nextDouble() * (max - min) + min;
    }

    /**
     * @return A random float, distributed around [-1, 1] in a triangle distribution X ~ pdf(t) = 1 - |t|.
     */
    public static float triangle(RandomSource random)
    {
        return random.nextFloat() - random.nextFloat() * 0.5f;
    }

    /**
     * @return A random integer, distributed around (-range, range) in a triangle distribution X ~ pmf(t) ~= (1 - |t|)
     */
    public static int triangle(RandomSource random, int range)
    {
        return random.nextInt(range) - random.nextInt(range);
    }

    /**
     * @return A random float, distributed around [-delta, delta] in a triangle distribution X ~ pdf(t) ~= (1 - |t|)
     */
    public static float triangle(RandomSource random, float delta)
    {
        return (random.nextFloat() - random.nextFloat()) * delta;
    }

    public static double triangle(RandomSource random, double delta)
    {
        return (random.nextDouble() - random.nextDouble()) * delta;
    }

    public static float easeInOutCubic(float x)
    {
        return x < 0.5f ? 4 * x * x * x : 1 - cube(-2 * x + 2) / 2;
    }

    private static float cube(float x)
    {
        return x * x * x;
    }

    /**
     * Returns ceil(num / div)
     *
     * @see Math#floorDiv(int, int)
     */
    public static int ceilDiv(int num, int div)
    {
        return (num + div - 1) / div;
    }

    /**
     * Checks the existence of a <a href="https://en.wikipedia.org/wiki/Perfect_matching">perfect matching</a> of a <a href="https://en.wikipedia.org/wiki/Bipartite_graph">bipartite graph</a>.
     * The graph is interpreted as the matches between the set of inputs, and the set of tests.
     * This algorithm computes the <a href="https://en.wikipedia.org/wiki/Edmonds_matrix">Edmonds Matrix</a> of the graph, which has the property that the determinant is identically zero iff the graph does not admit a perfect matching.
     */
    public static <T> boolean perfectMatchExists(List<T> inputs, List<? extends Predicate<T>> tests)
    {
        if (inputs.size() != tests.size())
        {
            return false;
        }
        final int size = inputs.size();
        final boolean[][] matrices = new boolean[size][];
        for (int i = 0; i < size; i++)
        {
            matrices[i] = new boolean[(size + 1) * (size + 1)];
        }
        final boolean[] matrix = matrices[size - 1];
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                matrix[i + size * j] = tests.get(i).test(inputs.get(j));
            }
        }
        return perfectMatchDet(matrices, size);
    }

    /**
     * Used by {@link Helpers#perfectMatchExists(List, List)}
     * Computes a symbolic determinant
     */
    private static boolean perfectMatchDet(boolean[][] matrices, int size)
    {
        // matrix true = nonzero = matches
        final boolean[] matrix = matrices[size - 1];
        return switch (size)
        {
            case 1 -> matrix[0];
            case 2 -> (matrix[0] && matrix[3]) || (matrix[1] && matrix[2]);
            default ->
            {
                for (int c = 0; c < size; c++)
                {
                    if (matrix[c])
                    {
                        perfectMatchSub(matrices, size, c);
                        if (perfectMatchDet(matrices, size - 1))
                        {
                            yield true;
                        }
                    }
                }
                yield false;
            }
        };
    }

    /**
     * Used by {@link Helpers#perfectMatchExists(List, List)}
     * Computes the symbolic minor of a matrix by removing an arbitrary column.
     */
    private static void perfectMatchSub(boolean[][] matrices, int size, int dc)
    {
        final int subSize = size - 1;
        final boolean[] matrix = matrices[subSize], sub = matrices[subSize - 1];
        for (int c = 0; c < subSize; c++)
        {
            final int c0 = c + (c >= dc ? 1 : 0);
            for (int r = 0; r < subSize; r++)
            {
                sub[c + subSize * r] = matrix[c0 + size * (r + 1)];
            }
        }
    }

    /**
     * Adds a tooltip based on an inventory, listing out the items inside.
     * Modified from {@link ShulkerBoxBlock#appendHoverText(ItemStack, Item.TooltipContext, List, TooltipFlag)}
     */
    public static void addInventoryTooltipInfo(Iterable<ItemStack> inventory, List<Component> tooltips)
    {
        int maximumItems = 0, totalItems = 0;
        for (ItemStack stack : inventory)
        {
            if (!stack.isEmpty())
            {
                ++totalItems;
                if (maximumItems <= 4)
                {
                    ++maximumItems;
                    tooltips.add(Tooltips.countOfItem(stack));
                }
            }
        }

        if (totalItems - maximumItems > 0)
        {
            tooltips.add(Component.translatable("container.shulkerBox.more", totalItems - maximumItems).withStyle(ChatFormatting.ITALIC));
        }
    }

    public static boolean isItem(ItemStack stack, ItemLike item)
    {
        return stack.is(item.asItem());
    }

    public static boolean isItem(ItemStack stack, TagKey<Item> tag)
    {
        return stack.is(tag);
    }

    @SuppressWarnings("deprecation")
    public static boolean isItem(Item item, TagKey<Item> tag)
    {
        return item.builtInRegistryHolder().is(tag);
    }

    public static Optional<Item> randomItem(TagKey<Item> tag, RandomSource random)
    {
        return getRandomElement(BuiltInRegistries.ITEM, tag, random);
    }

    public static Stream<Item> allItems(TagKey<Item> tag)
    {
        return BuiltInRegistries.ITEM.getOrCreateTag(tag).stream().map(Holder::value);
    }

    public static boolean isBlock(BlockState block, Block other)
    {
        return block.is(other);
    }

    public static boolean isBlock(BlockState state, TagKey<Block> tag)
    {
        return state.is(tag);
    }

    @SuppressWarnings("deprecation")
    public static boolean isBlock(Block block, TagKey<Block> tag)
    {
        return block.builtInRegistryHolder().is(tag);
    }

    public static Optional<Block> randomBlock(TagKey<Block> tag, RandomSource random)
    {
        return getRandomElement(BuiltInRegistries.BLOCK, tag, random);
    }

    public static Stream<Block> allBlocks(TagKey<Block> tag)
    {
        return BuiltInRegistries.BLOCK.getOrCreateTag(tag).stream().map(Holder::value);
    }

    public static boolean isFluid(FluidState state, TagKey<Fluid> tag)
    {
        return state.is(tag);
    }

    @SuppressWarnings("deprecation")
    public static boolean isFluid(Fluid fluid, TagKey<Fluid> tag)
    {
        return fluid.is(tag);
    }

    public static boolean isFluid(FluidState fluid, Fluid other)
    {
        return fluid.is(other);
    }

    public static Stream<Fluid> allFluids(TagKey<Fluid> tag)
    {
        return BuiltInRegistries.FLUID.getOrCreateTag(tag).stream().map(Holder::value);
    }

    public static boolean isEntity(Entity entity, TagKey<EntityType<?>> tag)
    {
        return isEntity(entity.getType(), tag);
    }

    public static boolean isEntity(EntityType<?> entity, TagKey<EntityType<?>> tag)
    {
        return entity.is(tag);
    }

    public static Optional<EntityType<?>> randomEntity(TagKey<EntityType<?>> tag, RandomSource random)
    {
        return getRandomElement(BuiltInRegistries.ENTITY_TYPE, tag, random);
    }

    public static boolean isDamageSource(DamageSource source, TagKey<DamageType> tag)
    {
        return source.is(tag);
    }

    private static <T> Optional<T> getRandomElement(Registry<T> registry, TagKey<T> tag, RandomSource random)
    {
        return registry.getTag(tag).flatMap(set -> set.getRandomElement(random)).map(Holder::value);
    }

    /**
     * This exists to fix a horrible case of vanilla seeding, which led to noticeable issues of feature clustering.
     * The key issue was that features with a chance placement, applied sequentially, would appear to generate on the same chunk much more often than was expected.
     * This was then identified as the problem by the lovely KaptainWutax <3. The following is a excerpt / paraphrase from our conversation:
     * <pre>
     * So you're running setSeed(n), setSeed(n + 1) and setSeed(n + 2) on the 3 structure respectively.
     * And n is something we can compute given a chunk and seed.
     * setSeed applies an xor on the lowest 35 bits and assigns that value internally
     * But like, since your seeds are like 1 apart
     * Even after the xor they're at worst 1 apart
     * You can convince yourself of that quite easily
     * So now nextFloat() does seed = 25214903917 * seed + 11 and returns (seed >> 24) / 2^24
     * Sooo lets see what the actual difference in seeds are between your 2 features in the worst case:
     * a = 25214903917, b = 11
     * So a * (seed + 1) + b = a * seed + b + a
     * As you can see the internal seed only varies by "a" amount
     * Now we can measure the effect that big number has no the upper bits since the seed is shifted
     * 25214903917/2^24 = 1502.92539101839
     * And that's by how much the upper 24 bits will vary
     * The effect on the next float are 1502 / 2^24 = 8.95261764526367e-5
     * Blam, so the first nextFloat() between setSeed(n) and setSeed(n + 1) is that distance apart ^
     * Which as you can see... isn't that far from 0
     * </pre>
     */
    public static void seedLargeFeatures(RandomSource random, long baseSeed, int index, int decoration)
    {
        random.setSeed(baseSeed);
        final long seed = (index * random.nextLong() * 203704237L) ^ (decoration * random.nextLong() * 758031792L) ^ baseSeed;
        random.setSeed(seed);
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable, T> T throwAsUnchecked(Throwable exception) throws E
    {
        throw (E) exception;
    }
}