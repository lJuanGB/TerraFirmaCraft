/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.compat.jei.category;

import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.recipes.QuernRecipe;

public class QuernRecipeCategory extends SimpleItemRecipeCategory<QuernRecipe>
{
    public QuernRecipeCategory(RecipeType<QuernRecipe> type, IGuiHelper helper)
    {
        super(type, helper, new ItemStack(TFCBlocks.QUERN.get()));
    }

    @Override
    protected TagKey<Item> getToolTag()
    {
        return TFCTags.Items.QUERN_HANDSTONES;
    }
}
