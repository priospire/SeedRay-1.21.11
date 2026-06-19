package com.seedxray.client.screen;

import com.seedxray.client.input.KeybindManager;
import net.minecraft.client.util.InputUtil;

public final class XrayConfigScreen extends XrayConfigScreenBase {
    public XrayConfigScreen(KeybindManager keybindManager) {
        super(keybindManager);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return handleKeyPressed(keyCode, InputUtil.fromKeyCode(keyCode, scanCode)) || super.keyPressed(keyCode, scanCode, modifiers);
    }
}
