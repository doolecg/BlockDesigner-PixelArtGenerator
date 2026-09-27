package io.blockdesigner.pixelart.palette;

import io.blockdesigner.core.model.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * The blocks a picture may be built from. Only full, opaque cubes that look the same from every side of a face and
 * don't fall, glow or need support, so a build looks like its picture from any angle. Blocks the loaded Minecraft
 * version doesn't have are left out by the caller ({@link #blocks(String, Predicate)}).
 */
public final class BlockSets {
    public static final String COLOURS = "Colours (concrete, wool, terracotta)";
    public static final String CONCRETE = "Concrete";
    public static final String WOOL = "Wool";
    public static final String TERRACOTTA = "Terracotta";
    public static final String BUILDING = "Building blocks (stone, wood, brick)";
    public static final String ALL = "All solid blocks";
    public static final String CUSTOM = "Custom";

    public static final List<String> NAMES = List.of(COLOURS, CONCRETE, WOOL, TERRACOTTA, BUILDING, ALL, CUSTOM);

    static final List<String> DYES = List.of("white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink");

    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
            "cherry", "pale_oak", "bamboo", "crimson", "warped");

    private static final List<String> BUILDING_BLOCKS = List.of(
            "stone", "smooth_stone", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks",
            "cobblestone", "mossy_cobblestone", "andesite", "polished_andesite", "diorite", "polished_diorite", "granite",
            "polished_granite", "deepslate", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks", "deepslate_tiles",
            "tuff", "polished_tuff", "tuff_bricks", "calcite", "dripstone_block", "bricks", "mud_bricks", "packed_mud",
            "sandstone", "smooth_sandstone", "cut_sandstone", "red_sandstone", "smooth_red_sandstone", "cut_red_sandstone",
            "quartz_block", "smooth_quartz", "quartz_bricks", "prismarine", "prismarine_bricks", "dark_prismarine",
            "nether_bricks", "red_nether_bricks", "blackstone", "polished_blackstone", "polished_blackstone_bricks",
            "basalt", "smooth_basalt", "end_stone", "end_stone_bricks", "purpur_block", "obsidian", "netherrack",
            "resin_bricks", "copper_block", "exposed_copper", "weathered_copper", "oxidized_copper", "cut_copper",
            "iron_block", "gold_block", "diamond_block", "emerald_block", "lapis_block", "redstone_block", "coal_block",
            "netherite_block", "amethyst_block", "raw_iron_block", "raw_copper_block", "raw_gold_block");

    private static final List<String> NATURAL_BLOCKS = List.of(
            "dirt", "coarse_dirt", "rooted_dirt", "mud", "clay", "moss_block", "pale_moss_block", "snow_block",
            "packed_ice", "blue_ice", "hay_block", "melon", "pumpkin", "dried_kelp_block", "bone_block", "nether_wart_block",
            "warped_wart_block", "shroomlight", "sponge", "honeycomb_block", "brown_mushroom_block", "red_mushroom_block",
            "mushroom_stem", "terracotta", "magma_block", "crying_obsidian", "soul_soil", "sculk");

    private BlockSets() {
    }

    /** The ids (with namespace) in a named set, before checking which exist; empty for {@link #CUSTOM}. */
    public static List<String> ids(String set) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        switch (set) {
            case CONCRETE -> DYES.forEach(d -> out.add(d + "_concrete"));
            case WOOL -> DYES.forEach(d -> out.add(d + "_wool"));
            case TERRACOTTA -> {
                out.add("terracotta");
                DYES.forEach(d -> out.add(d + "_terracotta"));
            }
            case COLOURS -> {
                out.addAll(ids(CONCRETE));
                out.addAll(ids(WOOL));
                out.addAll(ids(TERRACOTTA));
            }
            case BUILDING -> {
                out.addAll(BUILDING_BLOCKS);
                WOODS.forEach(w -> out.add(w + "_planks"));
            }
            case ALL -> {
                out.addAll(ids(COLOURS));
                out.addAll(ids(BUILDING));
                out.addAll(NATURAL_BLOCKS);
                for (String w : WOODS) {
                    boolean nether = w.equals("crimson") || w.equals("warped");
                    if (w.equals("bamboo")) continue;
                    out.add(w + (nether ? "_hyphae" : "_wood"));
                    out.add("stripped_" + w + (nether ? "_hyphae" : "_wood"));
                }
            }
            default -> {
            }
        }
        List<String> ids = new ArrayList<>();
        for (String id : out) ids.add(id.contains(":") ? id : "minecraft:" + id);
        return ids;
    }

    /** The set's blocks that {@code exists} accepts (the loaded game's registry). */
    public static List<BlockState> blocks(String set, Predicate<String> exists) {
        List<BlockState> out = new ArrayList<>();
        for (String id : ids(set)) if (exists.test(id)) out.add(BlockState.of(id));
        return out;
    }
}
