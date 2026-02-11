package com.ideomancer.ideomod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Machine Block - a mechanical building block with two key properties:
 * 
 * 1. SELECTIVE STICKINESS:
 * 
 *    Acts like a slime block, but ONLY sticks to other Machine Blocks.
 *    When pushed by a piston, it drags adjacent Machine Blocks along
 *    (which drag theirs, etc.), but ignores all other block types.
 * 
 *    Exclusive with slime and honey - same pattern as honey vs slime
 *    in vanilla (they refuse to stick to each other).
 * 
 *    Uses Forge's isStickyBlock/canStickTo hooks. No event handler needed.
 * 
 * 2. REDSTONE CONDUCTION: Acts like redstone dust in solid block form.
 *    Signal decays by 1 per Machine Block. Only accepts signal from
 *    actual redstone components (whitelist) to prevent feedback loops.
 *    Color gradient from dark steel grey (unpowered) to bright red (full).
 */
public class MachineBlock extends Block {

    // Power level 0-15, same range as redstone dust
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    // Guard flag to prevent infinite recursion during redstone updates.
    private static boolean isUpdating = false;

    public MachineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    // ========================================================================
    // FEATURE 1: Selective Stickiness (Forge hooks)
    // ========================================================================

    /**
     * Tells the piston resolver "this block is sticky" - meaning it
     * should try to drag neighbors along when pushed/pulled.
     * Same as what slime blocks and honey blocks return.
     */
    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    /**
     * Controls WHAT this block sticks to.
     * 
     * - Machine Block + Machine Block: stick (both return true)
     * - Machine Block + Slime: EXCLUDED (we say no)
     * - Machine Block + Honey: EXCLUDED (we say no)
     * - Machine Block + anything else: no (not a Machine Block)
     * 
     * This is the same pattern vanilla uses for honey vs slime exclusion.
     */
    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        // Exclusive with slime and honey
        if (other.getBlock() instanceof SlimeBlock) return false;
        if (other.getBlock() instanceof HoneyBlock) return false;
        // Stick to other Machine Blocks only
        return other.getBlock() instanceof MachineBlock;
    }

    // ========================================================================
    // FEATURE 2: Redstone Conduction
    // ========================================================================

    /**
     * "Yes, I am a source of redstone signal."
     * Must return true for getSignal/getDirectSignal to be called.
     */
    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * Returns the redstone signal strength this block provides to a neighbor.
     * Called when an adjacent block asks "how much power is this block giving me?"
     */
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    /**
     * "Direct signal" - strong power. Lets repeaters read our signal
     * and lets us power blocks that need strong signal.
     */
    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    /**
     * Called when a neighboring block changes. Recalculates our power.
     * Logic mirrors redstone dust: check all 6 neighbors, take the
     * best signal (with -1 decay for Machine Block chains).
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (isUpdating) return;

        int currentPower = state.getValue(POWER);
        int newPower = calculatePower(level, pos);

        if (newPower != currentPower) {
            isUpdating = true;
            try {
                level.setBlock(pos, state.setValue(POWER, newPower), 2);
                level.updateNeighborsAt(pos, this);
            } finally {
                isUpdating = false;
            }
        }
    }

    /**
     * Recalculate power when the block is first placed.
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this)) {
            updatePowerState(level, pos, state);
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }
        }
    }

    /**
     * When removed, notify neighbors so they can recalculate.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !newState.is(this)) {
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void updatePowerState(Level level, BlockPos pos, BlockState state) {
        int newPower = calculatePower(level, pos);
        if (newPower != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, newPower), 2);
            level.updateNeighborsAt(pos, this);
        }
    }

    /**
     * Calculates power from neighbors. Only accepts signal from actual
     * redstone components (whitelist) to prevent feedback loops where
     * we power a lamp, the lamp's block becomes "powered", and we read
     * that power back. Machine Block neighbors propagate with -1 decay.
     */
    private int calculatePower(Level level, BlockPos pos) {
        int bestPower = 0;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            Block neighborBlock = neighborState.getBlock();

            if (neighborBlock instanceof MachineBlock) {
                // Adjacent Machine Block: take its power - 1 (decay)
                int neighborPower = neighborState.getValue(POWER) - 1;
                if (neighborPower > bestPower) {
                    bestPower = neighborPower;
                }
            } else if (isRedstoneComponent(neighborBlock)) {
                // Whitelisted redstone component: take its signal directly
                int signal = level.getSignal(neighborPos, dir);
                if (signal > bestPower) {
                    bestPower = signal;
                }
            }
        }

        return Math.min(15, Math.max(0, bestPower));
    }

    /**
     * Whitelist of blocks that are "real" redstone sources.
     * We only accept signal from these to prevent feedback loops.
     * Generic solid blocks (lamps, stone, etc.) that are only powered
     * because WE powered them are excluded.
     */
    private static boolean isRedstoneComponent(Block block) {
        return block == Blocks.REDSTONE_WIRE
            || block == Blocks.LEVER
            || block == Blocks.STONE_BUTTON
            || block == Blocks.OAK_BUTTON
            || block == Blocks.SPRUCE_BUTTON
            || block == Blocks.BIRCH_BUTTON
            || block == Blocks.JUNGLE_BUTTON
            || block == Blocks.ACACIA_BUTTON
            || block == Blocks.DARK_OAK_BUTTON
            || block == Blocks.CHERRY_BUTTON
            || block == Blocks.MANGROVE_BUTTON
            || block == Blocks.BAMBOO_BUTTON
            || block == Blocks.CRIMSON_BUTTON
            || block == Blocks.WARPED_BUTTON
            || block == Blocks.POLISHED_BLACKSTONE_BUTTON
            || block == Blocks.STONE_PRESSURE_PLATE
            || block == Blocks.OAK_PRESSURE_PLATE
            || block == Blocks.SPRUCE_PRESSURE_PLATE
            || block == Blocks.BIRCH_PRESSURE_PLATE
            || block == Blocks.JUNGLE_PRESSURE_PLATE
            || block == Blocks.ACACIA_PRESSURE_PLATE
            || block == Blocks.DARK_OAK_PRESSURE_PLATE
            || block == Blocks.CHERRY_PRESSURE_PLATE
            || block == Blocks.MANGROVE_PRESSURE_PLATE
            || block == Blocks.BAMBOO_PRESSURE_PLATE
            || block == Blocks.CRIMSON_PRESSURE_PLATE
            || block == Blocks.WARPED_PRESSURE_PLATE
            || block == Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE
            || block == Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE
            || block == Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE
            || block == Blocks.REPEATER
            || block == Blocks.COMPARATOR
            || block == Blocks.OBSERVER
            || block == Blocks.REDSTONE_BLOCK
            || block == Blocks.REDSTONE_TORCH
            || block == Blocks.REDSTONE_WALL_TORCH
            || block == Blocks.DAYLIGHT_DETECTOR
            || block == Blocks.TRAPPED_CHEST
            || block == Blocks.TRIPWIRE_HOOK
            || block == Blocks.LECTERN
            || block == Blocks.TARGET
            || block == Blocks.SCULK_SENSOR
            || block == Blocks.CALIBRATED_SCULK_SENSOR
            || block == Blocks.LIGHTNING_ROD
            || block == Blocks.DETECTOR_RAIL;
    }

    // ========================================================================
    // COLOR TINTING (for visual gradient)
    // ========================================================================

    /**
     * Returns a color int for the given power level. Goes from dark
     * steel grey (power 0) to bright red (power 15), like redstone dust.
     * Used by client-side color handler to tint the block texture.
     */
    public static int getPowerColor(int power) {
        float t = power / 15.0f;
        int r = (int) (80 + t * (255 - 80));
        int g = (int) (80 + t * (48 - 80));
        int b = (int) (80 + t * (48 - 80));
        return (r << 16) | (g << 8) | b;
    }
}
