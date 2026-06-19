package com.seedxray.client.screen;

import com.seedxray.client.XrayClientMod;
import com.seedxray.client.config.XrayConfig;
import com.seedxray.client.input.KeybindManager;
import com.seedxray.client.prediction.OreTarget;
import com.seedxray.client.render.TerrainTransparencyHooks;
import com.seedxray.client.util.DebugExportManager;
import com.seedxray.client.util.KeyNameResolver;
import com.seedxray.client.util.LocalText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

abstract class XrayConfigScreenBase extends Screen {
    private static final int TAB_TOP = 24;
    private static final int CONTENT_TOP = 58;
    private static final int FOOTER_HEIGHT = 42;
    private static final int ROW_HEIGHT = 22;
    private static final int STATUS_HOLD_MS = 1400;
    private static final int STATUS_FADE_MS = 1600;
    private static final int[] COLOR_PRESETS = {
            0xFFFF6A3D, 0xFF20E7FF, 0xFF21E86D, 0xFFFFD447, 0xFFFF3030, 0xFF2E5DFF,
            0xFFF6F2E8, 0xFFE6B17A, 0xFFFF8A3D, 0xFFFFFFFF
    };
    private static final int[] ALPHA_PRESETS = {90, 140, 190, 230, 255};

    private final KeybindManager keybindManager;
    private final Map<KeyBinding, ButtonWidget> keyButtons = new HashMap<>();
    private final List<KeyRow> keyRows = new ArrayList<>();

    private Tab tab = Tab.ORES;
    private TextFieldWidget seedField;
    private TextFieldWidget transparencyField;
    private TextFieldWidget maxRenderField;
    private TextFieldWidget distanceField;
    private KeyBinding listeningKey;
    private String status = "";
    private int statusColor = 0xFFB6F6C5;
    private long statusSetTimeMs = 0L;
    private int scrollY = 0;
    private int contentHeight = 0;

    protected XrayConfigScreenBase(KeybindManager keybindManager) {
        super(LocalText.text("Seed X-Ray Quick Panel"));
        this.keybindManager = keybindManager;
    }

    @Override
    protected void init() {
        keyButtons.clear();
        keyRows.clear();
        int panelWidth = panelWidth();
        int panelX = (width - panelWidth) / 2;
        updateContentHeight();
        scrollY = clamp(scrollY, 0, maxScroll());
        int y = CONTENT_TOP;
        int tabGap = 6;
        int tabWidth = Math.max(64, (panelWidth - 28 - tabGap * (Tab.values().length - 1)) / Tab.values().length);
        int tabX = panelX + 14;
        for (Tab value : Tab.values()) {
            addDrawableChild(ButtonWidget.builder(LocalText.text((tab == value ? "> " : "") + value.label), button -> {
                        tab = value;
                        scrollY = 0;
                        clearAndInit();
                    })
                    .dimensions(tabX, TAB_TOP, tabWidth, 20)
                    .build());
            tabX += tabWidth + tabGap;
        }

        switch (tab) {
            case ORES -> initOres(panelX, y, panelWidth);
            case VISUAL -> initVisual(panelX, y, panelWidth);
            case SEED -> initSeed(panelX, y, panelWidth);
            case KEYS -> initKeys(panelX, y, panelWidth);
        }

        addDrawableChild(ButtonWidget.builder(LocalText.text("Done"), button -> close())
                .dimensions(panelX + panelWidth - 92, height - 30, 78, 20)
                .build());
    }

    private void initOres(int panelX, int y, int panelWidth) {
        XrayConfig config = XrayClientMod.CONFIG.get();
        int labelX = panelX + 18;
        int enabledX = panelX + 162;
        int highlightX = panelX + 218;
        int textureX = panelX + 274;
        int colorX = panelX + 330;
        int alphaX = panelX + 414;
        for (OreTarget target : OreTarget.values()) {
            int rowY = screenY(y);
            addIfVisible(y, 20, ButtonWidget.builder(LocalText.text(onOff("Ore", config.isOreEnabled(target))), button -> {
                        config.oreFilters.put(target.name(), !config.isOreEnabled(target));
                        saveAndRegenerate();
                        button.setMessage(LocalText.text(onOff("Ore", config.isOreEnabled(target))));
                    })
                    .dimensions(enabledX, rowY, 50, 20)
                    .build());
            addIfVisible(y, 20, ButtonWidget.builder(LocalText.text(onOff("Hi", config.isOreHighlightEnabled(target))), button -> {
                        boolean value = !config.isOreHighlightEnabled(target);
                        config.oreHighlightEnabled.put(target.name(), value);
                        saveConfigOnly();
                        button.setMessage(LocalText.text(onOff("Hi", value)));
                    })
                    .dimensions(highlightX, rowY, 50, 20)
                    .build());
            addIfVisible(y, 20, ButtonWidget.builder(LocalText.text(onOff("Tex", config.isOreTextureEnabled(target))), button -> {
                        boolean value = !config.isOreTextureEnabled(target);
                        config.oreTextureEnabled.put(target.name(), value);
                        saveConfigOnly();
                        button.setMessage(LocalText.text(onOff("Tex", value)));
                    })
                    .dimensions(textureX, rowY, 50, 20)
                    .build());
            addIfVisible(y, 20, ButtonWidget.builder(LocalText.text(colorLabel(config.colorFor(target))), button -> {
                        int next = nextColor(config.colorFor(target));
                        config.oreColors.put(target.name(), next);
                        saveConfigOnly();
                        button.setMessage(LocalText.text(colorLabel(next)));
                    })
                    .dimensions(colorX, rowY, 78, 20)
                    .build());
            addIfVisible(y, 20, ButtonWidget.builder(LocalText.text("A " + config.alphaFor(target)), button -> {
                        int next = nextAlpha(config.alphaFor(target));
                        config.oreAlphas.put(target.name(), next);
                        saveConfigOnly();
                        button.setMessage(LocalText.text("A " + next));
                    })
                    .dimensions(alphaX, rowY, 58, 20)
                    .build());
            y += ROW_HEIGHT;
        }
        addDrawableChild(ButtonWidget.builder(LocalText.text("Enable All"), button -> {
                    for (OreTarget target : OreTarget.values()) {
                        config.oreFilters.put(target.name(), true);
                    }
                    saveAndRegenerate();
                    clearAndInit();
                })
                .dimensions(panelX + 106, height - 30, 84, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(LocalText.text("Ancient Only"), button -> {
                    for (OreTarget target : OreTarget.values()) {
                        config.oreFilters.put(target.name(), target == OreTarget.ANCIENT_DEBRIS);
                    }
                    saveAndRegenerate();
                    clearAndInit();
                })
                .dimensions(panelX + 14, height - 30, 88, 20)
                .build());
    }

    private void initVisual(int panelX, int y, int panelWidth) {
        XrayConfig config = XrayClientMod.CONFIG.get();
        int leftX = panelX + 18;
        int fieldX = leftX + 122;
        int width = 190;
        transparencyField = field(fieldX, y, 64, Integer.toString(config.transparencyPercent), XrayConfigScreenBase::isPercentText);
        addIfVisible(y, 20, ButtonWidget.builder(LocalText.text("Apply Transparency"), button -> applyTransparency())
                .dimensions(fieldX + 72, screenY(y), 136, 20)
                .build());
        y += 28;

        maxRenderField = field(fieldX, y, 84, Integer.toString(config.maxRenderedHighlights), XrayConfigScreenBase::isPositiveNumberText);
        addIfVisible(y, 20, ButtonWidget.builder(LocalText.text("Apply Render Limit"), button -> applyRenderLimits())
                .dimensions(fieldX + 92, screenY(y), 136, 20)
                .build());
        y += 28;

        distanceField = field(fieldX, y, 84, Integer.toString(config.distanceLimitBlocks), XrayConfigScreenBase::isPositiveNumberText);
        addIfVisible(y, 20, ButtonWidget.builder(LocalText.text("Apply Distance"), button -> applyRenderLimits())
                .dimensions(fieldX + 92, screenY(y), 136, 20)
                .build());
        y += 30;

        addToggle(leftX, y, width, "X-Ray", () -> config.enabled, value -> {
            config.enabled = value;
            saveAndRefreshTerrain();
        });
        y += 24;
        addToggle(leftX, y, width, "Terrain Transparency", () -> config.useTerrainTransparency, value -> {
            config.useTerrainTransparency = value;
            config.useFallbackDimmingOverlay = !value;
            saveAndRefreshTerrain();
        });
        y += 24;
        addToggle(leftX, y, width, "Global Highlights", () -> config.showHighlights, value -> {
            config.showHighlights = value;
            saveConfigOnly();
        });
        y += 24;
        addToggle(leftX, y, width, "Global Textures", () -> config.showOreTextures, value -> {
            config.showOreTextures = value;
            saveConfigOnly();
        });
        y += 24;
        addToggle(leftX, y, width, "Filled Boxes", () -> config.showFilledBoxes, value -> {
            config.showFilledBoxes = value;
            saveConfigOnly();
        });
        y += 24;
        addToggle(leftX, y, width, "Outlines", () -> config.showOutlines, value -> {
            config.showOutlines = value;
            saveConfigOnly();
        });
        y += 24;
        addToggle(leftX, y, width, "Labels", () -> config.showLabels, value -> {
            config.showLabels = value;
            saveConfigOnly();
        });
        y += 24;
        addToggle(leftX, y, width, "Show Obstructed", () -> config.showClientObstructedPredictions, value -> {
            config.showClientObstructedPredictions = value;
            saveConfigOnly();
        });
    }

    private void initSeed(int panelX, int y, int panelWidth) {
        XrayConfig config = XrayClientMod.CONFIG.get();
        int leftX = panelX + 18;
        y += 18;
        seedField = field(leftX, y, panelWidth - 36, Long.toString(config.worldSeed), XrayConfigScreenBase::isSeedText);
        y += 28;
        addIfVisible(y, 20, ButtonWidget.builder(LocalText.text("Save Seed"), button -> applySeed())
                .dimensions(leftX, screenY(y), 108, 20)
                .build());
        addIfVisible(y, 20, ButtonWidget.builder(LocalText.text("Clear / Regenerate"), button -> {
                    keybindManager.clearPredictionState();
                    setStatus("Prediction cache cleared", 0xFFB6F6C5);
                })
                .dimensions(leftX + 114, screenY(y), 140, 20)
                .build());
        addIfVisible(y, 20, ButtonWidget.builder(LocalText.text("Export Debug CSV"), button -> {
                    try {
                        setStatus("Exported " + DebugExportManager.exportCurrentDimension().getFileName(), 0xFFB6F6C5);
                    } catch (Exception exception) {
                        setStatus("Export failed: " + exception.getMessage(), 0xFFFF8585);
                    }
                })
                .dimensions(leftX + 260, screenY(y), 132, 20)
                .build());
    }

    private void initKeys(int panelX, int y, int panelWidth) {
        int labelX = panelX + 18;
        int buttonX = panelX + panelWidth - 142;
        for (KeybindManager.ManagedBinding binding : keybindManager.managedBindings()) {
            int rowY = screenY(y);
            ButtonWidget button = ButtonWidget.builder(keyButtonText(binding.keyBinding()), clicked -> {
                        listeningKey = binding.keyBinding();
                        refreshKeyButtons();
                    })
                    .dimensions(buttonX, rowY, 124, 20)
                    .build();
            if (isContentVisible(y, 20)) {
                addDrawableChild(button);
                keyButtons.put(binding.keyBinding(), button);
                keyRows.add(new KeyRow(binding.label(), labelX, rowY + 6));
            }
            y += ROW_HEIGHT;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelWidth = panelWidth();
        int panelX = (width - panelWidth) / 2;
        context.fill(0, 0, width, height, 0x66000000);
        context.fill(panelX, 12, panelX + panelWidth, height - 8, 0xDD101215);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
        renderTabLabels(context, panelX);
        super.render(context, mouseX, mouseY, delta);
        renderScrollHint(context, panelX, panelWidth);
        renderStatus(context, panelX);
    }

    private void renderTabLabels(DrawContext context, int panelX) {
        if (tab == Tab.ORES) {
            int y = CONTENT_TOP + 6;
            for (OreTarget target : OreTarget.values()) {
                int rowY = screenY(y);
                if (isContentVisible(y, 20)) {
                    context.drawTextWithShadow(textRenderer, target.displayName(), panelX + 18, rowY + 6, 0xFFE0E0E0);
                }
                y += ROW_HEIGHT;
            }
            int headerY = CONTENT_TOP - 14;
            context.drawTextWithShadow(textRenderer, "Enabled", panelX + 164, headerY, 0xFFAAAAAA);
            context.drawTextWithShadow(textRenderer, "Highlight", panelX + 218, headerY, 0xFFAAAAAA);
            context.drawTextWithShadow(textRenderer, "Texture", panelX + 276, headerY, 0xFFAAAAAA);
            context.drawTextWithShadow(textRenderer, "Color", panelX + 340, headerY, 0xFFAAAAAA);
            context.drawTextWithShadow(textRenderer, "Alpha", panelX + 420, headerY, 0xFFAAAAAA);
        } else if (tab == Tab.VISUAL) {
            drawVisibleLabel(context, panelX + 18, CONTENT_TOP + 6, "Transparency %", 0xFFE0E0E0);
            drawVisibleLabel(context, panelX + 18, CONTENT_TOP + 34, "Max Rendered", 0xFFE0E0E0);
            drawVisibleLabel(context, panelX + 18, CONTENT_TOP + 62, "Distance Limit", 0xFFE0E0E0);
        } else if (tab == Tab.SEED) {
            drawVisibleLabel(context, panelX + 18, CONTENT_TOP + 6, "World Seed", 0xFFE0E0E0);
            drawVisibleLabel(context, panelX + 18, CONTENT_TOP + 62, "Saved seed persists until you replace it.", 0xFFAAAAAA);
            if (!XrayClientMod.CONFIG.get().seedConfigured) {
                drawVisibleLabel(context, panelX + 18, CONTENT_TOP + 76, "No seed has been saved yet; predictions will not match your target world.", 0xFFFFC266);
            }
        } else if (tab == Tab.KEYS) {
            context.drawTextWithShadow(textRenderer, "Keybinds", panelX + 18, CONTENT_TOP - 14, 0xFFE0E0E0);
            for (KeyRow row : keyRows) {
                context.drawTextWithShadow(textRenderer, row.label(), row.x(), row.y(), 0xFFE0E0E0);
            }
        }
    }

    protected boolean handleKeyPressed(int keyCode, InputUtil.Key key) {
        if (listeningKey != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listeningKey = null;
                refreshKeyButtons();
                return true;
            }
            listeningKey.setBoundKey(keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE
                    ? InputUtil.UNKNOWN_KEY
                    : key);
            KeyBinding.updateKeysByCode();
            if (client != null) {
                client.options.write();
            }
            listeningKey = null;
            refreshKeyButtons();
            setStatus("Keybind saved", 0xFFB6F6C5);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            return scrollBy(ROW_HEIGHT);
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            return scrollBy(-ROW_HEIGHT);
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            return scrollBy(viewportHeight());
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            return scrollBy(-viewportHeight());
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= CONTENT_TOP && mouseY <= contentBottom()) {
            return scrollBy((int) Math.round(-verticalAmount * ROW_HEIGHT));
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private TextFieldWidget field(int x, int y, int width, String value, java.util.function.Predicate<String> predicate) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 20, LocalText.text(""));
        field.setText(value);
        field.setMaxLength(32);
        field.setTextPredicate(predicate);
        field.setPosition(x, screenY(y));
        if (isContentVisible(y, 20)) {
            addDrawableChild(field);
        }
        return field;
    }

    private void addToggle(int x, int y, int width, String label, BooleanSupplier getter, BooleanConsumer setter) {
        ButtonWidget[] holder = new ButtonWidget[1];
        holder[0] = ButtonWidget.builder(LocalText.text(toggleText(label, getter.getAsBoolean())), button -> {
                    setter.accept(!getter.getAsBoolean());
                    holder[0].setMessage(LocalText.text(toggleText(label, getter.getAsBoolean())));
                })
                .dimensions(x, screenY(y), width, 20)
                .build();
        addIfVisible(y, 20, holder[0]);
    }

    private void applySeed() {
        try {
            long seed = Long.parseLong(seedField.getText().trim());
            XrayConfig config = XrayClientMod.CONFIG.get();
            if (config.worldSeed != seed) {
                config.worldSeed = seed;
                config.seedConfigured = true;
                saveAndRegenerate();
                setStatus("Seed saved; cache regenerating", 0xFFB6F6C5);
            } else {
                saveConfigOnly();
                setStatus("Seed unchanged", 0xFFB6F6C5);
            }
        } catch (NumberFormatException ignored) {
            setStatus("Invalid seed", 0xFFFF8585);
        }
    }

    private void applyTransparency() {
        try {
            XrayConfig config = XrayClientMod.CONFIG.get();
            config.transparencyPercent = Integer.parseInt(transparencyField.getText().trim());
            config.applyDefaults();
            transparencyField.setText(Integer.toString(config.transparencyPercent));
            saveAndRefreshTerrain();
            setStatus("Transparency set to " + config.transparencyPercent + "%", 0xFFB6F6C5);
        } catch (NumberFormatException ignored) {
            setStatus("Invalid transparency", 0xFFFF8585);
        }
    }

    private void applyRenderLimits() {
        try {
            XrayConfig config = XrayClientMod.CONFIG.get();
            config.maxRenderedHighlights = Integer.parseInt(maxRenderField.getText().trim());
            config.distanceLimitBlocks = Integer.parseInt(distanceField.getText().trim());
            config.applyDefaults();
            maxRenderField.setText(Integer.toString(config.maxRenderedHighlights));
            distanceField.setText(Integer.toString(config.distanceLimitBlocks));
            saveConfigOnly();
            setStatus("Render limits saved", 0xFFB6F6C5);
        } catch (NumberFormatException ignored) {
            setStatus("Invalid render limit", 0xFFFF8585);
        }
    }

    private void saveConfigOnly() {
        XrayClientMod.CONFIG.save();
        setStatus("Settings saved", 0xFFB6F6C5);
    }

    private void saveAndRegenerate() {
        XrayClientMod.CONFIG.save();
        keybindManager.clearPredictionState();
        setStatus("Settings saved; cache regenerating", 0xFFB6F6C5);
    }

    private void saveAndRefreshTerrain() {
        XrayClientMod.CONFIG.save();
        TerrainTransparencyHooks.scheduleTerrainRefresh(MinecraftClient.getInstance());
        setStatus("Settings saved", 0xFFB6F6C5);
    }

    private void refreshKeyButtons() {
        for (Map.Entry<KeyBinding, ButtonWidget> entry : keyButtons.entrySet()) {
            entry.getValue().setMessage(keyButtonText(entry.getKey()));
        }
    }

    private Text keyButtonText(KeyBinding keyBinding) {
        if (listeningKey == keyBinding) {
            return LocalText.text("> Press key <");
        }
        return LocalText.text(KeyNameResolver.keyBindingName(keyBinding));
    }

    private void setStatus(String status, int color) {
        this.status = status;
        this.statusColor = color;
        this.statusSetTimeMs = Util.getMeasuringTimeMs();
    }

    private int panelWidth() {
        return Math.min(560, Math.max(320, width - 24));
    }

    private void updateContentHeight() {
        contentHeight = switch (tab) {
            case ORES -> 8 + OreTarget.values().length * ROW_HEIGHT;
            case VISUAL -> 284;
            case SEED -> 120;
            case KEYS -> 12 + keybindManager.managedBindings().size() * ROW_HEIGHT;
        };
    }

    private int screenY(int contentY) {
        return contentY - scrollY;
    }

    private int contentBottom() {
        return Math.max(CONTENT_TOP + 20, height - FOOTER_HEIGHT);
    }

    private int viewportHeight() {
        return Math.max(20, contentBottom() - CONTENT_TOP);
    }

    private int maxScroll() {
        return Math.max(0, CONTENT_TOP + contentHeight - contentBottom());
    }

    private boolean isContentVisible(int contentY, int itemHeight) {
        int y = screenY(contentY);
        return y + itemHeight >= CONTENT_TOP && y <= contentBottom();
    }

    private void addIfVisible(int contentY, int itemHeight, ButtonWidget widget) {
        if (isContentVisible(contentY, itemHeight)) {
            addDrawableChild(widget);
        }
    }

    private void drawVisibleLabel(DrawContext context, int x, int contentY, String text, int color) {
        if (isContentVisible(contentY, 10)) {
            context.drawTextWithShadow(textRenderer, text, x, screenY(contentY), color);
        }
    }

    private boolean scrollBy(int amount) {
        int next = clamp(scrollY + amount, 0, maxScroll());
        if (next == scrollY) {
            return false;
        }
        scrollY = next;
        clearAndInit();
        return true;
    }

    private void renderScrollHint(DrawContext context, int panelX, int panelWidth) {
        if (maxScroll() <= 0) {
            return;
        }
        String text = "Scroll " + scrollY + "/" + maxScroll();
        context.drawTextWithShadow(textRenderer, text, panelX + panelWidth - 86, height - 24, 0xFFAAAAAA);
    }

    private void renderStatus(DrawContext context, int panelX) {
        if (status.isEmpty()) {
            return;
        }
        long elapsed = Util.getMeasuringTimeMs() - statusSetTimeMs;
        int alpha;
        if (elapsed <= STATUS_HOLD_MS) {
            alpha = 255;
        } else {
            float progress = (float) (elapsed - STATUS_HOLD_MS) / (float) STATUS_FADE_MS;
            alpha = Math.max(0, Math.min(255, Math.round(255.0F * (1.0F - progress))));
        }
        if (alpha <= 0) {
            status = "";
            return;
        }
        context.drawTextWithShadow(textRenderer, status, panelX + 18, height - 24, withAlpha(statusColor, alpha));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String onOff(String label, boolean value) {
        return label + " " + (value ? "ON" : "OFF");
    }

    private static String toggleText(String label, boolean value) {
        return label + ": " + (value ? "ON" : "OFF");
    }

    private static String colorLabel(int color) {
        return "#" + String.format("%06X", color & 0xFFFFFF);
    }

    private static int nextColor(int current) {
        int normalized = 0xFF000000 | (current & 0xFFFFFF);
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (COLOR_PRESETS[i] == normalized) {
                return COLOR_PRESETS[(i + 1) % COLOR_PRESETS.length];
            }
        }
        return COLOR_PRESETS[0];
    }

    private static int nextAlpha(int current) {
        for (int i = 0; i < ALPHA_PRESETS.length; i++) {
            if (current <= ALPHA_PRESETS[i]) {
                return ALPHA_PRESETS[(i + 1) % ALPHA_PRESETS.length];
            }
        }
        return ALPHA_PRESETS[0];
    }

    private static boolean isSeedText(String text) {
        if (text.isEmpty() || "-".equals(text)) {
            return true;
        }
        try {
            Long.parseLong(text);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isPercentText(String text) {
        if (text.isEmpty()) {
            return true;
        }
        try {
            int value = Integer.parseInt(text);
            return value >= 0 && value <= 99;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isPositiveNumberText(String text) {
        if (text.isEmpty()) {
            return true;
        }
        try {
            return Integer.parseInt(text) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private enum Tab {
        ORES("Ores"),
        VISUAL("Visual"),
        SEED("Seed"),
        KEYS("Keys");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    private interface BooleanConsumer {
        void accept(boolean value);
    }

    private record KeyRow(String label, int x, int y) {
    }
}
