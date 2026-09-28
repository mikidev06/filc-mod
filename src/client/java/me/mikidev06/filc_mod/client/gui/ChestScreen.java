package me.mikidev06.filc_mod.client.gui;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A screen painted to look like a double chest.
 *
 * <p>This deliberately is not a HandledScreen. A real container screen needs a
 * ScreenHandler synced from the server, and a client-only mod has none: faking
 * one would make the client send slot-click packets that a server will reject.
 * Drawing the chest texture and hit-testing the slots ourselves gives the same
 * appearance with no server interaction at all, and lets a "slot" mean whatever
 * the screen wants.
 */
public abstract class ChestScreen extends Screen {

    protected static final Identifier TEXTURE =
            Identifier.of("minecraft", "textures/gui/container/generic_54.png");

    protected static final int COLUMNS = 9;
    protected static final int ROWS = 6;
    protected static final int SLOT_SIZE = 18;
    protected static final int SLOT_COUNT = COLUMNS * ROWS;

    /** Width of the chest graphic, matching the vanilla container. */
    protected static final int BACKGROUND_WIDTH = 176;
    /** Title strip plus six slot rows. */
    private static final int BODY_HEIGHT = ROWS * SLOT_SIZE + 17;
    /** The outer border strip lifted from the bottom of the texture. */
    private static final int FOOTER_HEIGHT = 7;
    private static final int FOOTER_V = 215;
    protected static final int BACKGROUND_HEIGHT = BODY_HEIGHT + FOOTER_HEIGHT;

    /** Offset of the first slot inside the background, as in vanilla. */
    private static final int SLOT_ORIGIN_X = 8;
    private static final int SLOT_ORIGIN_Y = 18;

    protected int backgroundX;
    protected int backgroundY;

    protected ChestScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.backgroundX = (this.width - BACKGROUND_WIDTH) / 2;
        this.backgroundY = (this.height - BACKGROUND_HEIGHT) / 2;
    }

    /** The world carries on behind the screen; nothing here needs it paused. */
    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- subclass contract ----------------------------------------------

    /** The stack to draw in a slot, or an empty stack to leave it blank. */
    protected abstract ItemStack stackFor(int slot);

    /** Tooltip lines for a slot, or an empty list for no tooltip. */
    protected abstract List<Text> tooltipFor(int slot);

    /** Invoked when a slot is left-clicked. */
    protected void onSlotClick(int slot) {
    }

    /**
     * Text drawn in the slot's bottom-right corner, as the stack count normally
     * would be. Null draws nothing.
     */
    protected String countLabel(int slot) {
        return null;
    }

    /** Extra drawing once the chest and its slots are down, before tooltips. */
    protected void renderOverlay(DrawContext context, int mouseX, int mouseY) {
    }

    // ---- geometry --------------------------------------------------------

    protected int slotX(int slot) {
        return backgroundX + SLOT_ORIGIN_X + (slot % COLUMNS) * SLOT_SIZE;
    }

    protected int slotY(int slot) {
        return backgroundY + SLOT_ORIGIN_Y + (slot / COLUMNS) * SLOT_SIZE;
    }

    protected static int slotIndex(int row, int column) {
        return row * COLUMNS + column;
    }

    /** The slot under the cursor, or -1 when the cursor is elsewhere. */
    protected int slotAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int x = slotX(slot);
            int y = slotY(slot);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return -1;
    }

    // ---- rendering -------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawTexture(TEXTURE, backgroundX, backgroundY, 0, 0, BACKGROUND_WIDTH, BODY_HEIGHT);
        context.drawTexture(
                TEXTURE,
                backgroundX,
                backgroundY + BODY_HEIGHT,
                0,
                FOOTER_V,
                BACKGROUND_WIDTH,
                FOOTER_HEIGHT);

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = stackFor(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int x = slotX(slot);
            int y = slotY(slot);
            context.drawItem(stack, x, y);
            context.drawItemInSlot(this.textRenderer, stack, x, y, countLabel(slot));
        }

        renderOverlay(context, mouseX, mouseY);

        int hovered = slotAt(mouseX, mouseY);
        if (hovered >= 0) {
            List<Text> lines = tooltipFor(hovered);
            if (lines != null && !lines.isEmpty()) {
                context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                onSlotClick(slot);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
