package dev.buffy.ytlyrics;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Drag the lyric card anywhere, scroll to resize, R to reset, Esc to save. */
public final class HudEditScreen extends Screen {
    private boolean dragging;
    private double lastX;
    private double lastY;

    public HudEditScreen() {
        super(Text.literal("Lyrics HUD layout"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LyricsHud.renderEditor(context);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            dragging = true;
            lastX = click.x();
            lastY = click.y();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging) {
            HudConfig.moveTo(HudConfig.x() + (float) (offsetX / width),
                    HudConfig.y() + (float) (offsetY / height));
            lastX = click.x();
            lastY = click.y();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        HudConfig.resize((float) vertical * 0.05f);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_R) {
            HudConfig.reset();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        HudConfig.save();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
