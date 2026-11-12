package me.biquaternions.carpet;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.biquaternions.carpet.api.settings.CarpetRule;
import me.biquaternions.carpet.api.settings.Rule;
import me.biquaternions.carpet.api.settings.Validator;
import me.biquaternions.carpet.api.settings.Validators;
import me.biquaternions.carpet.utils.CommandHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

import static me.biquaternions.carpet.api.settings.RuleCategory.BUGFIX;
import static me.biquaternions.carpet.api.settings.RuleCategory.CLIENT;
import static me.biquaternions.carpet.api.settings.RuleCategory.COMMAND;
import static me.biquaternions.carpet.api.settings.RuleCategory.CREATIVE;
import static me.biquaternions.carpet.api.settings.RuleCategory.DISPENSER;
import static me.biquaternions.carpet.api.settings.RuleCategory.EXPERIMENTAL;
import static me.biquaternions.carpet.api.settings.RuleCategory.FEATURE;
import static me.biquaternions.carpet.api.settings.RuleCategory.SURVIVAL;
import static me.biquaternions.carpet.api.settings.RuleCategory.TNT;

/**
 * <p>All the rules available in Paper Carpet.</p>
 *
 * <p>Only the rules that could be ported to Paper are present here, see the README for the list of rules that
 * were dropped and why.</p>
 */
@SuppressWarnings({"CanBeFinal", "unused"})
public class CarpetSettings {

    private CarpetSettings() {
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Carpet itself
    // ---------------------------------------------------------------------------------------------------------------

    private static class CarpetPermissionLevel extends Validator<String> {
        @Override
        public String validate(@Nullable CommandSourceStack source, CarpetRule<String> currentRule, String newValue, String string) {
            if (source == null || CommandHelper.hasPermissionLevel(source, 4)) {
                return newValue;
            }
            return null;
        }

        @Override
        public String description() {
            return "This setting can only be set by admins with op level 4";
        }
    }

    @Rule(
            desc = "Carpet command permission level. Can only be set via config.yml or by level 4 operators",
            extra = "Players with the carpet.command permission can always use /carpet",
            categories = CREATIVE,
            validators = CarpetPermissionLevel.class,
            options = {"ops", "2", "4"}
    )
    public static String carpetCommandPermissionLevel = "ops";

    @Rule(desc = "Gbhs sgnf sadsgras fhskdpri!!!", categories = EXPERIMENTAL)
    public static boolean superSecretSetting = false;

    // ---------------------------------------------------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------------------------------------------------

    @Rule(desc = "Enables /spawn command for spawn tracking", extra = "Bypassed by the carpet.spawn permission", categories = COMMAND)
    public static String commandSpawn = "ops";

    @Rule(
            desc = "Enables /profile command to monitor game performance",
            extra = {"Only the overall tick time and entity counts are available on Paper,", "use spark (/spark) for in depth profiling", "Bypassed by the carpet.profile permission"},
            categories = COMMAND
    )
    public static String commandProfile = "true";

    @Rule(desc = "Enables /log command to monitor events via chat and overlays", extra = "Bypassed by the carpet.log permission", categories = COMMAND)
    public static String commandLog = "true";

    @Rule(
            desc = "sets these loggers in their default configurations for all new players",
            extra = "use csv, like 'tps,mobcaps' for multiple loggers, none for nothing",
            categories = {CREATIVE, SURVIVAL},
            options = {"none", "tps", "mobcaps,tps"},
            strict = false
    )
    public static String defaultLoggers = "none";

    @Rule(
            desc = "Enables /distance command to measure in game distance between points",
            extra = {"Also enables brown carpet placement action if 'carpets' rule is turned on as well", "Bypassed by the carpet.distance permission"},
            categories = COMMAND
    )
    public static String commandDistance = "true";

    @Rule(
            desc = "Enables /info command for blocks",
            extra = {"Also enables gray carpet placement action", "if 'carpets' rule is turned on as well", "Bypassed by the carpet.info permission"},
            categories = COMMAND
    )
    public static String commandInfo = "true";

    @Rule(
            desc = "Enables /perimeterinfo command",
            extra = {"... that scans the area around the block for potential spawnable spots", "Bypassed by the carpet.perimeterinfo permission"},
            categories = COMMAND
    )
    public static String commandPerimeterInfo = "true";

    @Rule(
            desc = "Enables /draw commands",
            extra = {"... allows for drawing simple shapes or", "other shapes which are sorta difficult to do normally", "Bypassed by the carpet.draw permission"},
            categories = COMMAND
    )
    public static String commandDraw = "ops";

    @Rule(desc = "Allows to track mobs AI via /track command", extra = "Bypassed by the carpet.track permission", categories = COMMAND)
    public static String commandTrackAI = "ops";

    @Rule(desc = "Enables /player command to control/spawn players", extra = "Bypassed by the carpet.player permission", categories = COMMAND)
    public static String commandPlayer = "ops";

    @Rule(desc = "Spawn offline players in online mode if online-mode player with specified name does not exist", categories = COMMAND)
    public static boolean allowSpawningOfflinePlayers = true;

    @Rule(desc = "Allows listing fake players on the multiplayer screen", categories = COMMAND)
    public static boolean allowListingFakePlayers = false;

    @Rule(desc = "Placing carpets may issue carpet commands for non-op players", categories = SURVIVAL)
    public static boolean carpets = false;

    @Rule(
            desc = "hoppers pointing to wool will count items passing through them",
            extra = {
                    "Enables /counter command, and actions while placing red and green carpets on wool blocks",
                    "Use /counter <color?> reset to reset the counter, and /counter <color?> to query",
                    "In survival, place green carpet on same color wool to query, red to reset the counters",
                    "Counters are per world and shared between players, 16 channels available",
                    "Items counted are destroyed, count up to one stack per tick per hopper"
            },
            categories = {COMMAND, CREATIVE, FEATURE}
    )
    public static boolean hopperCounters = false;

    // ---------------------------------------------------------------------------------------------------------------
    // Creative / survival tweaks
    // ---------------------------------------------------------------------------------------------------------------

    @Rule(desc = "Parrots don't get of your shoulders until you receive proper damage", categories = {SURVIVAL, FEATURE})
    public static boolean persistentParrots = false;

    @Rule(desc = "Players absorb XP instantly, without delay", categories = CREATIVE)
    public static boolean xpNoCooldown = false;

    @Rule(desc = "Explosions won't destroy blocks", categories = {CREATIVE, TNT})
    public static boolean explosionNoBlockDamage = false;

    @Rule(desc = "Experience will drop from all experience barring blocks with any explosion type", categories = {SURVIVAL, FEATURE})
    public static boolean xpFromExplosions = false;

    @Rule(desc = "Removes random TNT momentum when primed", categories = {CREATIVE, TNT})
    public static boolean tntPrimerMomentumRemoved = false;

    @Rule(
            desc = "Sets the horizontal random angle on TNT for debugging of TNT contraptions",
            extra = "Set to -1 for default behavior",
            categories = TNT,
            options = "-1",
            strict = false,
            validators = TNTAngleValidator.class
    )
    public static double hardcodeTNTangle = -1.0D;

    private static class TNTAngleValidator extends Validator<Double> {
        @Override
        public Double validate(@Nullable CommandSourceStack source, CarpetRule<Double> currentRule, Double newValue, String string) {
            return (newValue >= 0 && newValue < Math.PI * 2) || newValue == -1 ? newValue : null;
        }

        @Override
        public String description() {
            return "Must be between 0 and 2pi, or -1";
        }
    }

    @Rule(desc = "Merges stationary primed TNT entities", categories = TNT)
    public static boolean mergeTNT = false;

    @Rule(desc = "TNT doesn't update when placed against a power source", categories = {CREATIVE, TNT})
    public static boolean tntDoNotUpdate = false;

    @Rule(
            desc = "Players can flip and rotate blocks when holding cactus",
            extra = {
                    "Doesn't cause block updates when rotated/flipped",
                    "Applies to pistons, observers, droppers, repeaters, stairs, glazed terracotta etc..."
            },
            categories = {CREATIVE, SURVIVAL, FEATURE}
    )
    public static boolean flippinCactus = false;

    @Rule(desc = "Cactus in dispensers rotates blocks.", extra = "Rotates block anti-clockwise if possible", categories = {FEATURE, DISPENSER})
    public static boolean rotatorBlock = false;

    @Rule(
            desc = "Allows Budding Amethyst blocks to be mined",
            extra = {
                    "Adds an extra drop when mining with silk touch pickaxe",
                    "Unlike fabric-carpet, they can't be moved by pistons on Paper"
            },
            categories = FEATURE
    )
    public static boolean movableAmethyst = false;

    @Rule(desc = "Guardians turn into Elder Guardian when struck by lightning", categories = FEATURE)
    public static boolean renewableSponges = false;

    @Rule(desc = "Saplings turn into dead shrubs in hot climates and no water access", categories = FEATURE)
    public static boolean desertShrubs = false;

    @Rule(desc = "Silverfish drop a gravel item when breaking out of a block", categories = FEATURE)
    public static boolean silverFishDropGravel = false;

    @Rule(desc = "summoning a lightning bolt has all the side effects of natural lightning", categories = CREATIVE)
    public static boolean summonNaturalLightning = false;

    @Rule(desc = "Only husks spawn in desert temples", extra = "Monsters spawning naturally in desert temples are replaced by husks", categories = FEATURE)
    public static boolean huskSpawningInTemples = false;

    @Rule(desc = "Shulkers will respawn in end cities", extra = "Monsters spawning naturally in end city pieces are replaced by shulkers", categories = FEATURE)
    public static boolean shulkerSpawningInEndCities = false;

    @Rule(
            desc = "Piglins will respawn in bastion remnants",
            extra = {"Includes piglins, brutes, and a few hoglins", "Monsters spawning naturally in bastion pieces are replaced by them"},
            categories = FEATURE
    )
    public static boolean piglinsSpawningInBastions = false;

    @Rule(desc = "fill/clone/setblock and structure blocks cause block updates", extra = "On Paper, it only affects the /draw command", categories = CREATIVE)
    public static boolean fillUpdates = true;

    @Rule(desc = "Disables breaking of blocks caused by flowing liquids", categories = CREATIVE)
    public static boolean liquidDamageDisabled = false;

    @Rule(
            desc = "Customizable server list ping (Multiplayer menu) playerlist sample limit",
            options = {"0", "12", "20", "40"},
            categories = CREATIVE,
            strict = false,
            validators = Validators.NonNegativeNumber.class
    )
    public static int pingPlayerListLimit = 12;

    @Rule(
            desc = "Sets a different motd message on client trying to connect to the server",
            extra = {"use '_' to use the startup setting from server.properties", "Supports MiniMessage formatting"},
            options = "_",
            strict = false,
            categories = CREATIVE
    )
    public static String customMOTD = "_";

    private static class ViewDistanceValidator extends Validator<Integer> {
        @Override
        public Integer validate(@Nullable CommandSourceStack source, CarpetRule<Integer> currentRule, Integer newValue, String string) {
            if (newValue < 0 || newValue > 32) {
                if (source != null) {
                    source.getSender().sendMessage(Component.text("view distance has to be between 0 and 32", NamedTextColor.RED));
                }
                return null;
            }
            int vd = newValue >= 2 ? newValue : Bukkit.getViewDistance();
            for (World world : Bukkit.getWorlds()) {
                if (world.getViewDistance() != vd) {
                    world.setViewDistance(vd);
                }
            }
            return newValue;
        }

        @Override
        public String description() {
            return "You must choose a value from 0 (use server settings) to 32";
        }
    }

    @Rule(
            desc = "Changes the view distance of the server.",
            extra = "Set to 0 to not override the value in server settings.",
            options = {"0", "12", "16", "32"},
            categories = CREATIVE,
            strict = false,
            validators = ViewDistanceValidator.class
    )
    public static int viewDistance = 0;

    private static class SimulationDistanceValidator extends Validator<Integer> {
        @Override
        public Integer validate(@Nullable CommandSourceStack source, CarpetRule<Integer> currentRule, Integer newValue, String string) {
            if (newValue < 0 || newValue > 32) {
                if (source != null) {
                    source.getSender().sendMessage(Component.text("simulation distance has to be between 0 and 32", NamedTextColor.RED));
                }
                return null;
            }
            int sd = newValue >= 2 ? newValue : Bukkit.getSimulationDistance();
            for (World world : Bukkit.getWorlds()) {
                if (world.getSimulationDistance() != sd) {
                    world.setSimulationDistance(sd);
                }
            }
            return newValue;
        }

        @Override
        public String description() {
            return "You must choose a value from 0 (use server settings) to 32";
        }
    }

    @Rule(
            desc = "Changes the simulation distance of the server.",
            extra = "Set to 0 to not override the value in server settings.",
            options = {"0", "12", "16", "32"},
            categories = CREATIVE,
            strict = false,
            validators = SimulationDistanceValidator.class
    )
    public static int simulationDistance = 0;

    @Rule(desc = "Lava and water generate deepslate and cobbled deepslate instead below Y0", categories = FEATURE)
    public static boolean renewableDeepslate = false;

    @Rule(desc = "Increases for testing purposes number of blue skulls shot by the wither", categories = CREATIVE)
    public static boolean moreBlueSkulls = false;

    // ---------------------------------------------------------------------------------------------------------------
    // Client side rules, only effective for players with fabric-carpet installed on their client
    // ---------------------------------------------------------------------------------------------------------------

    @Rule(
            desc = "smooth client animations with low tps settings",
            extra = {"works only in SP, and will slow down players", "Requires fabric-carpet on the client"},
            categories = {CREATIVE, SURVIVAL, CLIENT}
    )
    public static boolean smoothClientAnimations = true;

    @Rule(
            desc = "Removes fog from client in the nether and the end",
            extra = {"Improves visibility, but looks weird", "Requires fabric-carpet on the client"},
            categories = CLIENT
    )
    public static boolean fogOff = false;

    @Rule(
            desc = "Creative flying speed multiplier",
            extra = {"Purely client side setting, sent to clients with fabric-carpet installed"},
            categories = {CREATIVE, CLIENT},
            options = {"1.0"},
            strict = false,
            validators = Validators.NonNegativeNumber.class
    )
    public static double creativeFlySpeed = 1.0;

    @Rule(
            desc = "Creative air drag",
            extra = {
                    "Increased drag will slow down your flight",
                    "So need to adjust speed accordingly",
                    "With 1.0 drag, using speed of 11 seems to matching vanilla speeds.",
                    "Purely client side setting, sent to clients with fabric-carpet installed"
            },
            categories = {CREATIVE, CLIENT},
            options = {"0.09"},
            strict = false,
            validators = Validators.Probability.class
    )
    public static double creativeFlyDrag = 0.09;

    @Rule(
            desc = "Customizable Structure Block outline render distance",
            extra = "Requires fabric-carpet on the client",
            options = {"96", "192", "2048"},
            categories = {CREATIVE, CLIENT},
            strict = false,
            validators = Validators.NonNegativeNumber.class
    )
    public static int structureBlockOutlineDistance = 96;

    @Rule(
            desc = "Lightning kills the items that drop when lightning kills an entity",
            extra = {"Setting to true will prevent lightning from killing drops", "Fixes [MC-206922](https://bugs.mojang.com/browse/MC-206922)."},
            categories = BUGFIX
    )
    public static boolean lightningKillsDropsFix = false;

}
