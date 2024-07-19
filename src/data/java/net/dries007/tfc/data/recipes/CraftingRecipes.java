package net.dries007.tfc.data.recipes;

import java.util.List;
import net.minecraft.world.item.DyeColor;

public interface CraftingRecipes extends Recipes
{
    default void craftingRecipes()
    {
        // Removed Crafting Recipes
        remove(
            "anvil",
            "barrel",
            "beetroot_soup",
            "bone_meal",
            "bookshelf",
            "bow",
            "bowl",
            "bricks",
            "bucket",
            "campfire",
            "chest",
            "chest_minecart",
            "clock",
            "coast_armor_trim_smithing_template",
            "compass",
            "composter",
            "crafting_table",
            "creeper_banner_pattern",
            "dune_armor_trim_smithing_template",
            "enchanting_table",
            "eye_armor_trim_smithing_template",
            "fishing_rod",
            "fletching_table",
            "flint_and_steel",
            "flower_banner_pattern",
            "flower_pot",
            "furnace",
            "glass_bottle",
            "glass_pane",
            "hay_block",
            "host_armor_trim_smithing_template",
            "iron_door",
            "iron_trapdoor",
            "jack_o_lantern",
            "lantern",
            "lapis_lazuli",
            "leather_boots",
            "leather_chestplate",
            "leather_horse_armor",
            "leather_leggings",
            "lectern",
            "lightning_rod",
            "loom",
            "map",
            "melon",
            "melon_seeds",
            "mojang_banner_pattern",
            "mushroom_stew",
            "painting",
            "pumpkin_pie",
            "rabbit_stew_from_brown_mushroom",
            "rabbit_stew_from_red_mushroom",
            "rail",
            "raiser_armor_trim_smithing_template",
            "rib_armor_trim_smithing_template",
            "scaffolding",
            "sentry_armor_trim_smithing_template",
            "shaper_armor_trim_smithing_template",
            "shield",
            "silence_armor_trim_smithing_template",
            "skull_banner_pattern",
            "slime_ball",
            "smoker",
            "snout_armor_trim_smithing_template",
            "soul_campfire",
            "soul_lantern",
            "soul_torch",
            "spire_armor_trim_smithing_template",
            "spyglass",
            "sticky_piston",
            "stone_axe",
            "stone_hoe",
            "stone_shovel",
            "stone_sword",
            "suspicious_stew",
            "tide_armor_trim_smithing_template",
            "tinted_glass",
            "torch",
            "trapped_chest",
            "turtle_helmet",
            "vex_armor_trim_smithing_template",
            "ward_armor_trim_smithing_template",
            "wayfinder_armor_trim_smithing_template",
            "wheat",
            "wild_armor_trim_smithing_template",
            "wooden_axe",
            "wooden_hoe",
            "wooden_pickaxe",
            "wooden_shovel",
            "wooden_sword"
        );
        for (String material : List.of("diamond", "golden", "iron"))
            remove(
                material + "_axe",
                material + "_boots",
                material + "_chestplate",
                material + "_helmet",
                material + "_hoe",
                material + "_leggings",
                material + "_pickaxe",
                material + "_shovel",
                material + "_sword"
            );
        for (DyeColor color : DyeColor.values())
            remove(
                color.getSerializedName() + "_banner",
                color.getSerializedName() + "_bed",
                color.getSerializedName() + "_concrete_powder",
                color.getSerializedName() + "_stained_glass",
                color.getSerializedName() + "_stained_glass_pane",
                color.getSerializedName() + "_stained_glass_pane_from_glass_pane"
            );
    }
}
