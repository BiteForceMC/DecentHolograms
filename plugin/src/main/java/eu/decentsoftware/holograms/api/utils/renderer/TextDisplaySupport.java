package eu.decentsoftware.holograms.api.utils.renderer;

import eu.decentsoftware.holograms.api.utils.reflect.ReflectionUtil;
import org.bukkit.entity.EntityType;

/**
 * Utility for checking whether this runtime supports Bukkit TextDisplay APIs.
 */
public final class TextDisplaySupport {

    private static final boolean SUPPORTED = detectSupport();

    private TextDisplaySupport() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Check whether the current server runtime supports TextDisplay entities and transformation APIs.
     *
     * @return True if supported, otherwise false.
     */
    public static boolean isSupported() {
        return SUPPORTED;
    }

    private static boolean detectSupport() {
        if (!ReflectionUtil.checkClassExists("org.bukkit.entity.Display")
                || !ReflectionUtil.checkClassExists("org.bukkit.entity.TextDisplay")
                || !ReflectionUtil.checkClassExists("org.bukkit.util.Transformation")
                || !ReflectionUtil.checkClassExists("org.joml.Vector3f")
                || !ReflectionUtil.checkClassExists("org.joml.Quaternionf")) {
            return false;
        }

        try {
            EntityType.valueOf("TEXT_DISPLAY");
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
