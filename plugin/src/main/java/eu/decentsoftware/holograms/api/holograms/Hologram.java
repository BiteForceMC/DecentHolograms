package eu.decentsoftware.holograms.api.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.DecentHolograms;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import eu.decentsoftware.holograms.api.Settings;
import eu.decentsoftware.holograms.api.actions.Action;
import eu.decentsoftware.holograms.api.actions.ClickType;
import eu.decentsoftware.holograms.api.holograms.enums.EnumFlag;
import eu.decentsoftware.holograms.api.holograms.objects.UpdatingHologramObject;
import eu.decentsoftware.holograms.api.utils.Log;
import eu.decentsoftware.holograms.api.utils.config.FileConfig;
import eu.decentsoftware.holograms.api.utils.event.EventFactory;
import eu.decentsoftware.holograms.api.utils.exception.LocationParseException;
import eu.decentsoftware.holograms.api.utils.location.LocationUtils;
import eu.decentsoftware.holograms.api.utils.reflect.Version;
import eu.decentsoftware.holograms.api.utils.scheduler.S;
import eu.decentsoftware.holograms.api.utils.tick.ITicked;
import eu.decentsoftware.holograms.event.HologramClickEvent;
import eu.decentsoftware.holograms.nms.api.renderer.NmsClickableHologramRenderer;
import eu.decentsoftware.holograms.shared.DecentPosition;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Getter
@Setter
public class Hologram extends UpdatingHologramObject implements ITicked {

    private static final DecentHolograms DECENT_HOLOGRAMS = DecentHologramsAPI.get();

    /*
     *	Hologram Cache
     */

    /**
     * This map contains all cached holograms. This map is used to get holograms by name.
     * <p>
     * Holograms are cached when they are loaded from files or created. They are removed
     * from the cache when they are deleted.
     * <p>
     * Holograms, that are only in this map and not in the {@link HologramManager}, are not
     * editable via commands. They are only editable via the API.
     *
     * @see #getCachedHologram(String)
     */
    private static final @NonNull Map<String, Hologram> CACHED_HOLOGRAMS;

    static {
        CACHED_HOLOGRAMS = new ConcurrentHashMap<>();
    }

    /**
     * @see DHAPI#getHologram(String)
     */
    public static Hologram getCachedHologram(@NonNull String name) {
        return CACHED_HOLOGRAMS.get(name);
    }

    @NonNull
    @Contract(pure = true)
    public static Set<String> getCachedHologramNames() {
        return CACHED_HOLOGRAMS.keySet();
    }

    @NonNull
    @Contract(pure = true)
    public static Collection<Hologram> getCachedHolograms() {
        return CACHED_HOLOGRAMS.values();
    }

    /*
     *	Static Methods
     */

    @SuppressWarnings("unchecked")
    @NonNull
    public static Hologram fromFile(final @NotNull String filePath) throws LocationParseException, IllegalArgumentException {
        Hologram hologram = null;
        try {
            final FileConfig config = new FileConfig(DECENT_HOLOGRAMS.getPlugin(), "holograms/" + filePath);
            final String fileName = new File(filePath).getName();

            // Parse hologram name
            String name;
            if (fileName.toLowerCase().startsWith("hologram_") && fileName.length() > "hologram_".length()) {
                name = fileName.substring("hologram_".length(), fileName.length() - 4);
            } else {
                name = fileName.substring(0, fileName.length() - 4);
            }

            if (name.isEmpty()) {
                // This shouldn't happen when loading holograms from files.
                throw new IllegalArgumentException("Hologram name cannot be null or empty.");
            }

            if (Hologram.getCachedHologramNames().contains(name)) {
                throw new IllegalArgumentException("Hologram with name '" + name + "' already exists.");
            }

            // Get hologram location
            String locationString = config.getString("location");
            Location location = LocationUtils.asLocationE(locationString);

            boolean enabled = true;
            if (config.isBoolean("enabled")) {
                enabled = config.getBoolean("enabled");
            }

            hologram = new Hologram(name, location, config, enabled);
            if (config.isString("permission")) {
                hologram.setPermission(config.getString("permission"));
            }
            hologram.setDisplayRange(config.getInt("display-range", Settings.DEFAULT_DISPLAY_RANGE));
            hologram.setUpdateRange(config.getInt("update-range", Settings.DEFAULT_UPDATE_RANGE));
            hologram.setUpdateInterval(config.getInt("update-interval", Settings.DEFAULT_UPDATE_INTERVAL));
            hologram.addFlags(config.getStringList("flags").stream().map(EnumFlag::valueOf).toArray(EnumFlag[]::new));
            if (config.isBoolean("down-origin")) {
                hologram.setDownOrigin(config.getBoolean("down-origin", Settings.DEFAULT_DOWN_ORIGIN));
            }
            if (config.contains("text-display.enabled")) {
                hologram.setTextDisplayEnabled(config.getBoolean("text-display.enabled"));
            }
            if (config.contains("text-display.scale")) {
                hologram.setTextDisplayScale(config.getDouble("text-display.scale"));
            }
            if (config.contains("text-display.background.enabled")) {
                hologram.setTextDisplayBackgroundEnabled(config.getBoolean("text-display.background.enabled"));
            } else if (config.contains("text-display.background")) {
                hologram.setTextDisplayBackgroundEnabled(config.getBoolean("text-display.background"));
            }
            if (config.contains("text-display.double-sided.enabled")) {
                hologram.setTextDisplayDoubleSided(config.getBoolean("text-display.double-sided.enabled"));
            } else if (config.contains("text-display.double-sided")) {
                hologram.setTextDisplayDoubleSided(config.getBoolean("text-display.double-sided"));
            }
            if (config.contains("text-display.billboard.enabled")) {
                hologram.setTextDisplayBillboardEnabled(config.getBoolean("text-display.billboard.enabled"));
            }
            if (config.contains("text-display.billboard.yaw")) {
                hologram.setTextDisplayBillboardYaw((float) config.getDouble("text-display.billboard.yaw"));
            }
            if (config.contains("text-display.billboard.pitch")) {
                hologram.setTextDisplayBillboardPitch((float) config.getDouble("text-display.billboard.pitch"));
            }

            if (!config.contains("pages") && config.contains("lines")) {
                // Old Config
                HologramPage page = hologram.getPage(0);
                Set<String> keysLines = config.getConfigurationSection("lines").getKeys(false);
                for (int j = 1; j <= keysLines.size(); j++) {
                    String path = "lines." + j;
                    HologramLine line = HologramLine.fromFile(config.getConfigurationSection(path), page, page.getNextLineLocation());
                    page.addLine(line);
                }
                config.set("lines", null);
                hologram.save();
                return hologram;
            }

            // New Config
            boolean firstPage = true;
            for (Map<?, ?> map : config.getMapList("pages")) {
                HologramPage page;
                if (firstPage) {
                    page = hologram.getPage(0);
                    firstPage = false;
                } else {
                    page = hologram.addPage();
                }

                // Load click actions
                if (map.containsKey("actions")) {
                    Map<String, List<String>> actionsMap = (Map<String, List<String>>) map.get("actions");
                    for (ClickType clickType : ClickType.values()) {
                        if (actionsMap.containsKey(clickType.name())) {
                            List<String> clickTypeActions = actionsMap.get(clickType.name());
                            for (String clickTypeAction : clickTypeActions) {
                                try {
                                    page.addAction(clickType, new Action(clickTypeAction));
                                } catch (Exception e) {
                                    Log.warn("Failed to parse action '%s' for hologram '%s' at page %s! Skipping...",
                                            e, clickTypeAction, hologram.getName(), page.getIndex());
                                }
                            }
                        }
                    }
                }

                // Load lines
                if (map.containsKey("lines")) {
                    for (Map<?, ?> lineMap : (List<Map<?, ?>>) map.get("lines")) {
                        Map<String, Object> values = null;
                        try {
                            values = (Map<String, Object>) lineMap;
                        } catch (Exception ignored) {
                            // Ignore
                        }
                        if (values == null) continue;
                        HologramLine line = HologramLine.fromMap(values, page, page.getNextLineLocation());
                        page.addLine(line);
                    }
                }
            }
            hologram.setFacing((float) config.getDouble("facing", 0.0f));
            return hologram;
        } catch (Exception e) {
            if (hologram != null) {
                hologram.destroy();
            }
            throw e;
        }
    }

    /*
     *	Fields
     */

    /**
     * The lock used to synchronize the saving process of this hologram.
     *
     * @implNote This lock is used to prevent multiple threads from saving
     * the same hologram at the same time. This is important because the
     * saving process is not thread-safe in SnakeYAML.
     * @since 2.7.10
     */
    protected final Lock lock = new ReentrantLock();

    /**
     * This object serves as a mutex for all visibility-related operations.
     * <p>
     * For example, when we want to hide a hologram, that's already being
     * updated on another thread, we would need to wait for the update to
     * finish before we can hide the hologram. That is because if we didn't,
     * parts of the hologram might still be visible after the hide operation,
     * due to the update process.
     *
     * @implNote This lock is used to prevent multiple threads from modifying
     * the visibility of the same hologram at the same time. This is important
     * because the visibility of a hologram is not thread-safe.
     * @since 2.7.11
     */
    protected final Object visibilityMutex = new Object();

    protected final String name;
    protected boolean saveToFile;
    protected final FileConfig config;
    protected final Map<UUID, Integer> viewerPages = new ConcurrentHashMap<>();
    protected final Set<UUID> hidePlayers = ConcurrentHashMap.newKeySet();
    protected final Set<UUID> showPlayers = ConcurrentHashMap.newKeySet();
    protected boolean defaultVisibleState = true;
    protected final List<HologramPage> pages = new ArrayList<>();
    protected boolean downOrigin = Settings.DEFAULT_DOWN_ORIGIN;
    protected boolean alwaysFacePlayer = false;
    private final AtomicInteger tickCounter;
    private final List<NmsClickableHologramRenderer> clickableHologramRenderers = new ArrayList<>();
    /**
     * Optional per-hologram TextDisplay renderer toggle.
     * If null, value falls back to global config.
     */
    private Boolean textDisplayEnabled = null;
    /**
     * Optional per-hologram text display scale override.
     * Values <= 0 mean "inherit global config scale".
     */
    private double textDisplayScale = -1.0d;
    /**
     * Per-hologram TextDisplay background mode override.
     */
    private boolean textDisplayBackgroundEnabled = false;
    /**
     * Optional per-hologram TextDisplay double-sided override.
     * If null, value falls back to fixed billboard behavior.
     */
    private Boolean textDisplayDoubleSided = null;
    /**
     * Per-hologram TextDisplay fixed billboard mode override.
     */
    private boolean textDisplayBillboardEnabled = false;
    /**
     * Yaw used in fixed TextDisplay billboard mode.
     */
    private float textDisplayBillboardYaw = 0.0f;
    /**
     * Pitch used in fixed TextDisplay billboard mode.
     */
    private float textDisplayBillboardPitch = 0.0f;

    /*
     *	Constructors
     */

    /**
     * Creates a new hologram with the given name and location. The hologram will be saved to a file.
     *
     * @param name     The name of the hologram.
     * @param location The location of the hologram.
     * @see DHAPI#createHologram(String, Location)
     */
    public Hologram(@NonNull String name, @NonNull Location location) {
        this(name, location, true);
    }

    /**
     * Creates a FileConfig if saveToFile is set to true
     *
     * @param saveToFile Whether the hologram should be saved to a file.
     * @param name       The name of the hologram.
     * @return
     */
    private static @Nullable FileConfig createConfig(final boolean saveToFile, final String name) {
        FileConfig conf = null;

        if (saveToFile)
            conf = new FileConfig(DECENT_HOLOGRAMS.getPlugin(), String.format("holograms/%s.yml", name));

        return conf;
    }

    /**
     * Creates a new hologram with the given name and location.
     *
     * @param name       The name of the hologram.
     * @param location   The location of the hologram.
     * @param saveToFile Whether the hologram should be saved to a file.
     * @see DHAPI#createHologram(String, Location, boolean)
     */
    public Hologram(@NonNull String name, @NonNull Location location, boolean saveToFile) {
        this(name, location, createConfig(saveToFile, name), true, saveToFile);
    }

    /**
     * Creates a new hologram with the given name and location. The hologram will be saved to the given file.
     *
     * @param name     The name of the hologram.
     * @param location The location of the hologram.
     * @param config   The config of the hologram.
     */
    public Hologram(@NonNull String name, @NonNull Location location, @NonNull FileConfig config) {
        this(name, location, config, true);
    }

    /**
     * Creates a new hologram with the given name and location.
     *
     * @param name     The name of the hologram.
     * @param location The location of the hologram.
     * @param config   The config of the hologram.
     * @param enabled  Whether the hologram should be enabled.
     */
    public Hologram(@NonNull String name, @NonNull Location location, @NonNull FileConfig config, boolean enabled) {
        this(name, location, config, enabled, true);
    }

    /**
     * Creates a new Hologram with the given parameters.
     *
     * @param name       The name of the hologram.
     * @param location   The location of the hologram.
     * @param config     The config of the hologram.
     * @param enabled    Whether the hologram should be enabled.
     * @param saveToFile Whether the hologram should be saved to a file.
     */
    public Hologram(@NonNull String name, @NonNull Location location, @Nullable FileConfig config, boolean enabled, boolean saveToFile) {
        super(location);
        this.config = config;
        this.enabled = enabled;
        this.name = name;
        this.saveToFile = saveToFile;
        this.tickCounter = new AtomicInteger();
        this.addPage();
        this.register();

        CACHED_HOLOGRAMS.put(this.name, this);
    }

    /*
     *	Tick
     */

    @Override
    public String getId() {
        return getName();
    }

    @Override
    public long getInterval() {
        return 1L;
    }

    @Override
    public void tick() {
        if (tickCounter.get() == getUpdateInterval()) {
            tickCounter.set(1);
            updateAll();
            return;
        }
        tickCounter.incrementAndGet();
        updateAnimationsAll();
    }

    /*
     *	General Methods
     */

    @Override
    public String toString() {
        return getClass().getName() + "{" +
                "name=" + getName() +
                ", enabled=" + isEnabled() +
                "} " + super.toString();
    }

    /**
     * This method calls {@link #destroy()} before deleting the hologram file.
     *
     * @see DHAPI#removeHologram(String)
     */
    @Override
    public void delete() {
        super.delete();

        if (config != null)
            config.delete();
    }

    /**
     * This method disables the hologram, removes it from the {@link HologramManager},
     * removes it from the cache and hides it from all players.
     */
    @Override
    public void destroy() {
        this.disable(DisableCause.API);
        this.viewerPages.clear();
        DECENT_HOLOGRAMS.getHologramManager().removeHologram(getName());
        CACHED_HOLOGRAMS.remove(getName());
    }

    /**
     * This method enables the hologram, calls the {@link #register()} method
     * to start the update task and shows it to all players.
     */
    @Override
    public void enable() {
        synchronized (visibilityMutex) {
            super.enable();
            this.showAll();
            this.register();
        }

        EventFactory.fireHologramEnableEvent(this);
    }

    /**
     * This method disables the hologram, calls the {@link #unregister()} method
     * to stop the update task and hides it from all players.
     */
    @Override
    public void disable(@NonNull DisableCause cause) {
        synchronized (visibilityMutex) {
            this.unregister();
            this.hideAll();
            super.disable(cause);
        }

        EventFactory.fireHologramDisableEvent(this);
    }

    @Override
    public void setFacing(float facing) {
        final float prev = this.facing;

        super.setFacing(facing);

        // Update the facing for all lines, that don't yet have a different facing set.
        // We want to keep the hologram facing working as a "default" value, but we don't want
        // it to override custom line facing.
        for (HologramPage page : this.pages) {
            page.getLines().forEach(line -> {
                if (line.getFacing() == prev) {
                    line.setFacing(facing);
                }
            });
            page.realignLines();
        }
    }

    /**
     * Set the location of this hologram. This method doesn't update the hologram's location
     * for the players, you have to call {@link #realignLines()} for that.
     *
     * @param location The new location of this hologram.
     * @see DHAPI#moveHologram(Hologram, Location)
     */
    @Override
    public void setLocation(@NonNull Location location) {
        super.setLocation(location);
        teleportClickableEntitiesAll();
    }

    /**
     * Get hologram size. (Number of pages)
     *
     * @return Number of pages in this hologram.
     */
    public int size() {
        return pages.size();
    }

    /**
     * Save this hologram to a file asynchronously.
     *
     * @implNote Always returns true. If the hologram is not persistent,
     * this method just doesn't do anything.
     */
    public void save() {
        if (!saveToFile) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                lock.tryLock(250, TimeUnit.MILLISECONDS);

                config.set("location", LocationUtils.asString(getLocation(), false));
                config.set("enabled", isEnabled());
                config.set("permission", permission == null || permission.isEmpty() ? null : permission);
                config.set("flags", flags.isEmpty() ? null : flags.stream().map(EnumFlag::name).collect(Collectors.toList()));
                config.set("display-range", displayRange);
                config.set("update-range", updateRange);
                config.set("update-interval", updateInterval);
                config.set("facing", facing);
                config.set("down-origin", downOrigin);
                config.set("text-display.enabled", textDisplayEnabled);
                config.set("text-display.scale", hasCustomTextDisplayScale() ? textDisplayScale : null);
                config.set("text-display.background", textDisplayBackgroundEnabled ? true : null);
                config.set("text-display.double-sided", textDisplayDoubleSided);
                config.set("text-display.billboard.enabled", textDisplayBillboardEnabled ? true : null);
                config.set("text-display.billboard.yaw", textDisplayBillboardYaw != 0.0f ? textDisplayBillboardYaw : null);
                config.set("text-display.billboard.pitch", textDisplayBillboardPitch != 0.0f ? textDisplayBillboardPitch : null);
                config.set("pages", pages.stream().map(HologramPage::serializeToMap).collect(Collectors.toList()));
                config.saveData();
            } catch (InterruptedException e) {
                // Failed to acquire lock, cancel save.
            } finally {
                // Prevents deadlocks
                lock.unlock();
            }
        });
    }

    /**
     * Create a new instance of this hologram object that's identical to this one.
     *
     * @param name     Name of the clone.
     * @param location Location of the clone.
     * @param temp     True if the clone should only exist until the next reload. (Won't save to file)
     * @return Cloned instance of this line.
     */
    public Hologram clone(@NonNull String name, @NonNull Location location, boolean temp) {
        Hologram hologram = new Hologram(name, location.clone(), !temp);
        hologram.setDownOrigin(this.isDownOrigin());
        hologram.setPermission(this.getPermission());
        hologram.setFacing(this.getFacing());
        hologram.setDisplayRange(this.getDisplayRange());
        hologram.setUpdateRange(this.getUpdateRange());
        hologram.setUpdateInterval(this.getUpdateInterval());
        hologram.setTextDisplayEnabled(this.getTextDisplayEnabled());
        hologram.setTextDisplayScale(this.getTextDisplayScale());
        hologram.setTextDisplayBackgroundEnabled(this.isTextDisplayBackgroundEnabled());
        hologram.setTextDisplayDoubleSided(this.getTextDisplayDoubleSided());
        hologram.setTextDisplayBillboardEnabled(this.isTextDisplayBillboardEnabled());
        hologram.setTextDisplayBillboardYaw(this.getTextDisplayBillboardYaw());
        hologram.setTextDisplayBillboardPitch(this.getTextDisplayBillboardPitch());
        hologram.addFlags(this.getFlags().toArray(new EnumFlag[0]));
        hologram.setDefaultVisibleState(this.isDefaultVisibleState());
        hologram.showPlayers.addAll(this.showPlayers);
        hologram.hidePlayers.addAll(this.hidePlayers);

        for (int i = 0; i < size(); i++) {
            HologramPage page = getPage(i);
            HologramPage clonePage = page.clone(hologram, i);
            if (hologram.pages.size() > i) {
                hologram.pages.set(i, clonePage);
            } else {
                hologram.pages.add(clonePage);
            }
        }
        return hologram;
    }

    /**
     * Handle a click on this hologram.
     *
     * @param player    The player that clicked the hologram.
     * @param entityId  The id of the clicked entity.
     * @param clickType The type of the click.
     * @return True if the click was handled, false otherwise.
     */
    public boolean onClick(@NonNull Player player, int entityId, @NonNull ClickType clickType) {
        HologramPage page = getPage(player);
        if (page == null || !page.hasEntity(entityId)) {
            return false;
        }

        boolean eventNotCancelled = EventFactory.fireHologramClickEvent(player, this, page, clickType, entityId);
        if (eventNotCancelled) {
            if (!hasFlag(EnumFlag.DISABLE_ACTIONS)) {
                page.executeActions(player, clickType);
            }
            return true;
        }

        return false;
    }

    /**
     * Handle the player quit event for this hologram. This method will hide the hologram
     * from the player and remove the player from the show/hide lists.
     *
     * @param player The player that quit.
     */
    public void onQuit(@NonNull Player player) {
        hide(player);
        removeShowPlayer(player);
        removeHidePlayer(player);
        viewerPages.remove(player.getUniqueId());
    }

    /*
     *	Visibility Methods
     */

    /**
     * @return Default display state
     */
    public boolean isVisibleState() {
        return defaultVisibleState;
    }

    /**
     * Set player hide state
     *
     * @param player player
     */
    public void setHidePlayer(@NonNull Player player) {
        UUID uniqueId = player.getUniqueId();
        if (!hidePlayers.contains(uniqueId)) {
            hidePlayers.add(player.getUniqueId());
        }
    }

    /**
     * Remove a player hide state
     *
     * @param player player
     */
    public void removeHidePlayer(@NonNull Player player) {
        UUID uniqueId = player.getUniqueId();
        hidePlayers.remove(uniqueId);
    }

    /**
     * Determine if the player can't see the hologram
     *
     * @param player player
     * @return state
     */
    public boolean isHideState(@NonNull Player player) {
        return hidePlayers.contains(player.getUniqueId());
    }

    /**
     * Set player show state
     *
     * @param player player
     */
    public void setShowPlayer(@NonNull Player player) {
        UUID uniqueId = player.getUniqueId();
        if (!showPlayers.contains(uniqueId)) {
            showPlayers.add(player.getUniqueId());
        }
    }

    /**
     * Remove a player show state
     *
     * @param player player
     */
    public void removeShowPlayer(@NonNull Player player) {
        UUID uniqueId = player.getUniqueId();
        showPlayers.remove(uniqueId);
    }

    /**
     * Determine if the player can see the hologram
     *
     * @param player player
     * @return state
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isShowState(@NonNull Player player) {
        return showPlayers.contains(player.getUniqueId());
    }

    /**
     * Show this hologram for given player on a given page.
     *
     * @param player    Given player.
     * @param pageIndex Given page.
     */
    public boolean show(@NonNull Player player, int pageIndex) {
        synchronized (visibilityMutex) {
            if (isDisabled() || isHideState(player) || (!isDefaultVisibleState() && !isShowState(player))) {
                return false;
            }
            HologramPage page = getPage(pageIndex);
            if (page != null && page.size() > 0 && canShow(player) && isInDisplayRange(player)) {
                // First hide the current page
                HologramPage currentPage = getPage(player);
                if (currentPage != null) {
                    hidePageFrom(player, currentPage);
                }

                if (Version.after(8)) {
                    showPageTo(player, page, pageIndex);
                } else {
                    // We need to run the task later on older versions as, if we don't, it causes issues with some holograms *randomly* becoming invisible.
                    // I *think* this is from despawning and spawning the entities (with the same ID) in the same tick.
                    S.sync(() -> showPageTo(player, page, pageIndex), 0L);
                }
                return true;
            }
            return false;
        }
    }

    private void showPageTo(@NonNull Player player, @NonNull HologramPage page, int pageIndex) {
        page.getLines().forEach(line -> line.show(player));
        // Add player to viewers
        viewerPages.put(player.getUniqueId(), pageIndex);
        viewers.add(player.getUniqueId());
        showClickableEntities(player);
    }

    public void showAll() {
        synchronized (visibilityMutex) {
            if (isEnabled()) {
                Bukkit.getOnlinePlayers().forEach(player -> show(player, getPlayerPage(player)));
            }
        }
    }

    public void update(@NonNull Player player) {
        update(false, player);
    }

    public void update(boolean force, @NonNull Player player) {
        synchronized (visibilityMutex) {
            if (hasFlag(EnumFlag.DISABLE_UPDATING)) {
                return;
            }

            performUpdate(force, player);
        }
    }

    public void updateAll() {
        updateAll(false);
    }

    /**
     * @param force If true, the line will be updated even if it does not need to be.
     * @see DHAPI#updateHologram(String)
     */
    public void updateAll(boolean force) {
        synchronized (visibilityMutex) {
            if (isEnabled() && !hasFlag(EnumFlag.DISABLE_UPDATING)) {
                getViewerPlayers().forEach(player -> performUpdate(force, player));
            }
        }
    }

    private void performUpdate(boolean force, @NotNull Player player) {
        if (!isVisible(player) || !isInUpdateRange(player) || isHideState(player)) {
            return;
        }

        HologramPage page = getPage(player);
        if (page != null) {
            page.getLines().forEach(line -> line.update(force, player));
        }
    }

    public void updateAnimations(@NonNull Player player) {
        synchronized (visibilityMutex) {
            if (hasFlag(EnumFlag.DISABLE_ANIMATIONS)) {
                return;
            }

            performUpdateAnimations(player);
        }
    }

    public void updateAnimationsAll() {
        synchronized (visibilityMutex) {
            if (isEnabled() && !hasFlag(EnumFlag.DISABLE_ANIMATIONS)) {
                getViewerPlayers().forEach(this::performUpdateAnimations);
            }
        }
    }

    private void performUpdateAnimations(@NotNull Player player) {
        if (!isVisible(player) || !isInUpdateRange(player) || isHideState(player)) {
            return;
        }

        HologramPage page = getPage(player);
        if (page != null) {
            page.getLines().forEach(line -> line.updateAnimations(player));
        }
    }

    public void hide(@NonNull Player player) {
        synchronized (visibilityMutex) {
            if (isVisible(player)) {
                HologramPage page = getPage(player);
                if (page != null) {
                    hidePageFrom(player, page);
                }
                viewers.remove(player.getUniqueId());
            }
        }
    }

    private void hidePageFrom(@NonNull Player player, @NonNull HologramPage page) {
        page.getLines().forEach(line -> line.hide(player));
        hideClickableEntities(player);
    }

    public void hideAll() {
        synchronized (visibilityMutex) {
            if (isEnabled()) {
                getViewerPlayers().forEach(this::hide);
            }
        }
    }

    public void showClickableEntities(@NonNull Player player) {
        HologramPage page = getPage(player);
        if (page == null || !(page.isClickable() || HologramClickEvent.isRegistered())) {
            return;
        }

        // Spawn clickable entities
        int amount = (int) (page.getHeight() / 2) + 1;
        Location location = getLocation().clone();
        location.setY((int) (location.getY() - (isDownOrigin() ? 0 : page.getHeight())) + 0.5);
        for (int i = 0; i < amount; i++) {
            NmsClickableHologramRenderer renderer = page.getClickableRenderer(i);
            renderer.display(player, DecentPosition.fromBukkitLocation(location));
            location.add(0, 1.8, 0);
        }
    }

    public void showClickableEntitiesAll() {
        if (isEnabled()) {
            getViewerPlayers().forEach(this::showClickableEntities);
        }
    }

    public void hideClickableEntities(@NonNull Player player) {
        HologramPage page = getPage(player);
        if (page == null) {
            return;
        }

        // De-spawn clickable entities
        page.getClickableEntityRenderers().forEach(renderer -> renderer.hide(player));
    }

    public void hideClickableEntitiesAll() {
        if (isEnabled()) {
            getViewerPlayers().forEach(this::hideClickableEntities);
        }
    }

    public void teleportClickableEntities(@NonNull Player player) {
        HologramPage page = getPage(player);
        if (page == null || !(page.isClickable() || HologramClickEvent.isRegistered())) {
            return;
        }

        // Spawn clickable entities
        int amount = (int) (page.getHeight() / 2) + 1;
        Location location = getLocation().clone();
        location.setY((int) (location.getY() - (isDownOrigin() ? 0 : page.getHeight())) + 0.5);
        for (int i = 0; i < amount; i++) {
            NmsClickableHologramRenderer renderer = page.getClickableRenderer(i);
            renderer.move(player, DecentPosition.fromBukkitLocation(location));
            location.add(0, 1.8, 0);
        }
    }

    public void teleportClickableEntitiesAll() {
        if (isEnabled()) {
            getViewerPlayers().forEach(this::teleportClickableEntities);
        }
    }


    /**
     * Check whether the given player is in the display range of this hologram object.
     *
     * @param player Given player.
     * @return Boolean whether the given player is in the display range of this hologram object.
     */
    public boolean isInDisplayRange(@NonNull Player player) {
        return isInRange(player, displayRange);
    }

    /**
     * Check whether the given player is in the update range of this hologram object.
     *
     * @param player Given player.
     * @return Boolean whether the given player is in the update range of this hologram object.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isInUpdateRange(@NonNull Player player) {
        return isInRange(player, updateRange);
    }

    private boolean isInRange(@NonNull Player player, double range) {
        /*
         * Some forks (e.g., Pufferfish) throw an exception, when we try to get
         * the world of a location, which is not loaded.
         * We catch this exception and return false, because the player is not in range.
         */
        try {
            if (player.getWorld().equals(location.getWorld())) {
                return player.getLocation().distanceSquared(location) <= range * range;
            }
        } catch (Exception ignored) {
            // Ignored
        }
        return false;
    }

    public void setDownOrigin(boolean downOrigin) {
        this.downOrigin = downOrigin;
        this.hideClickableEntitiesAll();
        this.showClickableEntitiesAll();
    }

    /**
     * Check whether this hologram should use TextDisplay rendering for text lines.
     *
     * <p>If this hologram does not define a custom value, global setting is used.</p>
     *
     * @return True if TextDisplay rendering is enabled for this hologram.
     */
    public boolean isEffectiveTextDisplayEnabled() {
        if (textDisplayEnabled != null) {
            return textDisplayEnabled;
        }
        return Settings.TEXT_DISPLAY_ENABLED;
    }

    /**
     * Check whether this hologram can use the TextDisplay renderer.
     *
     * @return True if TextDisplay is enabled and supported for this hologram.
     */
    public boolean shouldUseTextDisplayRenderer() {
        return isEffectiveTextDisplayEnabled() && Settings.isTextDisplaySupported();
    }

    /**
     * Set optional per-hologram TextDisplay scale override.
     *
     * <p>Values <= 0 disable override and inherit {@link Settings#TEXT_DISPLAY_SCALE}.</p>
     *
     * @param textDisplayScale Scale override.
     */
    public void setTextDisplayScale(double textDisplayScale) {
        if (textDisplayScale <= 0.0d) {
            this.textDisplayScale = -1.0d;
            return;
        }
        this.textDisplayScale = Math.min(Math.max(textDisplayScale, 0.1d), 10.0d);
    }

    /**
     * Check if this hologram has custom TextDisplay scale.
     *
     * @return True if custom scale override is configured.
     */
    public boolean hasCustomTextDisplayScale() {
        return textDisplayScale > 0.0d;
    }

    /**
     * Get effective TextDisplay scale for this hologram.
     *
     * @return Custom scale if set, otherwise global settings scale.
     */
    public double getEffectiveTextDisplayScale() {
        if (hasCustomTextDisplayScale()) {
            return textDisplayScale;
        }
        return Settings.TEXT_DISPLAY_SCALE;
    }

    /**
     * Enable or disable per-hologram fixed billboard mode for TextDisplay text lines.
     *
     * @param enabled True to enable, false to disable.
     */
    public void setTextDisplayBillboardEnabled(boolean enabled) {
        this.textDisplayBillboardEnabled = enabled;
    }

    /**
     * Set per-hologram fixed billboard yaw override for TextDisplay text lines.
     *
     * @param yaw Yaw in range -180..180.
     */
    public void setTextDisplayBillboardYaw(float yaw) {
        this.textDisplayBillboardYaw = Math.max(-180.0f, Math.min(180.0f, yaw));
    }

    /**
     * Set per-hologram fixed billboard pitch override for TextDisplay text lines.
     *
     * @param pitch Pitch in range -90..90.
     */
    public void setTextDisplayBillboardPitch(float pitch) {
        this.textDisplayBillboardPitch = Math.max(-90.0f, Math.min(90.0f, pitch));
    }

    /**
     * Check whether fixed billboard mode should be enabled for this hologram's text displays.
     *
     * @return True if fixed billboard mode should be enabled.
     */
    public boolean isEffectiveTextDisplayBillboardEnabled() {
        return Settings.TEXT_DISPLAY_BILLBOARD_ENABLED && textDisplayBillboardEnabled;
    }

    /**
     * Check whether this hologram should render TextDisplay lines as double-sided.
     *
     * <p>This setting only applies when fixed billboard mode is enabled for this hologram.</p>
     * <p>When billboard mode is enabled, this falls back to global billboard double-sided setting
     * unless explicitly overridden for this hologram.</p>
     *
     * @return True if TextDisplay lines should be double-sided.
     */
    public boolean isEffectiveTextDisplayDoubleSided() {
        if (!isEffectiveTextDisplayBillboardEnabled()) {
            return false;
        }
        if (textDisplayDoubleSided != null) {
            return textDisplayDoubleSided;
        }
        return Settings.TEXT_DISPLAY_BILLBOARD_DOUBLE_SIDED;
    }

    /*
     *	Viewer Methods
     */

    public int getPlayerPage(@NonNull Player player) {
        return viewerPages.getOrDefault(player.getUniqueId(), 0);
    }

    public Set<Player> getViewerPlayers(int pageIndex) {
        Set<Player> players = new HashSet<>();
        viewerPages.forEach((uuid, integer) -> {
            if (integer == pageIndex) {
                players.add(Bukkit.getPlayer(uuid));
            }
        });
        return players;
    }

    /*
     *	Pages Methods
     */

    /**
     * Re-Align the lines in this hologram, putting them to the right place.
     * <p>
     * This method is good to use after teleporting the hologram.
     * </p>
     */
    public void realignLines() {
        for (HologramPage page : pages) {
            page.realignLines();
        }
    }

    /**
     * @see DHAPI#addHologramPage(Hologram)
     */
    public HologramPage addPage() {
        HologramPage page = new HologramPage(this, pages.size());
        pages.add(page);
        return page;
    }

    /**
     * @see DHAPI#insertHologramPage(Hologram, int)
     */
    public HologramPage insertPage(int index) {
        if (index < 0 || index > size()) return null;
        HologramPage page = new HologramPage(this, index);
        pages.add(index, page);

        // Add 1 to indexes of all the other pages.
        pages.stream().skip(index).forEach(p -> p.setIndex(p.getIndex() + 1));
        // Add 1 to all page indexes of current viewers, so they still see the same page.
        viewerPages.replaceAll((uuid, integer) -> {
            if (integer > index) {
                return integer + 1;
            }
            return integer;
        });
        return page;
    }

    /**
     * @see DHAPI#getHologramPage(Hologram, int)
     */
    public HologramPage getPage(int index) {
        if (index < 0 || index >= size()) return null;
        return pages.get(index);
    }

    public HologramPage getPage(@NonNull Player player) {
        if (isVisible(player)) {
            return getPage(getPlayerPage(player));
        }
        return null;
    }

    /**
     * @see DHAPI#removeHologramPage(Hologram, int)
     */
    public HologramPage removePage(int index) {
        if (index < 0 || index >= size()) {
            return null;
        }

        HologramPage page = pages.remove(index);
        page.getLines().forEach(HologramLine::hide);

        // Update indexes of all the other pages.
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setIndex(i);
        }

        // Update all page indexes of current viewers, so they still see the same page.
        if (!pages.isEmpty()) {
            for (Map.Entry<UUID, Integer> entry : viewerPages.entrySet()) {
                UUID uuid = entry.getKey();
                int currentPage = viewerPages.get(uuid);
                if (currentPage == index) {
                    show(Bukkit.getPlayer(uuid), 0);
                } else if (currentPage > index) {
                    viewerPages.put(uuid, currentPage - 1);
                }
            }
        }
        return page;
    }

    public boolean swapPages(int index1, int index2) {
        if (index1 == index2 || index1 < 0 || index1 >= size() || index2 < 0 || index2 >= size()) {
            return false;
        }
        // Swap them in the list
        Collections.swap(pages, index1, index2);

        // Swap indexes of affected pages
        HologramPage page1 = getPage(index1);
        HologramPage page2 = getPage(index2);
        int i = page1.getIndex();
        page1.setIndex(page2.getIndex());
        page2.setIndex(i);

        // Swap viewers
        Set<Player> viewers1 = getViewerPlayers(index1);
        Set<Player> viewers2 = getViewerPlayers(index2);
        viewers1.forEach(player -> show(player, index2));
        viewers2.forEach(player -> show(player, index1));
        return true;
    }

}
