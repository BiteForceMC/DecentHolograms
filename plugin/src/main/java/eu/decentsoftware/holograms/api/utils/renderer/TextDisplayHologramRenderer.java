package eu.decentsoftware.holograms.api.utils.renderer;

import eu.decentsoftware.holograms.nms.api.NmsHologramPartData;
import eu.decentsoftware.holograms.nms.api.renderer.NmsTextHologramRenderer;
import eu.decentsoftware.holograms.shared.DecentPosition;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Text renderer backed by Bukkit TextDisplay entities.
 *
 * <p>This renderer is only intended for server versions where TextDisplay APIs are available.</p>
 */
public class TextDisplayHologramRenderer implements NmsTextHologramRenderer {

    private static final double DISPLAY_VERTICAL_OFFSET = 0.5d;
    private static final EntityType TEXT_DISPLAY_ENTITY_TYPE = EntityType.valueOf("TEXT_DISPLAY");
    private static final Method SET_TEXT_METHOD = resolveRequiredMethod("org.bukkit.entity.TextDisplay", "setText", String.class);
    private static final Method SET_LINE_WIDTH_METHOD = resolveOptionalMethod("org.bukkit.entity.TextDisplay", "setLineWidth", int.class);
    private static final Method SET_SEE_THROUGH_METHOD = resolveOptionalMethod("org.bukkit.entity.TextDisplay", "setSeeThrough", boolean.class);
    private static final Method SET_DEFAULT_BACKGROUND_METHOD = resolveOptionalMethod("org.bukkit.entity.TextDisplay", "setDefaultBackground", boolean.class);
    private static final Method SET_BACKGROUND_COLOR_METHOD = resolveOptionalMethod("org.bukkit.entity.TextDisplay", "setBackgroundColor", Color.class);
    private static final Method SET_SHADOWED_METHOD = resolveOptionalMethod("org.bukkit.entity.TextDisplay", "setShadowed", boolean.class);
    private static final Method SET_BILLBOARD_METHOD = resolveOptionalMethod("org.bukkit.entity.Display", "setBillboard",
            resolveRequiredClass("org.bukkit.entity.Display$Billboard"));
    private static final Object BILLBOARD_CENTER = resolveEnumConstant("org.bukkit.entity.Display$Billboard", "CENTER");
    private static final Object BILLBOARD_FIXED = resolveEnumConstant("org.bukkit.entity.Display$Billboard", "FIXED");
    private static final Method SET_TRANSFORMATION_METHOD = resolveOptionalMethod("org.bukkit.entity.Display", "setTransformation",
            resolveRequiredClass("org.bukkit.util.Transformation"));
    private static final Constructor<?> TRANSFORMATION_CONSTRUCTOR = resolveRequiredConstructor(
            "org.bukkit.util.Transformation",
            resolveRequiredClass("org.joml.Vector3f"),
            resolveRequiredClass("org.joml.Quaternionf"),
            resolveRequiredClass("org.joml.Vector3f"),
            resolveRequiredClass("org.joml.Quaternionf"));
    private static final Constructor<?> VECTOR3F_CONSTRUCTOR = resolveRequiredConstructor("org.joml.Vector3f", float.class, float.class, float.class);
    private static final Constructor<?> QUATERNIONF_CONSTRUCTOR = resolveRequiredConstructor("org.joml.Quaternionf");
    private static final Method SET_VISIBLE_BY_DEFAULT_METHOD = resolveOptionalMethod("org.bukkit.entity.Entity", "setVisibleByDefault", boolean.class);
    private static final Method SHOW_ENTITY_METHOD = resolveOptionalMethod(Player.class, "showEntity", org.bukkit.plugin.Plugin.class, Entity.class);
    private static final Method HIDE_ENTITY_METHOD = resolveOptionalMethod(Player.class, "hideEntity", org.bukkit.plugin.Plugin.class, Entity.class);
    private static final Object TRANSPARENT_BACKGROUND = createTransparentBackground();

    private final JavaPlugin plugin;
    private final float scale;
    private final boolean backgroundEnabled;
    private final boolean fixedBillboardMode;
    private final float fixedBillboardYaw;
    private final float fixedBillboardPitch;
    private final boolean forceDoubleSided;
    private final Map<UUID, SpawnedTextDisplay> displaysByViewer = new ConcurrentHashMap<>();

    public TextDisplayHologramRenderer(JavaPlugin plugin,
                                       double scale,
                                       boolean backgroundEnabled,
                                       boolean doubleSided,
                                       boolean fixedBillboardMode,
                                       float fixedBillboardYaw,
                                       float fixedBillboardPitch) {
        this.plugin = plugin;
        this.scale = (float) Math.min(Math.max(scale, 0.1d), 10.0d);
        this.backgroundEnabled = backgroundEnabled;
        this.fixedBillboardMode = fixedBillboardMode;
        this.fixedBillboardYaw = normalizeYaw(fixedBillboardYaw);
        this.fixedBillboardPitch = Math.max(-90.0f, Math.min(90.0f, fixedBillboardPitch));
        // There is no direct Bukkit TextDisplay double-sided API, so this is implemented
        // by spawning a mirrored second TextDisplay entity when enabled.
        this.forceDoubleSided = doubleSided;
    }

    @Override
    public void display(Player player, NmsHologramPartData<String> data) {
        runOnMainThread(() -> {
            SpawnedTextDisplay displayEntity = getOrCreateDisplay(player, data.getPosition());
            if (displayEntity == null) {
                return;
            }
            setText(displayEntity, data.getContent());
            teleport(displayEntity, data.getPosition());
        });
    }

    @Override
    public void updateContent(Player player, NmsHologramPartData<String> data) {
        runOnMainThread(() -> {
            SpawnedTextDisplay displayEntity = getExistingDisplay(player.getUniqueId());
            if (displayEntity == null) {
                display(player, data);
                return;
            }
            setText(displayEntity, data.getContent());
        });
    }

    @Override
    public void move(Player player, NmsHologramPartData<String> data) {
        runOnMainThread(() -> {
            SpawnedTextDisplay displayEntity = getExistingDisplay(player.getUniqueId());
            if (displayEntity == null) {
                display(player, data);
                return;
            }
            teleport(displayEntity, data.getPosition());
        });
    }

    @Override
    public void hide(Player player) {
        runOnMainThread(() -> {
            SpawnedTextDisplay removed = displaysByViewer.remove(player.getUniqueId());
            if (removed == null) {
                return;
            }

            removeEntityById(removed.primaryEntityUuid);
            removeEntityById(removed.secondaryEntityUuid);
        });
    }

    @Override
    public double getHeight(NmsHologramPartData<String> data) {
        return 0.25d;
    }

    @Override
    public int[] getEntityIds() {
        return displaysByViewer.values().stream()
                .flatMapToInt(spawnedDisplay -> {
                    if (spawnedDisplay.secondaryEntityId > 0) {
                        return java.util.stream.IntStream.of(spawnedDisplay.primaryEntityId, spawnedDisplay.secondaryEntityId);
                    }
                    return java.util.stream.IntStream.of(spawnedDisplay.primaryEntityId);
                })
                .toArray();
    }

    private SpawnedTextDisplay getOrCreateDisplay(Player player, DecentPosition position) {
        SpawnedTextDisplay existing = getExistingDisplay(player.getUniqueId());
        if (existing != null) {
            return existing;
        }

        Location spawnLocation = positionToLocation(player, position);
        if (spawnLocation.getWorld() == null) {
            return null;
        }

        Entity spawned = spawnDisplay(spawnLocation, player);
        Entity mirrored = null;
        if (forceDoubleSided) {
            mirrored = spawnDisplay(toMirroredLocation(spawnLocation), player);
        }

        SpawnedTextDisplay created = new SpawnedTextDisplay(
                spawned.getUniqueId(),
                spawned.getEntityId(),
                mirrored != null ? mirrored.getUniqueId() : null,
                mirrored != null ? mirrored.getEntityId() : -1);
        displaysByViewer.put(player.getUniqueId(), created);
        return created;
    }

    private SpawnedTextDisplay getExistingDisplay(UUID viewerUuid) {
        SpawnedTextDisplay spawnedTextDisplay = displaysByViewer.get(viewerUuid);
        if (spawnedTextDisplay == null) {
            return null;
        }

        Entity primaryEntity = getValidEntity(spawnedTextDisplay.primaryEntityUuid);
        if (primaryEntity == null) {
            displaysByViewer.remove(viewerUuid);
            return null;
        }
        if (spawnedTextDisplay.secondaryEntityUuid != null) {
            Entity secondaryEntity = getValidEntity(spawnedTextDisplay.secondaryEntityUuid);
            if (secondaryEntity == null) {
                primaryEntity.remove();
                displaysByViewer.remove(viewerUuid);
                return null;
            }
        }
        return spawnedTextDisplay;
    }

    private Entity spawnDisplay(Location location, Player viewer) {
        Entity spawned = location.getWorld().spawnEntity(location, TEXT_DISPLAY_ENTITY_TYPE);
        applyDisplayDefaults(spawned);
        applyPlayerVisibility(spawned, viewer);
        return spawned;
    }

    private void applyDisplayDefaults(Entity entity) {
        entity.setPersistent(false);
        entity.setGravity(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);

        invokeIfPresent(SET_LINE_WIDTH_METHOD, entity, Integer.MAX_VALUE);
        invokeIfPresent(SET_SEE_THROUGH_METHOD, entity, true);
        invokeIfPresent(SET_DEFAULT_BACKGROUND_METHOD, entity, backgroundEnabled);
        if (!backgroundEnabled && TRANSPARENT_BACKGROUND != null) {
            invokeIfPresent(SET_BACKGROUND_COLOR_METHOD, entity, TRANSPARENT_BACKGROUND);
        }
        invokeIfPresent(SET_SHADOWED_METHOD, entity, false);
        Object billboardValue = fixedBillboardMode ? BILLBOARD_FIXED : BILLBOARD_CENTER;
        if (SET_BILLBOARD_METHOD != null && billboardValue != null) {
            invokeIfPresent(SET_BILLBOARD_METHOD, entity, billboardValue);
        }
        applyScale(entity);
    }

    private void applyPlayerVisibility(Entity entity, Player viewer) {
        // Version split:
        // On modern APIs we can disable default visibility and explicitly show the entity only
        // to the intended viewer, which preserves per-player placeholder output behavior.
        if (SET_VISIBLE_BY_DEFAULT_METHOD != null && SHOW_ENTITY_METHOD != null) {
            invokeIfPresent(SET_VISIBLE_BY_DEFAULT_METHOD, entity, false);
            invokeIfPresent(SHOW_ENTITY_METHOD, viewer, plugin, entity);
            return;
        }

        // Fallback path if selective visibility API is unavailable.
        if (HIDE_ENTITY_METHOD != null) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getUniqueId().equals(viewer.getUniqueId())) {
                    continue;
                }
                invokeIfPresent(HIDE_ENTITY_METHOD, onlinePlayer, plugin, entity);
            }
        }
    }

    private void setText(SpawnedTextDisplay display, String text) {
        setTextForEntity(display.primaryEntityUuid, text);
        setTextForEntity(display.secondaryEntityUuid, text);
    }

    private void setTextForEntity(UUID entityUuid, String text) {
        if (entityUuid == null) {
            return;
        }
        Entity entity = getValidEntity(entityUuid);
        if (entity == null) {
            return;
        }
        invokeRequired(SET_TEXT_METHOD, entity, text);
    }

    private void teleport(SpawnedTextDisplay display, DecentPosition position) {
        Entity primary = getValidEntity(display.primaryEntityUuid);
        if (primary != null) {
            Location target = positionToLocation(primary.getLocation(), position);
            teleportEntity(primary, target);
        }

        if (display.secondaryEntityUuid != null) {
            Entity secondary = getValidEntity(display.secondaryEntityUuid);
            if (secondary != null) {
                Location target = positionToLocation(secondary.getLocation(), position);
                teleportEntity(secondary, toMirroredLocation(target));
            }
        }
    }

    private void teleportEntity(Entity entity, Location target) {
        entity.teleport(target);
    }

    private Location positionToLocation(Player player, DecentPosition position) {
        return positionToLocation(new Location(player.getWorld(), 0, 0, 0), position);
    }

    private Location positionToLocation(Location base, DecentPosition position) {
        float yaw = fixedBillboardMode ? fixedBillboardYaw : position.getYaw();
        float pitch = fixedBillboardMode ? fixedBillboardPitch : position.getPitch();
        return new Location(
                base.getWorld(),
                position.getX(),
                position.getY() - DISPLAY_VERTICAL_OFFSET,
                position.getZ(),
                yaw,
                pitch);
    }

    private Location toMirroredLocation(Location primaryLocation) {
        Location mirrored = primaryLocation.clone();
        mirrored.setYaw(normalizeYaw(mirrored.getYaw() + 180.0f));
        mirrored.setPitch(-mirrored.getPitch());
        return mirrored;
    }

    private void applyScale(Entity entity) {
        if (SET_TRANSFORMATION_METHOD == null) {
            return;
        }

        Object transformation = createScaleTransformation();
        if (transformation != null) {
            invokeIfPresent(SET_TRANSFORMATION_METHOD, entity, transformation);
        }
    }

    private Object createScaleTransformation() {
        try {
            Object translation = VECTOR3F_CONSTRUCTOR.newInstance(0.0f, 0.0f, 0.0f);
            Object leftRotation = QUATERNIONF_CONSTRUCTOR.newInstance();
            Object scaleVector = VECTOR3F_CONSTRUCTOR.newInstance(scale, scale, scale);
            Object rightRotation = QUATERNIONF_CONSTRUCTOR.newInstance();
            return TRANSFORMATION_CONSTRUCTOR.newInstance(translation, leftRotation, scaleVector, rightRotation);
        } catch (Exception e) {
            return null;
        }
    }

    private void runOnMainThread(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, runnable);
        } catch (IllegalPluginAccessException ignored) {
            // Plugin is shutting down; ignore.
        }
    }

    private static void invokeRequired(Method method, Object target, Object... args) {
        try {
            method.invoke(target, args);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to invoke method: " + method.getName(), e);
        }
    }

    private static void invokeIfPresent(Method method, Object target, Object... args) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(target, args);
        } catch (Exception ignored) {
            // Keep renderer resilient if a specific optional call fails.
        }
    }

    private static Class<?> resolveRequiredClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Missing required class: " + className, e);
        }
    }

    private static Method resolveRequiredMethod(String className, String methodName, Class<?>... parameterTypes) {
        Class<?> clazz = resolveRequiredClass(className);
        Method method = resolveOptionalMethod(clazz, methodName, parameterTypes);
        if (method == null) {
            throw new IllegalStateException("Missing required method: " + className + "#" + methodName + Arrays.toString(parameterTypes));
        }
        return method;
    }

    private static Method resolveOptionalMethod(String className, String methodName, Class<?>... parameterTypes) {
        return resolveOptionalMethod(resolveRequiredClass(className), methodName, parameterTypes);
    }

    private static Method resolveOptionalMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Constructor<?> resolveRequiredConstructor(String className, Class<?>... parameterTypes) {
        Class<?> clazz = resolveRequiredClass(className);
        try {
            Constructor<?> constructor = clazz.getConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Missing required constructor: " + className + Arrays.toString(parameterTypes), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object resolveEnumConstant(String className, String constant) {
        Class<?> enumClass = resolveRequiredClass(className);
        if (!enumClass.isEnum()) {
            throw new IllegalStateException(className + " is not an enum class");
        }
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), constant);
    }

    private static Entity getValidEntity(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(uuid);
        if (entity == null || !entity.isValid()) {
            return null;
        }
        return entity;
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw;
        while (normalized <= -180.0f) {
            normalized += 360.0f;
        }
        while (normalized > 180.0f) {
            normalized -= 360.0f;
        }
        return normalized;
    }

    private static void removeEntityById(UUID entityUuid) {
        if (entityUuid == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(entityUuid);
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private static Object createTransparentBackground() {
        try {
            Method fromArgbMethod;
            try {
                fromArgbMethod = Color.class.getMethod("fromARGB", int.class, int.class, int.class, int.class);
                return fromArgbMethod.invoke(null, 0, 0, 0, 0);
            } catch (NoSuchMethodException ignored) {
                fromArgbMethod = Color.class.getMethod("fromARGB", int.class);
                return fromArgbMethod.invoke(null, 0);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static class SpawnedTextDisplay {
        private final UUID primaryEntityUuid;
        private final int primaryEntityId;
        private final UUID secondaryEntityUuid;
        private final int secondaryEntityId;

        private SpawnedTextDisplay(UUID primaryEntityUuid, int primaryEntityId, UUID secondaryEntityUuid, int secondaryEntityId) {
            this.primaryEntityUuid = primaryEntityUuid;
            this.primaryEntityId = primaryEntityId;
            this.secondaryEntityUuid = secondaryEntityUuid;
            this.secondaryEntityId = secondaryEntityId;
        }
    }
}
