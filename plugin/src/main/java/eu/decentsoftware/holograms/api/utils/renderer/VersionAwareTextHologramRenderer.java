package eu.decentsoftware.holograms.api.utils.renderer;

import eu.decentsoftware.holograms.api.Settings;
import eu.decentsoftware.holograms.api.utils.Log;
import eu.decentsoftware.holograms.nms.api.NmsHologramPartData;
import eu.decentsoftware.holograms.nms.api.renderer.NmsTextHologramRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Delegating text renderer that picks a modern TextDisplay renderer where available,
 * while keeping the legacy renderer as a fallback.
 */
public class VersionAwareTextHologramRenderer implements NmsTextHologramRenderer {

    private final NmsTextHologramRenderer delegate;

    public VersionAwareTextHologramRenderer(NmsTextHologramRenderer legacyRenderer,
                                            JavaPlugin plugin,
                                            boolean useTextDisplayRenderer,
                                            double scale,
                                            boolean backgroundEnabled,
                                            boolean doubleSided,
                                            boolean fixedBillboardMode,
                                            float fixedBillboardYaw,
                                            float fixedBillboardPitch) {
        NmsTextHologramRenderer selectedRenderer = legacyRenderer;

        // Version split:
        // Use TextDisplay only when enabled for this line/hologram and supported by the current server runtime.
        if (useTextDisplayRenderer && Settings.isTextDisplaySupported()) {
            try {
                selectedRenderer = new TextDisplayHologramRenderer(
                        plugin,
                        scale,
                        backgroundEnabled,
                        doubleSided,
                        fixedBillboardMode,
                        fixedBillboardYaw,
                        fixedBillboardPitch);
            } catch (Exception e) {
                Log.warn("Failed to initialize TextDisplay renderer. Falling back to legacy text renderer.", e);
            }
        }
        this.delegate = selectedRenderer;
    }

    @Override
    public void display(Player player, NmsHologramPartData<String> data) {
        delegate.display(player, data);
    }

    @Override
    public void updateContent(Player player, NmsHologramPartData<String> data) {
        delegate.updateContent(player, data);
    }

    @Override
    public void move(Player player, NmsHologramPartData<String> data) {
        delegate.move(player, data);
    }

    @Override
    public void hide(Player player) {
        delegate.hide(player);
    }

    @Override
    public double getHeight(NmsHologramPartData<String> data) {
        return delegate.getHeight(data);
    }

    @Override
    public int[] getEntityIds() {
        return delegate.getEntityIds();
    }
}
