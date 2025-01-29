/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.client.render.blockentity;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.MoldBlockEntity;
import net.dries007.tfc.common.component.TFCComponents;
import net.dries007.tfc.common.component.fluid.FluidComponent;
import net.dries007.tfc.common.component.mold.IMold;
import net.dries007.tfc.common.items.TFCItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.dries007.tfc.TerraFirmaCraft;
import net.dries007.tfc.client.RenderHelpers;
import net.dries007.tfc.common.recipes.CastingRecipe;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.MetalItem;
import net.dries007.tfc.util.collections.IndirectHashCollection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

public class MoldBlockEntityRenderer implements BlockEntityRenderer<MoldBlockEntity> {

    @Override
    public void render(MoldBlockEntity mold, float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
            int combinedLight, int combinedOverlay) {
        VertexConsumer builder = buffer.getBuffer(RenderType.cutout());

        // Render flow into the mold
        if (mold.hasSource()) {
            ResourceLocation texture = IClientFluidTypeExtensions.of(mold.getSourceFluid().getFluidType())
                    .getStillTexture();
            int color = RenderHelpers.getFluidColor(mold.getSourceFluid());
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(RenderHelpers.BLOCKS_ATLAS)
                    .apply(texture);

            RenderHelpers.renderChannelFlow(poseStack, builder, sprite, color, combinedLight, combinedOverlay,
                    mold.getFlowSource(), true);
            if (mold.getFlowSource().getLeft() == Direction.UP) {
                RenderHelpers.renderChannelFlowCenter(poseStack, builder, sprite, color, combinedLight,
                        combinedOverlay);
            }
        }

        ItemStack moldStack = mold.getMoldStack();
        IMold moldItem = IMold.get(moldStack);
        if (moldItem != null) {
            FluidStack fluidInTank = moldItem.getFluidInTank(0);
            MetalItem solidMetal = MetalItem.unknown();

            float fillPercent = 0;
            boolean shouldRenderSolid = false;

            // There is an output item, so render a full, solid mold
            if (!mold.getOutputStack().isEmpty()) {
                fillPercent = 1;
                shouldRenderSolid = true;

                // We need to figure out the MetalItem solidMetal from the output item
                // We try to melt it, then cast it into an ingot, and get the MetalItem from
                // that
                HeatingRecipe recipe = HeatingRecipe.getRecipe(mold.getOutputStack());

                if (recipe != null) {
                    solidMetal = getMetalItemFromFluidStack(recipe.getDisplayOutputFluid());
                }
            }
            // There is some content in the tank
            else if (fluidInTank.getAmount() > 0) {
                fillPercent = ((float) fluidInTank.getAmount()) / moldItem.getTankCapacity(0);
                if (moldItem.isMolten()) {
                    shouldRenderSolid = false;
                } else {
                    shouldRenderSolid = true;
                    solidMetal = getMetalItemFromFluidStack(fluidInTank);
                }
            }

            if (fillPercent > 0) {
                if (shouldRenderSolid) {
                    final TextureAtlasSprite metalSprite = RenderHelpers.blockTexture(solidMetal.softTextureId());
                    RenderHelpers.renderTexturedQuads(
                            poseStack, builder, metalSprite, combinedLight, combinedOverlay,
                            RenderHelpers.getYVertices(2f / 16, 1f / 16, 2f / 16, 14f / 16,
                                    (1 + fillPercent * 0.95f) / 16, 14f / 16),
                            16f * (14f / 16 - 2f / 16), 16f * (14f / 16 - 2f / 16), 0, 1, 0, true);
                } else {
                    RenderHelpers.renderFluidFace(poseStack, fluidInTank, buffer, 2f / 16, 2f / 16, 14f / 16, 14f / 16,
                            (1 + fillPercent * 0.95f) / 16, combinedOverlay, combinedLight);
                }
            }

            // Render the mold
            poseStack.pushPose();
            Optional.ofNullable(MOLD_MODEL_CACHE.values.get(moldStack.getItem())).ifPresent(
                (model) -> Minecraft.getInstance().getItemRenderer().renderModelLists(
                    model, ItemStack.EMPTY, combinedLight, combinedOverlay, poseStack, builder
                )
            );
            poseStack.popPose();
        }

    }

    @Override
    public AABB getRenderBoundingBox(MoldBlockEntity mold) {
        if (mold.hasSource()) {
            Vec3 worldPosition = mold.getBlockPos().getCenter();
            if (mold.getFlowSource().getLeft() == Direction.UP) {
                return new AABB(worldPosition.add(-1.5, -0.5, -1.5),
                        worldPosition.add(1.5, 1 + mold.getFlowSource().getRight(), 1.5));
            } else {
                return new AABB(worldPosition.add(-1.5, -0.5, -1.5), worldPosition.add(1.5, 1.5, 1.5));
            }
        } else {
            return new AABB(mold.getBlockPos());
        }
    }

    private static final Map<Fluid, MetalItem> FLUID_TO_METAL_ITEM = new HashMap<>();

    private static MetalItem getMetalItemFromFluidStack(FluidStack fluidStack) {
        return FLUID_TO_METAL_ITEM.computeIfAbsent(fluidStack.getFluid(),
                (fluid) -> {
                    // Fill an ingot mold with this fluid, and try to retrieve the ingot
                    ItemStack ingotMold = new ItemStack(TFCItems.FIRE_INGOT_MOLD);
                    ingotMold.set(TFCComponents.FLUID, new FluidComponent(new FluidStack(fluid, 9999)));

                    CastingRecipe castingRecipe = CastingRecipe.get(IMold.get(ingotMold));
                    if (castingRecipe == null) {
                        return MetalItem.unknown();
                    }

                    ItemStack hopefullyIngot = castingRecipe.getResultItem();

                    return MetalItem.getOrUnknown(hopefullyIngot);
                });
    }

    private static final MoldModelCache MOLD_MODEL_CACHE = IndirectHashCollection.create(new MoldModelCache(new IdentityHashMap<>()));

    record MoldModelCache(Map<Item, BakedModel> values) implements IndirectHashCollection.Cache {
        @Override
        public void clear() {
            values.clear();
        }

        @Override
        public void reload(RecipeManager manager) {
            BuiltInRegistries.ITEM.getTagOrEmpty(TFCTags.Items.USABLE_IN_MOLD_TABLE).forEach(
                    (item) -> {
                        ResourceLocation moldLocation = BuiltInRegistries.ITEM.getKey(item.value());
                        ModelResourceLocation modelLocation = RenderHelpers.modelId(
                                ResourceLocation.fromNamespaceAndPath(
                                        moldLocation.getNamespace(),
                                        "mold/" + moldLocation.getPath()));

                        BakedModel model = Minecraft.getInstance().getModelManager().getModel(modelLocation);

                        if (model != null && model != Minecraft.getInstance().getModelManager().getMissingModel()) {
                            values.put(item.value(), model);
                        } else {
                            TerraFirmaCraft.LOGGER.error("No mold model loaded for mold item {}", moldLocation);
                        }
                    });
        }
    }
}