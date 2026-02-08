package ru.mgcveqbj.pearlnavigator;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class PearlNavigatorClient implements ClientModInitializer {
    private static final String CATEGORY = "key.categories.pearlnavigator";
    private static KeyBinding toggleKey;
    private static KeyBinding configKey;
    private static KeyBinding aimKey;
    private static KeyBinding autoKey;

    @Override
    public void onInitializeClient() {
        PearlNavigator.init();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pearlnavigator.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY
        ));
        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pearlnavigator.config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
        ));
        aimKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pearlnavigator.aimlock",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            CATEGORY
        ));
        autoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pearlnavigator.auto",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(PearlNavigatorClient::onClientTick);
        HudRenderCallback.EVENT.register(PearlNavigatorHud::render);
        WorldRenderEvents.LAST.register(PearlNavigatorWorldRender::render);
    }

    private static void onClientTick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            PearlNavigator.toggle(client);
        }
        while (aimKey.wasPressed()) {
            PearlNavigator.toggleAimAssist(client);
        }
        while (autoKey.wasPressed()) {
            PearlNavigator.toggleAutoMode(client);
        }
        while (configKey.wasPressed()) {
            client.openScreen(new PearlNavigatorConfigScreen(null));
        }

        PearlNavigator.onClientTick(client);
    }
}
