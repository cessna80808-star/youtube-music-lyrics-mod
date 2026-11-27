package dev.buffy.ytlyrics;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YtLyricsMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ytlyrics");

    @Override
    public void onInitializeClient() {
        HudConfig.load();
        NowPlaying.start();

        KeyBinding visualizer = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ytlyrics.visualizer", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V,
                KeyBinding.Category.create(Identifier.of("ytlyrics", "main"))));

        KeyBinding layout = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ytlyrics.layout", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H,
                KeyBinding.Category.create(Identifier.of("ytlyrics", "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (visualizer.wasPressed()) LyricsHud.toggleVisualizer();

            while (layout.wasPressed()) {
                if (client.player != null && client.currentScreen == null) client.setScreen(new HudEditScreen());
            }
        });

        LOGGER.info("YouTube Music lyrics HUD ready");
    }
}
