package com.ideomancer.ideomod.client.gui;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.machine.MachineFrameMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client-side GUI screen for the Machine Frame's single-slot interface.
 *
 * Draws a compact GUI with one specialization slot centered above the
 * player inventory. Uses a custom background texture.
 *
 * GUI dimensions: 176 x 133 (standard width, shorter than furnace since
 * we only have one slot row above the player inventory).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameScreen extends AbstractContainerScreen<MachineFrameMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Ideomod.MOD_ID, "textures/gui/machine_frame.png");

    public MachineFrameScreen(MachineFrameMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        // GUI dimensions: 176 wide (standard), 133 tall (compact)
        this.imageWidth = 176;
        this.imageHeight = 133;
        // Adjust inventory label position for our shorter GUI
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
