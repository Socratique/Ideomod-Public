package com.ideomancer.ideomod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Oddstone Attractor -- scans for the farthest Oddstone in its FACING direction
 * and pulls it toward the device by 1 block.
 *
 * All redstone detection, block event handling, and placement logic is
 * inherited from {@link AbstractRangedPistonBlock}. This class only
 * defines the scan-and-move behavior.
 */
public class OddstoneAttractorBlock extends AbstractRangedPistonBlock {

    public OddstoneAttractorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void fire(Level level, BlockPos pos, Direction facing) {
        RangedPistonUtil.tryAttract(level, pos, facing, RangedPistonUtil.ODDSTONE_ONLY);
    }
}
