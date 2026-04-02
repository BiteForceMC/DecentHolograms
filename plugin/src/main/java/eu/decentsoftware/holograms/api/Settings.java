package eu.decentsoftware.holograms.api;

import com.google.common.collect.ImmutableMap;
import eu.decentsoftware.holograms.api.utils.config.CFG;
import eu.decentsoftware.holograms.api.utils.config.FileConfig;
import eu.decentsoftware.holograms.api.utils.config.Key;
import eu.decentsoftware.holograms.api.utils.Log;
import eu.decentsoftware.holograms.api.utils.renderer.TextDisplaySupport;
import lombok.experimental.UtilityClass;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"java:S1444", "java:S3008"})
@UtilityClass
public class Settings {

    private static final DecentHolograms DECENT_HOLOGRAMS = DecentHologramsAPI.get();
    private static FileConfig config;

    @Key("update-checker")
    public static boolean CHECK_FOR_UPDATES = true;
    @Key(value = "click-cooldown", min = 1, max = 300)
    public static int CLICK_COOLDOWN = 1;
    @Key("defaults.text")
    public static String DEFAULT_TEXT = "Blank Line";
    @Key("defaults.down-origin")
    public static boolean DEFAULT_DOWN_ORIGIN = false;
    @Key(value = "defaults.height.text", min = 0.0d, max = 2.5d)
    public static double DEFAULT_HEIGHT_TEXT = 0.3;
    @Key(value = "defaults.height.icon", min = 0.0d, max = 2.5d)
    public static double DEFAULT_HEIGHT_ICON = 0.6;
    @Key(value = "defaults.height.head", min = 0.0d, max = 2.5d)
    public static double DEFAULT_HEIGHT_HEAD = 0.75;
    @Key(value = "defaults.height.smallhead", min = 0.0d, max = 2.5d)
    public static double DEFAULT_HEIGHT_SMALLHEAD = 0.6;
    @Key(value = "defaults.display-range", min = 1, max = 48)
    public static int DEFAULT_DISPLAY_RANGE = 48;
    @Key(value = "defaults.update-range", min = 1, max = 48)
    public static int DEFAULT_UPDATE_RANGE = 48;
    @Key(value = "defaults.update-interval", min = 1, max = 1200)
    public static int DEFAULT_UPDATE_INTERVAL = 20;
    @Key(value = "defaults.lru-cache-size", min = 5, max = 1e4)
    public static int DEFAULT_LRU_CACHE_SIZE = 500;
    @Key("allow-placeholders-inside-animations")
    public static boolean ALLOW_PLACEHOLDERS_INSIDE_ANIMATIONS = false;
    /**
     * If true, the visibility of holograms will be updated when a player gets teleported or respawned.
     *
     * <p>By default, this is disabled because it causes visual glitches where even if a player gets teleported
     * by a fraction of a block, the holograms still disappear and reappear for them.</p>
     *
     * <p>Some clients (or client versions?) need this though, so if someone is experiencing issues with holograms
     * not showing up after a player gets teleported or respawned, they can enable this setting.</p>
     *
     * @since 2.8.9
     */
    @Key("update-visibility-on-teleport")
    public static boolean UPDATE_VISIBILITY_ON_TELEPORT = false;
    /**
     * Set this to true if you want holograms to appear at the player's eye level.
     *
     * <p>When enabled, holograms will be positioned at the player's eye height when created or moved.</p>
     *
     * <p>When disabled, holograms will be positioned at the player's feet height when created or moved (default).</p>
     */
    @Key("holograms-eye-level-positioning")
    public static boolean HOLOGRAMS_EYE_LEVEL_POSITIONING = false;
    @Key(value = "player-skin-connection-timeout", min = 1, max = 60)
    public static int PLAYER_SKIN_CONNECTION_TIMEOUT = 5;
    @Key("text-display.enabled")
    public static boolean TEXT_DISPLAY_ENABLED = true;
    @Key(value = "text-display.scale", min = 0.1d, max = 10.0d)
    public static double TEXT_DISPLAY_SCALE = 1.0d;
    @Key("text-display.billboard.enabled")
    public static boolean TEXT_DISPLAY_BILLBOARD_ENABLED = false;
    @Key("text-display.billboard.double-sided")
    public static boolean TEXT_DISPLAY_BILLBOARD_DOUBLE_SIDED = true;

    /**
     * If true, the warning about an unsupported TextDisplay API has already been logged.
     */
    private static boolean textDisplayUnsupportedWarningShown = false;

    public static Map<String, String> CUSTOM_REPLACEMENTS = ImmutableMap.<String, String>builder()
            .put("[x]", "\u2588")
            .put("[X]", "\u2588")
            .put("[/]", "\u258C")
            .put("[,]", "\u2591")
            .put("[,,]", "\u2592")
            .put("[,,,]", "\u2593")
            .put("[p]", "\u2022")
            .put("[P]", "\u2022")
            .put("[|]", "\u23B9")
            .build();

    // ========================================= //

    /**
     * Reload all Settings
     */
    public static void reload() {
        config = new FileConfig(DECENT_HOLOGRAMS.getPlugin(), "config.yml");

        CFG.load(DECENT_HOLOGRAMS.getPlugin(), Settings.class, config.getFile());

        if (TEXT_DISPLAY_ENABLED && !TextDisplaySupport.isSupported() && !textDisplayUnsupportedWarningShown) {
            Log.warn("TextDisplay line rendering is enabled in config, but this server version does not support Display entities. Falling back to legacy text renderer.");
            textDisplayUnsupportedWarningShown = true;
        }

        // -- Load custom replacements
        ConfigurationSection customReplacementsSection = config.getConfigurationSection("custom-replacements");
        if (customReplacementsSection != null) {
            Map<String, String> replacements = new HashMap<>();
            for (String key : customReplacementsSection.getKeys(false)) {
                if (!customReplacementsSection.isString(key)) {
                    continue;
                }
                replacements.put(key, customReplacementsSection.getString(key));
            }
            CUSTOM_REPLACEMENTS = replacements;
        }
    }

    /**
     * Check whether the modern TextDisplay API can be used on this server version.
     *
     * @return True if TextDisplay API is available, false otherwise.
     */
    public static boolean isTextDisplaySupported() {
        return TextDisplaySupport.isSupported();
    }

    /**
     * Check whether text lines should use TextDisplay instead of legacy packet armor stands.
     *
     * @return True if TextDisplay rendering should be used, false otherwise.
     */
    public static boolean shouldUseTextDisplayRenderer() {
        return TEXT_DISPLAY_ENABLED && isTextDisplaySupported();
    }

    public static FileConfig getConfig() {
        return config;
    }

}
