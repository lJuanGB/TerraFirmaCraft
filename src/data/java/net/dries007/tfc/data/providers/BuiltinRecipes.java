package net.dries007.tfc.data.providers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import com.mojang.serialization.Codec;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.packs.VanillaRecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rock.Ore;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.common.blocks.soil.SoilBlockType;
import net.dries007.tfc.common.items.HideItemType;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.common.recipes.BlastFurnaceRecipe;
import net.dries007.tfc.common.recipes.BloomeryRecipe;
import net.dries007.tfc.common.recipes.CollapseRecipe;
import net.dries007.tfc.common.recipes.LandslideRecipe;
import net.dries007.tfc.common.recipes.LoomRecipe;
import net.dries007.tfc.common.recipes.ScrapingRecipe;
import net.dries007.tfc.common.recipes.ingredients.BlockIngredient;
import net.dries007.tfc.common.recipes.outputs.ItemStackProvider;
import net.dries007.tfc.data.DataAccessor;
import net.dries007.tfc.data.recipes.AlloyRecipes;
import net.dries007.tfc.data.recipes.AnvilRecipes;
import net.dries007.tfc.data.recipes.BarrelRecipes;
import net.dries007.tfc.data.recipes.CastingRecipes;
import net.dries007.tfc.data.recipes.ChiselRecipes;
import net.dries007.tfc.data.recipes.CraftingRecipes;
import net.dries007.tfc.data.recipes.GlassRecipes;
import net.dries007.tfc.data.recipes.HeatRecipes;
import net.dries007.tfc.data.recipes.KnappingRecipes;
import net.dries007.tfc.data.recipes.PotRecipes;
import net.dries007.tfc.data.recipes.QuernRecipes;
import net.dries007.tfc.data.recipes.SewingRecipes;
import net.dries007.tfc.data.recipes.WeldingRecipes;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.data.FluidHeat;
import net.dries007.tfc.util.data.KnappingType;
import net.dries007.tfc.util.registry.RegistryHolder;

@SuppressWarnings("NotNullFieldNotInitialized")
public final class BuiltinRecipes extends VanillaRecipeProvider implements
    AnvilRecipes,
    AlloyRecipes,
    BarrelRecipes,
    CastingRecipes,
    ChiselRecipes,
    CraftingRecipes,
    GlassRecipes,
    HeatRecipes,
    KnappingRecipes,
    PotRecipes,
    QuernRecipes,
    SewingRecipes,
    WeldingRecipes
{
    final DataAccessor<FluidHeat> fluidHeat;
    final BuiltinItemHeat.Output itemHeat;

    // We store the list of recipe generated by vanilla (throwing away the actual recipes) in order to compare against recipes we are
    // removing. This helps prevent us keeping old recipe removals around.
    final Set<ResourceLocation> vanillaRecipes = new HashSet<>();
    final Set<ResourceLocation> removedRecipes = new HashSet<>();
    final Set<ResourceLocation> replacedRecipes = new HashSet<>();

    // This here, is a dirty hack that allows us to generate a recipe without a 'type' field, which is perfectly legal! As long as
    // we can ensure that the recipe will NEVER pass the condition - aka, if it is using a false condition. This is the most effective
    // way to remove recipes, that generates the least amount of log spam, but NeoForge's condition serializer won't let us not write
    // a real, actual recipe.
    final Codec<Unit> emptyRecipeCodec = Codec.STRING.fieldOf("type")
        .codec()
        .listOf()
        .fieldOf("neoforge:conditions")
        .xmap(l -> Unit.INSTANCE, r -> List.of("neoforge:false"))
        .codec();

    RecipeOutput output;
    HolderLookup.Provider lookup;

    public BuiltinRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, DataAccessor<FluidHeat> fluidHeat, BuiltinItemHeat.Output itemHeat)
    {
        super(output, lookup);
        this.fluidHeat = fluidHeat;
        this.itemHeat = itemHeat;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output, HolderLookup.Provider lookup)
    {
        this.lookup = lookup;
        return CompletableFuture.allOf(
            super.run(output, lookup),
            CompletableFuture.allOf(removedRecipes
                .stream()
                .map(id -> DataProvider.saveStable(output, lookup, emptyRecipeCodec, Unit.INSTANCE, recipePathProvider.json(id)))
                .toArray(CompletableFuture[]::new))
        );
    }

    @Override // This is only used in vanilla to build an "impossible" trigger - just don't cause that to happen
    @Deprecated // And warn ourselves if we use this method
    protected CompletableFuture<?> buildAdvancement(CachedOutput output, HolderLookup.Provider registries, AdvancementHolder advancement)
    {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    @SuppressWarnings("RedundantTypeArguments") // Actually necessary, IDEA is wrong
    protected void buildRecipes(RecipeOutput output)
    {
        this.output = output;

        // Invoke vanilla's recipe building, just to get the list of all vanilla recipes, so we know which ones we can legally remove
        super.buildRecipes(new RecipeOutput() {
            @Override
            public Advancement.Builder advancement()
            {
                return output.advancement();
            }

            @Override
            public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions)
            {
                vanillaRecipes.add(id);
            }
        });

        anvilRecipes();
        alloyRecipes();
        barrelRecipes();
        castingRecipes();
        chiselRecipes();
        craftingRecipes();
        glassRecipes();
        heatRecipes();
        knappingRecipes();
        potRecipes();
        quernRecipes();
        sewingRecipes();
        weldingRecipes();

        // Bloomery Recipes
        add(new BloomeryRecipe(
            SizedFluidIngredient.of(fluidOf(Metal.CAST_IRON), 100),
            SizedIngredient.of(Items.CHARCOAL, 2),
            ItemStackProvider.of(TFCItems.RAW_IRON_BLOOM),
            15 * ICalendar.TICKS_IN_HOUR
        ));

        // Blast Furnace Recipes
        add("pig_iron", new BlastFurnaceRecipe(
            SizedFluidIngredient.of(fluidOf(Metal.CAST_IRON), 1),
            Ingredient.of(TFCTags.Items.FLUX),
            new FluidStack(fluidOf(Metal.PIG_IRON), 1)
        ));

        // Loom Recipes
        add(new LoomRecipe(SizedIngredient.of(TFCItems.JUTE_FIBER, 12), ItemStackProvider.of(TFCItems.BURLAP_CLOTH), 12, Helpers.identifier("block/burlap")));
        add(new LoomRecipe(SizedIngredient.of(TFCItems.WOOL_YARN, 16), ItemStackProvider.of(TFCItems.WOOL_CLOTH), 16, Helpers.identifierMC("block/white_wool")));
        add(new LoomRecipe(SizedIngredient.of(Tags.Items.STRINGS, 24), ItemStackProvider.of(TFCItems.SILK_CLOTH), 24, Helpers.identifierMC("block/white_wool")));
        add(new LoomRecipe(SizedIngredient.of(TFCItems.WOOL_CLOTH, 4), ItemStackProvider.of(Items.WHITE_WOOL, 8), 4, Helpers.identifierMC("block/white_wool")));
        add(new LoomRecipe(SizedIngredient.of(TFCItems.SOAKED_PAPYRUS_STRIP, 4), ItemStackProvider.of(TFCItems.UNREFINED_PAPER), 8, Helpers.identifier("block/unrefined_paper")));

        // Scraping Recipes
        add(new ScrapingRecipe(
            Ingredient.of(TFCItems.UNREFINED_PAPER), ItemStackProvider.of(Items.PAPER),
            Helpers.identifier("block/unrefined_paper"), Helpers.identifier("block/paper"),
            ItemStackProvider.empty()
        ));

        for (HideItemType.Size size : HideItemType.Size.values())
        {
            final String sizeId = size.name().toLowerCase(Locale.ROOT);
            add(new ScrapingRecipe(
                Ingredient.of(TFCItems.HIDES.get(HideItemType.SHEEPSKIN).get(size)),
                ItemStackProvider.of(TFCItems.HIDES.get(HideItemType.RAW).get(size)),
                Helpers.identifier("item/hide/%s/sheepskin".formatted(sizeId)),
                Helpers.identifier("item/hide/%s/raw".formatted(sizeId)),
                ItemStackProvider.of(TFCItems.WOOL)
            ));
            add(new ScrapingRecipe(
                Ingredient.of(TFCItems.HIDES.get(HideItemType.SOAKED).get(size)),
                ItemStackProvider.of(TFCItems.HIDES.get(HideItemType.SCRAPED).get(size)),
                Helpers.identifier("item/hide/%s/soaked".formatted(sizeId)),
                Helpers.identifier("item/hide/%s/scraped".formatted(sizeId)),
                ItemStackProvider.empty()
            ));
        }

        // Collapse Recipes
        TFCBlocks.ROCK_BLOCKS.forEach((rock, blocks) -> {
            add(new CollapseRecipe(BlockIngredient.of(Stream.of(
                blocks.get(Rock.BlockType.RAW).get(),
                blocks.get(Rock.BlockType.HARDENED).get(),
                blocks.get(Rock.BlockType.SMOOTH).get(),
                blocks.get(Rock.BlockType.CRACKED_BRICKS).get(),
                TFCBlocks.GRADED_ORES.get(rock).values()
                    .stream()
                    .map(m -> m.get(Ore.Grade.POOR).get()),
                TFCBlocks.ORES.get(rock).values()
                    .stream()
                    .map(RegistryHolder::get)
            ).<Block>flatMap(Helpers::flatten)), blocks.get(Rock.BlockType.COBBLE).get().defaultBlockState()));
            TFCBlocks.GRADED_ORES.get(rock).forEach((ore, oreBlocks) -> {
                add(new CollapseRecipe(
                    BlockIngredient.of(oreBlocks.get(Ore.Grade.RICH).get()),
                    oreBlocks.get(Ore.Grade.NORMAL).get().defaultBlockState()));
                add(new CollapseRecipe(
                    BlockIngredient.of(oreBlocks.get(Ore.Grade.NORMAL).get()),
                    oreBlocks.get(Ore.Grade.POOR).get().defaultBlockState()));
            });
        });

        // Landslide Recipes
        for (SoilBlockType.Variant type : SoilBlockType.Variant.values())
        {
            final var blocks = pivot(TFCBlocks.SOIL, type);

            add(new LandslideRecipe(BlockIngredient.of(
                blocks.get(SoilBlockType.CLAY).get(),
                blocks.get(SoilBlockType.CLAY_GRASS).get()
            ), blocks.get(SoilBlockType.CLAY).get().defaultBlockState()));
            add(new LandslideRecipe(BlockIngredient.of(
                blocks.get(SoilBlockType.DIRT).get(),
                blocks.get(SoilBlockType.GRASS).get(),
                blocks.get(SoilBlockType.GRASS_PATH).get(),
                blocks.get(SoilBlockType.FARMLAND).get(),
                blocks.get(SoilBlockType.ROOTED_DIRT).get()
            ), blocks.get(SoilBlockType.DIRT).get().defaultBlockState()));
            add(new LandslideRecipe(BlockIngredient.of(
                blocks.get(SoilBlockType.MUD).get()
            ), blocks.get(SoilBlockType.MUD).get().defaultBlockState()));
        }
    }

    @Override
    public DataAccessor<FluidHeat> fluidHeat()
    {
        return fluidHeat;
    }

    @Override
    public BuiltinItemHeat.Output itemHeat()
    {
        return itemHeat;
    }

    @Override
    public HolderLookup.Provider lookup()
    {
        return lookup;
    }

    @Override
    public void add(String prefix, String name, Recipe<?> recipe)
    {
        output.accept(Helpers.identifier((prefix + "/" + name).toLowerCase(Locale.ROOT)), recipe, null);
    }

    @Override
    public void remove(String... names)
    {
        for (String name : names)
        {
            final ResourceLocation id = ResourceLocation.withDefaultNamespace(name);
            assert vanillaRecipes.contains(id) : "Recipe " + id + " was not a legal vanilla recipe to remove";
            assert !removedRecipes.contains(id) && !replacedRecipes.contains(id) : "Recipe " + id + " was already replaced or removed";
            removedRecipes.add(id);
        }
    }

    @Override
    public void replace(String name, Recipe<?> recipe)
    {
        final ResourceLocation id = ResourceLocation.withDefaultNamespace(name);
        assert vanillaRecipes.contains(id) : "Recipe " + id + " was not a legal vanilla recipe to replace";
        assert !removedRecipes.contains(id) && !replacedRecipes.contains(id) : "Recipe " + id + " was already replaced or removed";
        replacedRecipes.add(id);
        output.accept(id, recipe, null);
    }
}
