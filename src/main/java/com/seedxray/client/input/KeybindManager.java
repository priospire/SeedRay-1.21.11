package com.seedxray.client.input;

import com.seedxray.client.XrayClientMod;
import com.seedxray.client.XrayConstants;
import com.seedxray.client.config.DisplayMode;
import com.seedxray.client.config.XrayConfig;
import com.seedxray.client.diagnostic.DiagnosticCache;
import com.seedxray.client.diagnostic.DiagnosticScheduler;
import com.seedxray.client.prediction.OreTarget;
import com.seedxray.client.prediction.PredictionCache;
import com.seedxray.client.prediction.PredictionScheduler;
import com.seedxray.client.screen.XrayConfigScreen;
import com.seedxray.client.render.TerrainTransparencyHooks;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class KeybindManager {
    private static final Object CATEGORY = createCategory();
    private final PredictionCache predictionCache;
    private final DiagnosticCache diagnosticCache;
    private final PredictionScheduler predictionScheduler;
    private final DiagnosticScheduler diagnosticScheduler;

    private KeyBinding toggleXray;
    private KeyBinding cycleMode;
    private KeyBinding cycleOreGroup;
    private KeyBinding toggleLabels;
    private KeyBinding toggleHud;
    private KeyBinding increaseRadius;
    private KeyBinding decreaseRadius;
    private KeyBinding clearCache;
    private KeyBinding toggleDimming;
    private KeyBinding openSettings;
    private FilterPreset filterPreset = FilterPreset.ANCIENT_DEBRIS;

    public KeybindManager(
            PredictionCache predictionCache,
            DiagnosticCache diagnosticCache,
            PredictionScheduler predictionScheduler,
            DiagnosticScheduler diagnosticScheduler
    ) {
        this.predictionCache = predictionCache;
        this.diagnosticCache = diagnosticCache;
        this.predictionScheduler = predictionScheduler;
        this.diagnosticScheduler = diagnosticScheduler;
    }

    public void register() {
        toggleXray = register("key.seed-xray.toggle_xray", GLFW.GLFW_KEY_F7);
        cycleMode = register("key.seed-xray.cycle_mode", GLFW.GLFW_KEY_B);
        cycleOreGroup = register("key.seed-xray.cycle_ore_group", GLFW.GLFW_KEY_N);
        toggleLabels = register("key.seed-xray.toggle_labels", GLFW.GLFW_KEY_L);
        toggleHud = register("key.seed-xray.toggle_diagnostic_hud", GLFW.GLFW_KEY_H);
        increaseRadius = register("key.seed-xray.increase_radius", GLFW.GLFW_KEY_RIGHT_BRACKET);
        decreaseRadius = register("key.seed-xray.decrease_radius", GLFW.GLFW_KEY_LEFT_BRACKET);
        clearCache = register("key.seed-xray.clear_cache", GLFW.GLFW_KEY_K);
        toggleDimming = register("key.seed-xray.toggle_dimming", GLFW.GLFW_KEY_J);
        openSettings = register("key.seed-xray.open_settings", GLFW.GLFW_KEY_F8);
    }

    public void tick(MinecraftClient client) {
        while (openSettings.wasPressed()) {
            client.setScreen(new XrayConfigScreen(this));
        }
        while (toggleXray.wasPressed()) {
            XrayConfig config = XrayClientMod.CONFIG.get();
            config.enabled = !config.enabled;
            XrayClientMod.CONFIG.save();
            TerrainTransparencyHooks.scheduleTerrainRefresh(client);
            if (config.enabled && !config.seedConfigured) {
                feedback(client, "Seed X-Ray ON - save the target world seed in F8");
            } else {
                feedback(client, "Seed X-Ray " + (config.enabled ? "ON" : "OFF"));
            }
        }
        while (cycleMode.wasPressed()) {
            XrayConfig config = XrayClientMod.CONFIG.get();
            config.displayMode = config.displayMode.next();
            XrayClientMod.CONFIG.save();
            feedback(client, "X-Ray mode: " + readableMode(config.displayMode));
        }
        while (cycleOreGroup.wasPressed()) {
            filterPreset = filterPreset.next();
            filterPreset.apply(XrayClientMod.CONFIG.get());
            clearPredictionState();
            XrayClientMod.CONFIG.save();
            feedback(client, "Ore filter: " + filterPreset.displayName);
        }
        while (toggleLabels.wasPressed()) {
            XrayConfig config = XrayClientMod.CONFIG.get();
            config.showLabels = !config.showLabels;
            XrayClientMod.CONFIG.save();
            feedback(client, "X-Ray labels " + (config.showLabels ? "ON" : "OFF"));
        }
        while (toggleHud.wasPressed()) {
            XrayConfig config = XrayClientMod.CONFIG.get();
            config.showHud = !config.showHud;
            XrayClientMod.CONFIG.save();
            feedback(client, "X-Ray HUD " + (config.showHud ? "ON" : "OFF"));
        }
        while (increaseRadius.wasPressed()) {
            XrayConfig config = XrayClientMod.CONFIG.get();
            config.predictionRadiusChunks++;
            config.applyDefaults();
            predictionScheduler.clearQueue();
            XrayClientMod.CONFIG.save();
            feedback(client, "Prediction radius: " + config.predictionRadiusChunks + " chunks");
        }
        while (decreaseRadius.wasPressed()) {
            XrayConfig config = XrayClientMod.CONFIG.get();
            config.predictionRadiusChunks--;
            config.applyDefaults();
            predictionScheduler.clearQueue();
            XrayClientMod.CONFIG.save();
            feedback(client, "Prediction radius: " + config.predictionRadiusChunks + " chunks");
        }
        while (clearCache.wasPressed()) {
            clearPredictionState();
            feedback(client, "Seed X-Ray cache cleared; regenerating nearby chunks");
        }
        while (toggleDimming.wasPressed()) {
            XrayConfig config = XrayClientMod.CONFIG.get();
            config.useTerrainTransparency = !config.useTerrainTransparency;
            config.useFallbackDimmingOverlay = !config.useTerrainTransparency;
            XrayClientMod.CONFIG.save();
            TerrainTransparencyHooks.scheduleTerrainRefresh(client);
            feedback(client, config.useTerrainTransparency ? "Terrain transparency ON" : "Fallback dimming ON");
        }
    }

    public List<ManagedBinding> managedBindings() {
        return List.of(
                new ManagedBinding("Toggle X-Ray", toggleXray),
                new ManagedBinding("Open Settings", openSettings),
                new ManagedBinding("Cycle Mode", cycleMode),
                new ManagedBinding("Cycle Ore Filter", cycleOreGroup),
                new ManagedBinding("Toggle Labels", toggleLabels),
                new ManagedBinding("Toggle HUD", toggleHud),
                new ManagedBinding("Increase Radius", increaseRadius),
                new ManagedBinding("Decrease Radius", decreaseRadius),
                new ManagedBinding("Clear Cache", clearCache),
                new ManagedBinding("Toggle Transparency", toggleDimming)
        );
    }

    public void clearPredictionState() {
        XrayClientMod.clearPredictionState();
    }

    private static KeyBinding register(String translationKey, int key) {
        return KeyBindingHelper.registerKeyBinding(createKeyBinding(translationKey, key));
    }

    private static Object createCategory() {
        try {
            Class<?> categoryClass = Class.forName("net.minecraft.client.option.KeyBinding$Category");
            Method create = categoryClass.getMethod("create", Identifier.class);
            return create.invoke(null, Identifier.of(XrayConstants.MOD_ID, "controls"));
        } catch (ClassNotFoundException ignored) {
            return "key.categories." + XrayConstants.MOD_ID + ".controls";
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create Seed X-Ray keybinding category", exception);
        }
    }

    private static KeyBinding createKeyBinding(String translationKey, int key) {
        try {
            Constructor<KeyBinding> constructor = KeyBinding.class.getConstructor(
                    String.class,
                    InputUtil.Type.class,
                    int.class,
                    CATEGORY.getClass()
            );
            return constructor.newInstance(translationKey, InputUtil.Type.KEYSYM, key, CATEGORY);
        } catch (NoSuchMethodException exception) {
            if (CATEGORY instanceof String category) {
                return createLegacyKeyBinding(translationKey, key, category, exception);
            }
            throw new IllegalStateException("Unsupported Seed X-Ray keybinding constructor", exception);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to create Seed X-Ray keybinding", exception);
        }
    }

    private static KeyBinding createLegacyKeyBinding(String translationKey, int key, String category, NoSuchMethodException firstFailure) {
        try {
            Constructor<KeyBinding> constructor = KeyBinding.class.getConstructor(
                    String.class,
                    InputUtil.Type.class,
                    int.class,
                    String.class
            );
            return constructor.newInstance(translationKey, InputUtil.Type.KEYSYM, key, category);
        } catch (ReflectiveOperationException exception) {
            exception.addSuppressed(firstFailure);
            throw new IllegalStateException("Unsupported Seed X-Ray keybinding constructor", exception);
        }
    }

    private static String readableMode(DisplayMode mode) {
        return switch (mode) {
            case PREDICTION_ONLY -> "Prediction";
            case DIAGNOSTIC_ONLY -> "Diagnostic";
            case COMBINED -> "Combined";
        };
    }

    private static void feedback(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), true);
        }
    }

    private enum FilterPreset {
        ANCIENT_DEBRIS("Ancient Debris"),
        NETHER("Nether Ores"),
        OVERWORLD("Overworld Ores"),
        ALL("All Ores");

        private final String displayName;

        FilterPreset(String displayName) {
            this.displayName = displayName;
        }

        FilterPreset next() {
            FilterPreset[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        void apply(XrayConfig config) {
            for (OreTarget target : OreTarget.values()) {
                boolean enabled = switch (this) {
                    case ANCIENT_DEBRIS -> target == OreTarget.ANCIENT_DEBRIS;
                    case NETHER -> target.dimensionGroup() == OreTarget.DimensionGroup.NETHER;
                    case OVERWORLD -> target.dimensionGroup() == OreTarget.DimensionGroup.OVERWORLD;
                    case ALL -> true;
                };
                config.oreFilters.put(target.name(), enabled);
            }
        }
    }

    public record ManagedBinding(String label, KeyBinding keyBinding) {
    }
}
