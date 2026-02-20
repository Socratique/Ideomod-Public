package com.ideomancer.ideomod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Oddstone Repulser -- scans for the nearest Oddstone in its FACING direction
 * and pushes it away by 1 block.
 *
 * All redstone detection, block event handling, and placement logic is
 * inherited from {@link AbstractRangedPistonBlock}. This class only
 * defines the scan-and-move behavior.
 */
public class OddstoneRepulserBlock extends AbstractRangedPistonBlock {

    public OddstoneRepulserBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void fire(Level level, BlockPos pos, Direction facing) {
        RangedPistonUtil.tryRepulse(level, pos, facing, RangedPistonUtil.ODDSTONE_ONLY);
    }
}
