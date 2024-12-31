/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.data.recipes;

import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.Recipe;

import net.dries007.tfc.data.Accessors;

public interface Recipes extends Accessors
{
    HolderLookup.Provider lookup();

    default String nameOf(Recipe<?> recipe)
    {
        return nameOf(recipe.getResultItem(lookup()).getItem());
    }

    default void add(Recipe<?> recipe)
    {
        add(nameOf(recipe), recipe);
    }

    default void add(String name, Recipe<?> recipe)
    {
        add(Objects.requireNonNull(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()), "No recipe type").getPath(), name, recipe);
    }

    void add(String prefix, String name, Recipe<?> recipe);

    void remove(String... names);

    void replace(String name, Recipe<?> recipe);
}
