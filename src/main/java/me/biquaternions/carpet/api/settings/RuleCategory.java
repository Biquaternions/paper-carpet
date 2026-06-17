package me.biquaternions.carpet.api.settings;

/**
 * <p>Multiple categories for rules that Carpet uses and can be used to group other rules under the same
 * categories.</p>
 *
 * <p>These are not the only categories that can be used, you can create your own ones for your rules
 * and they will be added to the SettingsManager.</p>
 *
 */
public class RuleCategory {
    public static final String BUGFIX = "bugfix";
    public static final String SURVIVAL = "survival";
    public static final String CREATIVE = "creative";
    public static final String EXPERIMENTAL = "experimental";
    public static final String OPTIMIZATION = "optimization";
    public static final String FEATURE = "feature";
    public static final String COMMAND = "command";
    public static final String TNT = "tnt";
    public static final String DISPENSER = "dispenser";
    public static final String SCARPET = "scarpet";
}
