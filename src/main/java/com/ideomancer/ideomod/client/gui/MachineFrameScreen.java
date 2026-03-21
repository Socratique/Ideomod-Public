package com.ideomancer.ideomod.client.gui;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.machine.MachineFrameMenu;
import com.ideomancer.ideomod.network.ModNetworking;
import com.ideomancer.ideomod.network.RepeaterDelayPacket;
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
 * FIXED-SIZE LAYOUT:
 * ==================
 * All machine GUIs use the same height (determined at open time by the
 * menu's content height). The transit slot area exists in the pipe layout
 * but the slot tile is only rendered when the BE is a pipe.
 *
 * For Machine Repeater, 4 numbered delay buttons (1-4) are rendered in
 * the same zone where the transit slot would be for Pipes. Since Repeater
 * and Pipe never coexist, this is safe.
 *
 * GUI ASSEMBLY FROM SPRITESHEET (ideomod_gui.png):
 * =================================================
 *   1. Top border strip (176x4)
 *   2. Content area: left/right edges + background fill (176 x contentH)
 *   3. Spec slot tile stamped at (79, 19) -- always
 *   4. Transit slot tile stamped at (79, 37) -- only when pipe
 *      OR: 4 delay buttons at (52, 37)...(120, 37) -- only when repeater
 *   5. Player inventory section (176x90) -- pre-assembled with slots
 *   6. Bottom border strip (176x4)
 *
 * BUTTON SPRITESHEET (ideomod_numbered_buttons.png, 256x256):
 *   Buttons 1-64 at 16x16 each, 16 per row.
 *   Unselected: rows 0-3 (Y 0-63)
 *   Selected:   rows 4-7 (Y 64-127)
 *   UV for button N: col=(N-1)%16, row=(N-1)/16
 *     unselected: u=col*16, v=row*16
 *     selected:   u=col*16, v=row*16+64
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameScreen extends AbstractContainerScreen<MachineFrameMenu> {

    private static final ResourceLocation GUI_SHEET =
            new ResourceLocation(Ideomod.MOD_ID, "textures/gui/ideomod_gui.png");

    private static final ResourceLocation BUTTON_SHEET =
            new ResourceLocation(Ideomod.MOD_ID, "textures/gui/ideomod_numbered_buttons.png");

    private static final int TEX = 256; // spritesheet texture size

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
    private static final int BG_COLOR = 0xFFC6C6C6; // rgb(198,198,198)

    // Button layout constants
    private static final int BUTTON_SIZE = 16;
    private static final int BUTTON_GAP = 2;  // gap between buttons
    private static final int BUTTON_COUNT = 4; // buttons 1-4

    // Buttons are centered horizontally in the content area, on the transit slot row (Y=37)
    // Total width: 4 buttons * 16px + 3 gaps * 2px = 70px
    // Center offset from left: (176 - 70) / 2 = 53
    private static final int BUTTONS_START_X = 53;
    private static final int BUTTONS_Y = 37;

    private final Component playerInventoryTitle;

    public MachineFrameScreen(MachineFrameMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.playerInventoryTitle = playerInv.getDisplayName();
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = this.menu.getGuiHeight();
        this.inventoryLabelY = BORDER_H + this.menu.getContentHeight() + 1;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int contentH = this.menu.getContentHeight();

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
        // Spec slot: always at (79, 19)
        g.blit(GUI_SHEET, x + 79, y + 19, SLOT_U, SLOT_V, SLOT_SIZE, SLOT_SIZE, TEX, TEX);

        // Transit slot: only rendered for pipe (empty background otherwise)
        if (this.menu.isPipe()) {
            g.blit(GUI_SHEET, x + 79, y + 37, SLOT_U, SLOT_V, SLOT_SIZE, SLOT_SIZE, TEX, TEX);
        }

        // Delay buttons: only rendered for repeater
        if (this.menu.isRepeater()) {
            renderDelayButtons(g, x, y);
        }

        // 4. Player inventory section
        int invY = y + BORDER_H + contentH;
        g.blit(GUI_SHEET, x, invY, INV_U, INV_V, GUI_WIDTH, INV_H, TEX, TEX);

        // 5. Bottom border strip
        int bottomY = invY + INV_H;
        g.blit(GUI_SHEET, x, bottomY, BOT_BORDER_U, BOT_BORDER_V, GUI_WIDTH, BORDER_H, TEX, TEX);
    }

    /**
     * Renders the 4 delay buttons (1-4) from the numbered buttons spritesheet.
     * The currently selected delay shows the "selected" (pressed) variant.
     */
    private void renderDelayButtons(GuiGraphics g, int guiX, int guiY) {
        int currentDelay = this.menu.getRepeaterDelay();

        for (int i = 0; i < BUTTON_COUNT; i++) {
            int buttonNumber = i + 1; // 1-4
            int btnX = guiX + BUTTONS_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
            int btnY = guiY + BUTTONS_Y;

            // UV lookup from spritesheet
            int col = (buttonNumber - 1) % 16;
            int row = (buttonNumber - 1) / 16;
            int u = col * BUTTON_SIZE;
            int v = row * BUTTON_SIZE;

            // Use selected (pressed) variant for current delay, unselected for others
            if (buttonNumber == currentDelay) {
                v += 64; // Selected buttons start at Y=64
            }

            g.blit(BUTTON_SHEET, btnX, btnY, u, v, BUTTON_SIZE, BUTTON_SIZE, TEX, TEX);
        }
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
        // Check if a delay button was clicked (only for repeater)
        if (button == 0 && this.menu.isRepeater()) {
            int guiX = (this.width - this.imageWidth) / 2;
            int guiY = (this.height - this.imageHeight) / 2;

            for (int i = 0; i < BUTTON_COUNT; i++) {
                int btnX = guiX + BUTTONS_START_X + i * (BUTTON_SIZE + BUTTON_GAP);
                int btnY = guiY + BUTTONS_Y;

                if (mouseX >= btnX && mouseX < btnX + BUTTON_SIZE
                        && mouseY >= btnY && mouseY < btnY + BUTTON_SIZE) {
                    int newDelay = i + 1;
                    // Send packet to server
                    ModNetworking.CHANNEL.sendToServer(
                            new RepeaterDelayPacket(this.menu.getBlockPos(), newDelay));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
