package com.ideomancer.ideomod.client.gui;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.machine.MachineFrameMenu;
import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.network.EnginePulseIntervalPacket;
import com.ideomancer.ideomod.network.ModNetworking;
import com.ideomancer.ideomod.network.RepeaterDelayPacket;
import com.ideomancer.ideomod.network.TimerDurationPacket;
import com.ideomancer.ideomod.network.TransformerLevelPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;

/**
 * Client-side GUI screen for all Machine Family blocks.
 *
 * FIXED-HEIGHT GUI:
 * =================
 * All Machine Family GUIs share the same fixed height (187px total:
 * 4px top border + 89px content + 90px inventory + 4px bottom border).
 * This is sized for the tallest specialization (Timer). Shorter
 * specializations have extra grey background between their controls
 * and the player inventory — visually clean and avoids the need to
 * reposition slots at runtime (Slot.y is final in Forge 1.20.1).
 *
 * Specialization-specific controls (buttons, labels, transit slot) are
 * rendered conditionally within the fixed content area. The GUI swaps
 * its rendered controls seamlessly when the specialization item changes,
 * without closing or reopening.
 *
 * BUTTON SPRITESHEET (ideomod_numbered_buttons.png, 256x256):
 *   128 buttons at 16x16 each, 16 per row.
 *   Buttons 1-64:
 *     Unselected: rows 0-3 (Y 0-63)
 *     Selected:   rows 4-7 (Y 64-127)
 *   Buttons 65-128:
 *     Unselected: rows 8-11 (Y 128-191)
 *     Selected:   rows 12-15 (Y 192-255)
 *
 * TODO: Add label text above buttons in Repeater and Transformer GUIs
 *       in a future pass (matching Timer's "Pulse Tick Duration" label).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameScreen extends AbstractContainerScreen<MachineFrameMenu> {

    private static final ResourceLocation GUI_SHEET =
            new ResourceLocation(Ideomod.MOD_ID, "textures/gui/ideomod_gui.png");

    private static final ResourceLocation BUTTON_SHEET =
            new ResourceLocation(Ideomod.MOD_ID, "textures/gui/ideomod_numbered_buttons.png");

    private static final int TEX = 256;

    // GUI spritesheet UV coordinates
    private static final int SLOT_U = 0, SLOT_V = 0;
    private static final int LEFT_EDGE_U = 0, LEFT_EDGE_V = 18;
    private static final int RIGHT_EDGE_U = 4, RIGHT_EDGE_V = 18;
    private static final int INV_U = 0, INV_V = 20;
    private static final int TOP_BORDER_U = 0, TOP_BORDER_V = 110;
    private static final int BOT_BORDER_U = 0, BOT_BORDER_V = 114;

    private static final int SLOT_SIZE = 18;
    private static final int GUI_WIDTH = 176;
    private static final int BORDER_H = 4;
    private static final int INV_H = 90;
    private static final int BG_COLOR = 0xFFC6C6C6;

    // Button layout constants
    private static final int BUTTON_SIZE = 16;
    private static final int BUTTON_GAP = 2;

    // Repeater: 4 buttons in 1 row
    private static final int REPEATER_BUTTON_COUNT = 4;
    private static final int REPEATER_BUTTONS_START_X = 53;
    private static final int REPEATER_BUTTONS_Y = 37;

    // Transformer: 15 buttons in 2 rows (8 top, 7 bottom)
    private static final int TRANSFORMER_ROW1_COUNT = 8;
    private static final int TRANSFORMER_ROW2_COUNT = 7;
    private static final int TRANSFORMER_ROW1_START_X = 17;
    private static final int TRANSFORMER_ROW2_START_X = 26;
    private static final int TRANSFORMER_ROW1_Y = 40;
    private static final int TRANSFORMER_ROW2_Y = 40 + BUTTON_SIZE + BUTTON_GAP;

    // Timer: label + 16 buttons in 2 rows (8+8)
    private static final int TIMER_BUTTONS_PER_ROW = 8;
    private static final int TIMER_ROW_START_X = 17;
    private static final int TIMER_LABEL_Y = 40;
    private static final int TIMER_ROW1_Y = 52;
    private static final int TIMER_ROW2_Y = 52 + BUTTON_SIZE + BUTTON_GAP;

    // Engine: fuel slot + flame + label + 8 buttons in 1 row
    // Fuel slot uses transit slot position (80, 38) — directly below spec slot.
    // This is the slot index 1 position, same as pipe's transit slot.
    // Slot bottom edge is at y=37+18=55, so controls start below that.
    private static final int ENGINE_BUTTONS_COUNT = 8;
    private static final int ENGINE_BUTTONS_START_X = 17;
    private static final int ENGINE_LABEL_Y = 58;
    private static final int ENGINE_ROW1_Y = 70;

    // Flame indicator: to the left of the fuel slot (at 80,38), offset left by flame width + gap
    // Slot left edge = 79. Flame (14px wide) at 79 - 14 - 2 = 63
    private static final int ENGINE_FLAME_X = 63;
    private static final int ENGINE_FLAME_Y = 40; // Vertically centered with fuel slot
    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    // Vanilla furnace flame sprites from furnace.png (256x256 texture):
    //   Lit flame sprite: U=176, V=0, 14x14 (progress overlay, drawn bottom-up)
    //   Empty flame outline: U=56, V=36, 14x14 (baked into the GUI background)
    private static final ResourceLocation FURNACE_GUI_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/furnace.png");
    private static final int FLAME_LIT_U = 176;
    private static final int FLAME_LIT_V = 0;
    private static final int FLAME_EMPTY_U = 56;
    private static final int FLAME_EMPTY_V = 36;

    private static final int LABEL_COLOR = 4210752;

    private final Component playerInventoryTitle;

    public MachineFrameScreen(MachineFrameMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.playerInventoryTitle = playerInv.getDisplayName();
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = menu.getGuiHeight();
        this.inventoryLabelY = BORDER_H + MachineFrameMenu.CONTENT_HEIGHT + 1;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int contentH = MachineFrameMenu.CONTENT_HEIGHT;

        // 1. Top border strip
        g.blit(GUI_SHEET, x, y, TOP_BORDER_U, TOP_BORDER_V, GUI_WIDTH, BORDER_H, TEX, TEX);

        // 2. Content area: edges + background
        int contentY = y + BORDER_H;
        for (int row = 0; row < contentH; row++) {
            int rowY = contentY + row;
            g.blit(GUI_SHEET, x, rowY, LEFT_EDGE_U, LEFT_EDGE_V, 4, 1, TEX, TEX);
            g.fill(x + 4, rowY, x + GUI_WIDTH - 4, rowY + 1, BG_COLOR);
            g.blit(GUI_SHEET, x + GUI_WIDTH - 4, rowY, RIGHT_EDGE_U, RIGHT_EDGE_V, 4, 1, TEX, TEX);
        }

        // 3. Slot tiles
        g.blit(GUI_SHEET, x + 79, y + 19, SLOT_U, SLOT_V, SLOT_SIZE, SLOT_SIZE, TEX, TEX);

        if (this.menu.isPipe() || this.menu.isEngine()) {
            g.blit(GUI_SHEET, x + 79, y + 37, SLOT_U, SLOT_V, SLOT_SIZE, SLOT_SIZE, TEX, TEX);
        }

        if (this.menu.isRepeater()) {
            renderDelayButtons(g, x, y);
        }

        if (this.menu.isTransformer()) {
            renderTransformerButtons(g, x, y);
        }

        if (this.menu.isTimer()) {
            renderTimerButtons(g, x, y);
        }

        if (this.menu.isEngine()) {
            renderEngineControls(g, x, y);
        }

        // 4. Player inventory section
        int invY = y + BORDER_H + contentH;
        g.blit(GUI_SHEET, x, invY, INV_U, INV_V, GUI_WIDTH, INV_H, TEX, TEX);

        // 5. Bottom border strip
        int bottomY = invY + INV_H;
        g.blit(GUI_SHEET, x, bottomY, BOT_BORDER_U, BOT_BORDER_V, GUI_WIDTH, BORDER_H, TEX, TEX);
    }

    private void renderDelayButtons(GuiGraphics g, int guiX, int guiY) {
        int currentDelay = this.menu.getRepeaterDelay();
        for (int i = 0; i < REPEATER_BUTTON_COUNT; i++) {
            int buttonNumber = i + 1;
            int btnX = guiX + REPEATER_BUTTONS_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
            int btnY = guiY + REPEATER_BUTTONS_Y;
            renderNumberedButton(g, btnX, btnY, buttonNumber, buttonNumber == currentDelay);
        }
    }

    private void renderTransformerButtons(GuiGraphics g, int guiX, int guiY) {
        int currentLevel = this.menu.getTransformerLevel();
        for (int i = 0; i < TRANSFORMER_ROW1_COUNT; i++) {
            int buttonNumber = i + 1;
            int btnX = guiX + TRANSFORMER_ROW1_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
            int btnY = guiY + TRANSFORMER_ROW1_Y;
            renderNumberedButton(g, btnX, btnY, buttonNumber, buttonNumber == currentLevel);
        }
        for (int i = 0; i < TRANSFORMER_ROW2_COUNT; i++) {
            int buttonNumber = i + 9;
            int btnX = guiX + TRANSFORMER_ROW2_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
            int btnY = guiY + TRANSFORMER_ROW2_Y;
            renderNumberedButton(g, btnX, btnY, buttonNumber, buttonNumber == currentLevel);
        }
    }

    private void renderTimerButtons(GuiGraphics g, int guiX, int guiY) {
        int[] durations = IdeomodConfig.getTimerDurations();
        int currentDuration = this.menu.getTimerDuration();
        int buttonCount = Math.min(durations.length, 16);

        // Label
        String labelText = "Pulse Tick Duration";
        int labelWidth = this.font.width(labelText);
        int labelX = guiX + (GUI_WIDTH - labelWidth) / 2;
        g.drawString(this.font, labelText, labelX, guiY + TIMER_LABEL_Y, LABEL_COLOR, false);

        // Row 1
        int row1Count = Math.min(buttonCount, TIMER_BUTTONS_PER_ROW);
        for (int i = 0; i < row1Count; i++) {
            int tickValue = durations[i];
            int btnX = guiX + TIMER_ROW_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
            int btnY = guiY + TIMER_ROW1_Y;
            renderNumberedButton(g, btnX, btnY, tickValue, tickValue == currentDuration);
        }

        // Row 2
        int row2Count = Math.min(buttonCount - TIMER_BUTTONS_PER_ROW, TIMER_BUTTONS_PER_ROW);
        if (row2Count > 0) {
            int row2Width = row2Count * BUTTON_SIZE + (row2Count - 1) * BUTTON_GAP;
            int row2StartX = (GUI_WIDTH - row2Width) / 2;
            for (int i = 0; i < row2Count; i++) {
                int durationIndex = TIMER_BUTTONS_PER_ROW + i;
                int tickValue = durations[durationIndex];
                int btnX = guiX + row2StartX + i * (BUTTON_SIZE + BUTTON_GAP);
                int btnY = guiY + TIMER_ROW2_Y;
                renderNumberedButton(g, btnX, btnY, tickValue, tickValue == currentDuration);
            }
        }
    }

    private void renderEngineControls(GuiGraphics g, int guiX, int guiY) {
        // Fuel slot tile is rendered in the main slot section (shared with pipe).

        // Flame indicator (to the left of fuel slot)
        // Step 1: Always render the empty flame outline from the vanilla furnace GUI background
        int litTime = this.menu.getEngineLitTime();
        int litDuration = this.menu.getEngineLitDuration();

        g.blit(FURNACE_GUI_TEXTURE,
                guiX + ENGINE_FLAME_X, guiY + ENGINE_FLAME_Y,
                FLAME_EMPTY_U, FLAME_EMPTY_V,
                FLAME_WIDTH, FLAME_HEIGHT, 256, 256);

        // Step 2: Overlay lit flame portion from bottom up (when burning)
        if (litTime > 0 && litDuration > 0) {
            int flameHeight = (int) ((float) litTime / (float) litDuration * FLAME_HEIGHT);
            if (flameHeight > 0) {
                int yOffset = FLAME_HEIGHT - flameHeight;
                g.blit(FURNACE_GUI_TEXTURE,
                        guiX + ENGINE_FLAME_X, guiY + ENGINE_FLAME_Y + yOffset,
                        FLAME_LIT_U, FLAME_LIT_V + yOffset,
                        FLAME_WIDTH, flameHeight, 256, 256);
            }
        }

        // "Pulse Interval" label
        int[] durations = IdeomodConfig.getEngineDurations();
        int currentInterval = this.menu.getEnginePulseInterval();

        String labelText = "Pulse Interval";
        int labelWidth = this.font.width(labelText);
        int labelX = guiX + (GUI_WIDTH - labelWidth) / 2;
        g.drawString(this.font, labelText, labelX, guiY + ENGINE_LABEL_Y, LABEL_COLOR, false);

        // Duration buttons (1 row of 8)
        int buttonCount = Math.min(durations.length, ENGINE_BUTTONS_COUNT);
        for (int i = 0; i < buttonCount; i++) {
            int tickValue = durations[i];
            int btnX = guiX + ENGINE_BUTTONS_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
            int btnY = guiY + ENGINE_ROW1_Y;
            renderNumberedButton(g, btnX, btnY, tickValue, tickValue == currentInterval);
        }
    }

    /**
     * Renders a single numbered button from the spritesheet (1-128).
     */
    private void renderNumberedButton(GuiGraphics g, int x, int y, int buttonNumber, boolean selected) {
        buttonNumber = Math.max(1, Math.min(128, buttonNumber));
        int u, v;
        if (buttonNumber <= 64) {
            int col = (buttonNumber - 1) % 16;
            int row = (buttonNumber - 1) / 16;
            u = col * BUTTON_SIZE;
            v = row * BUTTON_SIZE;
            if (selected) v += 64;
        } else {
            int col = (buttonNumber - 65) % 16;
            int row = (buttonNumber - 65) / 16;
            u = col * BUTTON_SIZE;
            v = 128 + row * BUTTON_SIZE;
            if (selected) v += 64;
        }
        g.blit(BUTTON_SHEET, x, y, u, v, BUTTON_SIZE, BUTTON_SIZE, TEX, TEX);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, getDynamicTitle(), this.titleLabelX, this.titleLabelY, 4210752, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    private Component getDynamicTitle() {
        if (this.minecraft != null && this.minecraft.level != null) {
            Block block = this.minecraft.level.getBlockState(this.menu.getBlockPos()).getBlock();
            return Component.translatable(block.getDescriptionId());
        }
        return this.title;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    // ========================================================================
    // MOUSE CLICK HANDLING
    // ========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int guiX = (this.width - this.imageWidth) / 2;
            int guiY = (this.height - this.imageHeight) / 2;

            if (this.menu.isRepeater()) {
                for (int i = 0; i < REPEATER_BUTTON_COUNT; i++) {
                    int btnX = guiX + REPEATER_BUTTONS_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
                    int btnY = guiY + REPEATER_BUTTONS_Y;
                    if (mouseX >= btnX && mouseX < btnX + BUTTON_SIZE
                            && mouseY >= btnY && mouseY < btnY + BUTTON_SIZE) {
                        ModNetworking.CHANNEL.sendToServer(
                                new RepeaterDelayPacket(this.menu.getBlockPos(), i + 1));
                        return true;
                    }
                }
            }

            if (this.menu.isTransformer()) {
                for (int i = 0; i < TRANSFORMER_ROW1_COUNT; i++) {
                    int btnX = guiX + TRANSFORMER_ROW1_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
                    int btnY = guiY + TRANSFORMER_ROW1_Y;
                    if (mouseX >= btnX && mouseX < btnX + BUTTON_SIZE
                            && mouseY >= btnY && mouseY < btnY + BUTTON_SIZE) {
                        ModNetworking.CHANNEL.sendToServer(
                                new TransformerLevelPacket(this.menu.getBlockPos(), i + 1));
                        return true;
                    }
                }
                for (int i = 0; i < TRANSFORMER_ROW2_COUNT; i++) {
                    int btnX = guiX + TRANSFORMER_ROW2_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
                    int btnY = guiY + TRANSFORMER_ROW2_Y;
                    if (mouseX >= btnX && mouseX < btnX + BUTTON_SIZE
                            && mouseY >= btnY && mouseY < btnY + BUTTON_SIZE) {
                        ModNetworking.CHANNEL.sendToServer(
                                new TransformerLevelPacket(this.menu.getBlockPos(), i + 9));
                        return true;
                    }
                }
            }

            if (this.menu.isEngine()) {
                int[] engineDurations = IdeomodConfig.getEngineDurations();
                int engineButtonCount = Math.min(engineDurations.length, ENGINE_BUTTONS_COUNT);
                for (int i = 0; i < engineButtonCount; i++) {
                    int btnX = guiX + ENGINE_BUTTONS_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
                    int btnY = guiY + ENGINE_ROW1_Y;
                    if (mouseX >= btnX && mouseX < btnX + BUTTON_SIZE
                            && mouseY >= btnY && mouseY < btnY + BUTTON_SIZE) {
                        ModNetworking.CHANNEL.sendToServer(
                                new EnginePulseIntervalPacket(this.menu.getBlockPos(), i));
                        return true;
                    }
                }
            }

            if (this.menu.isTimer()) {
                int[] durations = IdeomodConfig.getTimerDurations();
                int buttonCount = Math.min(durations.length, 16);

                int row1Count = Math.min(buttonCount, TIMER_BUTTONS_PER_ROW);
                for (int i = 0; i < row1Count; i++) {
                    int btnX = guiX + TIMER_ROW_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
                    int btnY = guiY + TIMER_ROW1_Y;
                    if (mouseX >= btnX && mouseX < btnX + BUTTON_SIZE
                            && mouseY >= btnY && mouseY < btnY + BUTTON_SIZE) {
                        ModNetworking.CHANNEL.sendToServer(
                                new TimerDurationPacket(this.menu.getBlockPos(), i));
                        return true;
                    }
                }

                int row2Count = Math.min(buttonCount - TIMER_BUTTONS_PER_ROW, TIMER_BUTTONS_PER_ROW);
                if (row2Count > 0) {
                    int row2Width = row2Count * BUTTON_SIZE + (row2Count - 1) * BUTTON_GAP;
                    int row2StartX = (GUI_WIDTH - row2Width) / 2;
                    for (int i = 0; i < row2Count; i++) {
                        int btnX = guiX + row2StartX + i * (BUTTON_SIZE + BUTTON_GAP);
                        int btnY = guiY + TIMER_ROW2_Y;
                        if (mouseX >= btnX && mouseX < btnX + BUTTON_SIZE
                                && mouseY >= btnY && mouseY < btnY + BUTTON_SIZE) {
                            ModNetworking.CHANNEL.sendToServer(
                                    new TimerDurationPacket(this.menu.getBlockPos(), TIMER_BUTTONS_PER_ROW + i));
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
