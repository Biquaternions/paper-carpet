package me.biquaternions.carpet.helper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@NullMarked
public class HopperCounter {

    private static final ConcurrentMap<UUID, Map<DyeColor, HopperCounter>> COUNTERS = new ConcurrentHashMap<>();

    public static void registerWorld(final World world) {
        Map<DyeColor, HopperCounter> counterEnumMap = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            counterEnumMap.put(color, new HopperCounter(color));
        }
        COUNTERS.put(world.getUID(), counterEnumMap);
    }

    public static void unregisterWorld(final World world) {
        COUNTERS.remove(world.getUID());
    }

    /**
     * The counter's color, determined by the color of wool it's pointing into
     */
    public final DyeColor color;
    /**
     * The component which makes each counter name be displayed in the color of
     * that counter.
     */
    private final Component coloredName;
    /**
     * All the items stored within the counter, as a map of {@link Material} mapped to a {@code long} of the amount of items
     * stored thus far from that item type.
     */
    private final ConcurrentMap<Material, Long> counter = new ConcurrentHashMap<>();
    /**
     * The starting tick of the counter, used to calculate in-game time. Only initialized when the first item enters the
     * counter
     */
    private long startTick;
    /**
     * The starting millisecond of the counter, used to calculate IRl time. Only initialized when the first item enters
     * the counter
     */
    private long startMillis;

    private HopperCounter(final DyeColor color) {
        this.startTick = -1L;
        this.color = color;
        TextColor textColor = TextColor.color(this.color.getColor().asRGB());
        this.coloredName = Component.text(textColor.asHexString()).appendSpace().append(Component.text(this.color.name()));
    }

    /**
     * Method used to add items to the counter. Note that this is when the {@link HopperCounter#startTick} and
     * {@link HopperCounter#startMillis} variables are initialized, so you can place the counters and then start the farm
     * after all the collection is sorted out.
     */
    public void add(World world, ItemStack stack) {
        if (this.startTick < 0) {
            this.startTick = world.getGameTime();
            this.startMillis = System.currentTimeMillis();
        }
        Material material = stack.getType();
        this.counter.merge(material, (long) stack.getAmount(), Long::sum);
    }

    /**
     * Resets the counter, clearing its items but keeping the clock running.
     */
    public void reset(World world) {
        this.counter.clear();
        this.startTick = world.getGameTime();
        this.startMillis = System.currentTimeMillis();
    }

    /**
     * Resets all counters, clearing their items.
     *
     * @param fresh Whether to start the clocks going immediately or later.
     */
    public static void resetAll(boolean fresh) {
        for (Map.Entry<UUID, Map<DyeColor, HopperCounter>> entry : COUNTERS.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }

            for (HopperCounter counter : entry.getValue().values()) {
                counter.reset(world);
                if (fresh) {
                    counter.startTick = -1;
                }
            }
        }
    }

    /**
     * Prints all the counters to chat, nicely formatted, and you can choose whether to display in game time or IRL time
     */
    public static List<Component> formatAll(boolean realtime) {
        final List<Component> text = new ArrayList<>();
        for (Map.Entry<UUID, Map<DyeColor, HopperCounter>> entry : COUNTERS.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }

            for (HopperCounter counter : entry.getValue().values()) {
                List<Component> temp = counter.format(world, realtime, false);
                if (temp.size() > 1) {
                    if (!text.isEmpty()) {
                        text.add(Component.empty());
                    }
                    text.addAll(temp);
                }
            }
        }

        if (text.isEmpty()) {
            text.add(Component.text("No items have been counted yet."));
        }
        return text;
    }

    /**
     * Prints a single counter's contents and timings to chat, with the option to keep it short (so no item breakdown,
     * only rates). Again, realtime displays IRL time as opposed to in game time.
     */
    public List<Component> format(World world, boolean realTime, boolean brief) {
        long ticks = Math.max(realTime ? (System.currentTimeMillis() - this.startMillis) / 50 : world.getGameTime() - this.startTick, 1);
        if (this.startTick < 0 || ticks == 0) {
            if (brief) {
                return Collections.singletonList(
                        Component.text("b").append(this.coloredName)
                                .append(Component.text("w : "))
                                .append(Component.text("gi -, -/h, - min "))
                );
            }
            return Collections.singletonList(this.coloredName.append(Component.text("w  hasn't started counting yet")));
        }
        long total = this.getTotalItems();
        if (total == 0) {
            if (brief) {
                return Collections.singletonList(Component.text("b").append(this.coloredName)
                        .append(Component.text("w : "))
                        .append(Component.text("wb 0"))
                        .append(Component.text("w , "))
                        .append(Component.text("wb 0"))
                        .append(Component.text("w /h, "))
                        .append(Component.text(String.format("wb %.1f ", ticks / (20.0 * 60.0))))
                        .append(Component.text("w min"))
                );
            }
            return Collections.singletonList(Component.text("w No items for ")
                    .append(this.coloredName)
                    .append(Component.text(String.format("w  yet (%.2f min.%s)", ticks / (20.0 * 60.0), (realTime ? " - real time" : ""))))
                    .append(Component.text("nb  [X]"))
                    .append(Component.text("^g reset"))
                    .append(Component.text("!/counter " + this.color.name() + " reset"))
            );
        }

        if (brief) {
            return Collections.singletonList(Component.text("b").append(this.coloredName)
                    .append(Component.text("w : "))
                    .append(Component.text("wb " + total))
                    .append(Component.text("w , "))
                    .append(Component.text("wb " + (total * (20 * 60 * 60) / ticks)))
                    .append(Component.text("w /h, "))
                    .append(Component.text(String.format("wb %.1f ", ticks / (20.0 * 60.0))))
                    .append(Component.text("w min"))
            );
        }
        List<Component> items = new ArrayList<>();
        items.add(Component.text("w Items for ").append(this.coloredName)
                .append(Component.text("w  ("))
                .append(Component.text(String.format("wb %.2f", ticks * 1.0 / (20 * 60))))
                .append(Component.text("w  min" + (realTime ? " - real time" : "") + "), "))
                .append(Component.text("w total: "))
                .append(Component.text("wb " + total))
                .append(Component.text("w , ("))
                .append(Component.text(String.format("wb %.1f", total * 1.0 * (20 * 60 * 60) / ticks)))
                .append(Component.text("w /h):"))
                .append(Component.text("nb [X]"))
                .append(Component.text("^g reset"))
                .append(Component.text("!/counter " + this.color + " reset"))
        );
        items.addAll(counter.entrySet().stream().sorted((e, f) -> Long.compare(f.getValue(), e.getValue())).map(e -> {
            Material material = e.getKey();
            TranslatableComponent itemName = Component.translatable(material.translationKey());
            Style itemStyle = itemName.style();
            TextColor color = guessColor(material, world);
            itemName = itemName.style((color != null) ? itemStyle.color(color) : itemStyle.decorate(TextDecoration.ITALIC));
            long count = e.getValue();
            return Component.text("g - ").append(itemName)
                    .append(Component.text("g : "))
                    .append(Component.text("wb " + count))
                    .append(Component.text("g , "))
                    .append(Component.text(String.format("wb %.1f", count * (20.0 * 60.0 * 60.0) / ticks)))
                    .append(Component.text("w /h"));
        }).toList());
        return items;
    }

    /**
     * Converts a color to have a low brightness and uniform color, so when it prints the items in different colors
     * it's not too flashy and bright, but enough that it's not dull to look at.
     */
    public static int appropriateColor(int color) {
        if (color == 0) {
            return Material.SNOW_BLOCK.createBlockData().getMapColor().asRGB();
        }

        int r = (color >> 16 & 255);
        int g = (color >> 8 & 255);
        int b = (color & 255);
        if (r < 70) {
            r = 70;
        }
        if (g < 70) {
            g = 70;
        }
        if (b < 70) {
            b = 70;
        }
        return (r << 16) + (g << 8) + b;
    }

    /**
     * Maps items that don't get a good block to reference for color, or those that color is wrong to a number of blocks, so we can get their colors easily with the
     * {@link org.bukkit.block.data.BlockData#getMapColor()} method as these items have those same colors.
     */
    private static final Map<Material, Material> DEFAULTS = Map.<Material, Material>ofEntries(
            Map.entry(Material.DANDELION, Material.YELLOW_WOOL),
            Map.entry(Material.POPPY, Material.RED_WOOL),
            Map.entry(Material.BLUE_ORCHID, Material.LIGHT_BLUE_WOOL),
            Map.entry(Material.ALLIUM, Material.MAGENTA_WOOL),
            Map.entry(Material.AZURE_BLUET, Material.SNOW_BLOCK),
            Map.entry(Material.RED_TULIP, Material.RED_WOOL),
            Map.entry(Material.ORANGE_TULIP, Material.ORANGE_WOOL),
            Map.entry(Material.WHITE_TULIP, Material.SNOW_BLOCK),
            Map.entry(Material.PINK_TULIP, Material.PINK_WOOL),
            Map.entry(Material.OXEYE_DAISY, Material.SNOW_BLOCK),
            Map.entry(Material.CORNFLOWER, Material.BLUE_WOOL),
            Map.entry(Material.WITHER_ROSE, Material.BLACK_WOOL),
            Map.entry(Material.LILY_OF_THE_VALLEY, Material.WHITE_WOOL),
            Map.entry(Material.BROWN_MUSHROOM, Material.BROWN_MUSHROOM_BLOCK),
            Map.entry(Material.RED_MUSHROOM, Material.RED_MUSHROOM_BLOCK),
            Map.entry(Material.STICK, Material.OAK_PLANKS),
            Map.entry(Material.GOLD_INGOT, Material.GOLD_BLOCK),
            Map.entry(Material.IRON_INGOT, Material.IRON_BLOCK),
            Map.entry(Material.DIAMOND, Material.DIAMOND_BLOCK),
            Map.entry(Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK),
            Map.entry(Material.SUNFLOWER, Material.YELLOW_WOOL),
            Map.entry(Material.LILAC, Material.MAGENTA_WOOL),
            Map.entry(Material.ROSE_BUSH, Material.RED_WOOL),
            Map.entry(Material.PEONY, Material.PINK_WOOL),
            Map.entry(Material.CARROT, Material.ORANGE_WOOL),
            Map.entry(Material.APPLE, Material.RED_WOOL),
            Map.entry(Material.WHEAT, Material.HAY_BLOCK),
            Map.entry(Material.PORKCHOP, Material.PINK_WOOL),
            Map.entry(Material.RABBIT, Material.PINK_WOOL),
            Map.entry(Material.CHICKEN, Material.WHITE_TERRACOTTA),
            Map.entry(Material.BEEF, Material.NETHERRACK),
            Map.entry(Material.ENCHANTED_GOLDEN_APPLE, Material.GOLD_BLOCK),
            Map.entry(Material.COD, Material.WHITE_TERRACOTTA),
            Map.entry(Material.SALMON, Material.ACACIA_PLANKS),
            Map.entry(Material.ROTTEN_FLESH, Material.BROWN_WOOL),
            Map.entry(Material.PUFFERFISH, Material.ORANGE_TERRACOTTA),
            Map.entry(Material.TROPICAL_FISH, Material.ORANGE_WOOL),
            Map.entry(Material.POTATO, Material.WHITE_TERRACOTTA),
            Map.entry(Material.MUTTON, Material.RED_WOOL),
            Map.entry(Material.BEETROOT, Material.NETHERRACK),
            Map.entry(Material.MELON_SLICE, Material.MELON),
            Map.entry(Material.POISONOUS_POTATO, Material.SLIME_BLOCK),
            Map.entry(Material.SPIDER_EYE, Material.NETHERRACK),
            Map.entry(Material.GUNPOWDER, Material.GRAY_WOOL),
            Map.entry(Material.TURTLE_SCUTE, Material.LIME_WOOL),
            Map.entry(Material.ARMADILLO_SCUTE, Material.ANCIENT_DEBRIS),
            Map.entry(Material.FEATHER, Material.WHITE_WOOL),
            Map.entry(Material.FLINT, Material.BLACK_WOOL),
            Map.entry(Material.LEATHER, Material.SPRUCE_PLANKS),
            Map.entry(Material.GLOWSTONE_DUST, Material.GLOWSTONE),
            Map.entry(Material.PAPER, Material.WHITE_WOOL),
            Map.entry(Material.BRICK, Material.BRICKS),
            Map.entry(Material.INK_SAC, Material.BLACK_WOOL),
            Map.entry(Material.SNOWBALL, Material.SNOW_BLOCK),
            Map.entry(Material.WATER_BUCKET, Material.WATER),
            Map.entry(Material.LAVA_BUCKET, Material.LAVA),
            Map.entry(Material.MILK_BUCKET, Material.WHITE_WOOL),
            Map.entry(Material.CLAY_BALL, Material.CLAY),
            Map.entry(Material.COCOA_BEANS, Material.COCOA),
            Map.entry(Material.BONE, Material.BONE_BLOCK),
            Map.entry(Material.COD_BUCKET, Material.BROWN_TERRACOTTA),
            Map.entry(Material.PUFFERFISH_BUCKET, Material.YELLOW_TERRACOTTA),
            Map.entry(Material.SALMON_BUCKET, Material.PINK_TERRACOTTA),
            Map.entry(Material.TROPICAL_FISH_BUCKET, Material.ORANGE_TERRACOTTA),
            Map.entry(Material.SUGAR, Material.WHITE_WOOL),
            Map.entry(Material.BLAZE_POWDER, Material.GOLD_BLOCK),
            Map.entry(Material.ENDER_PEARL, Material.WARPED_PLANKS),
            Map.entry(Material.NETHER_STAR, Material.DIAMOND_BLOCK),
            Map.entry(Material.PRISMARINE_CRYSTALS, Material.SEA_LANTERN),
            Map.entry(Material.PRISMARINE_SHARD, Material.PRISMARINE),
            Map.entry(Material.RABBIT_HIDE, Material.OAK_PLANKS),
            Map.entry(Material.CHORUS_FRUIT, Material.PURPUR_BLOCK),
            Map.entry(Material.SHULKER_SHELL, Material.SHULKER_BOX),
            Map.entry(Material.NAUTILUS_SHELL, Material.BONE_BLOCK),
            Map.entry(Material.HEART_OF_THE_SEA, Material.CONDUIT),
            Map.entry(Material.HONEYCOMB, Material.HONEYCOMB_BLOCK),
            Map.entry(Material.NAME_TAG, Material.BONE_BLOCK),
            Map.entry(Material.TOTEM_OF_UNDYING, Material.YELLOW_TERRACOTTA),
            Map.entry(Material.TRIDENT, Material.PRISMARINE),
            Map.entry(Material.GHAST_TEAR, Material.WHITE_WOOL),
            Map.entry(Material.PHANTOM_MEMBRANE, Material.BONE_BLOCK),
            Map.entry(Material.EGG, Material.BONE_BLOCK),
            // Map.entry(Material.,Material.),
            Map.entry(Material.COPPER_INGOT, Material.WAXED_COPPER_BLOCK),
            Map.entry(Material.AMETHYST_SHARD, Material.AMETHYST_BLOCK)
    );


    /**
     * Gets the color to print an item in when printing its count in a hopper counter.
     */
    public static @Nullable TextColor fromMaterial(Material material) {
        if (DEFAULTS.containsKey(material)) {
            Material blockMaterial = DEFAULTS.get(material);
            return TextColor.color(HopperCounter.appropriateColor(blockMaterial.createBlockData().getMapColor().asRGB()));
        }
//        if (material instanceof DyeItem dye) {
//            return TextColor.color(HopperCounter.appropriateColor(Optional.ofNullable(dye.getDefaultInstance().get(DataComponents.DYE)).orElse(DyeColor.WHITE).getMapColor().col));
//        }
        BlockData blockData = null;
        if (material.isBlock()) {
            blockData = material.createBlockData();
        }

        if (blockData != null) {
            return TextColor.color(HopperCounter.appropriateColor(blockData.getMapColor().asRGB()));
        }
        return null;
    }

    /**
     * Guesses the item's color from the item itself. It first calls {@link HopperCounter#fromMaterial} to see if it has a
     * valid color there, if not just makes a guess, and if that fails just returns null
     */
    public static @Nullable TextColor guessColor(Material material, World level) {
        TextColor direct = HopperCounter.fromMaterial(material);
        if (direct != null) {
            return direct;
        }

        NamespacedKey id = material.getKey();
        for (Recipe recipe : Bukkit.getRecipesFor(new ItemStack(material))) {
            switch (recipe) {
                case ShapelessRecipe r -> {
                    Optional<Material> match = r.getChoiceList().stream().map(HopperCounter::firstMaterialFromChoice).filter(Objects::nonNull).findFirst();
                    if (match.isPresent()) {
                        return HopperCounter.fromMaterial(match.get());
                    }
                }
                case ShapedRecipe r -> {
                    Optional<Material> match = r.getChoiceMap().values().stream().map(HopperCounter::firstMaterialFromChoice).filter(Objects::nonNull).findFirst();
                    if (match.isPresent()) {
                        return HopperCounter.fromMaterial(match.get());
                    }
                }
                case CookingRecipe<?> r -> {
                    Material input = HopperCounter.firstMaterialFromChoice(r.getInputChoice());
                    if (input != null) {
                        return HopperCounter.fromMaterial(input);
                    }
                }
                case SmithingRecipe r -> {
                    Material base = HopperCounter.firstMaterialFromChoice(r.getBase());
                    if (base != null) {
                        return HopperCounter.fromMaterial(base);
                    }
                    Material addition = HopperCounter.firstMaterialFromChoice(r.getAddition());
                    if (addition != null) {
                        return HopperCounter.fromMaterial(addition);
                    }
                }
                default -> {
                }
            }
        }

        return null;
    }

    private static @Nullable Material firstMaterialFromChoice(final RecipeChoice choice) {
        if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            return exactChoice.getChoices().getFirst().getType();
        }
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            return materialChoice.getChoices().getFirst();
        }
        return null;
    }

    /**
     * Returns the hopper counter for the given color
     */
    public static @Nullable HopperCounter getCounter(World world, DyeColor color) {
        Map<DyeColor, HopperCounter> counters = COUNTERS.get(world.getUID());
        if (counters == null) {
            return null;
        }
        return counters.get(color);
    }

    /**
     * Returns the hopper counter from the color name, if not null
     */
    public static @Nullable HopperCounter getCounter(World world, String color) {
        Map<DyeColor, HopperCounter> counters = COUNTERS.get(world.getUID());
        if (counters == null) {
            return null;
        }

        try {
            DyeColor colorEnum = DyeColor.valueOf(color.toUpperCase(Locale.ROOT));
            return counters.get(colorEnum);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The total number of items in the counter
     */
    public long getTotalItems() {
        return this.counter.isEmpty() ? 0 : this.counter.values().stream().mapToLong(Long::longValue).sum();
    }

}
