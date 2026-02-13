package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
 *    IMPORTANT — canStickTo vs MIXIN ROLES:
 *    Forge checks canStickTo BIDIRECTIONALLY (both blocks must agree).
 *    For one-way stickiness (slime grabs Machine Block, Machine Block
 *    doesn't grab slime), canStickTo must be PERMISSIVE — return true
 *    for any pair where asymmetric behavior is desired. The mixin's
 *    grab immunity rules then block the unwanted direction.
 * 
 *    See: docs/STICKINESS_SYSTEM.md for the full explanation.
 * 
 *    canStickTo returns TRUE for: Machine Block, Slime, Honey, Odd Slime
 *    Grab immunity rules BLOCK: Machine Block → Slime, Machine Block → Honey,
 *                                Machine Block → Odd Slime
 *    Net result: Slime/Honey/Odd Slime CAN grab Machine Block,
 *                Machine Block CANNOT grab them back.
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
    // FEATURE 1: Selective Stickiness (Forge hooks + mixin)
    // ========================================================================

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    /**
     * PERMISSIVE canStickTo — returns true for any sticky block type.
     * 
     * This is intentional. Forge's bidirectional canStickTo check means
     * if we return false here, the OTHER block can't grab us either.
     * We want slime/honey/odd slime to be ABLE to grab Machine Block
     * (one-way), so we must return true here.
     * 
     * The reverse (Machine Block grabbing slime/honey/odd slime) is
     * blocked by grab immunity rules in StickinessRules, enforced by
     * the PistonStructureResolver mixin.
     * 
     * For non-sticky blocks (stone, dirt, etc.): returns false.
     * Machine Block only actively grabs other Machine Blocks.
     */
    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        // Machine Block sticks to Machine Block (the actual desired behavior)
        if (other.getBlock() instanceof MachineBlock) return true;
        // Permissive for all slime-type blocks — lets THEM grab us.
        // Our mixin rules prevent US from grabbing THEM.
        if (other.getBlock() instanceof SlimeBlock) return true;
        if (other.getBlock() instanceof HoneyBlock) return true;
        if (other.getBlock() instanceof OddSlimeBlock) return true;
        // Everything else: no
        return false;
    }

    // ========================================================================
    // FEATURE 2: Redstone Conduction
    // ========================================================================

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

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

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this)) {
            updatePowerState(level, pos, state);
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }
        }
    }

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

    private int calculatePower(Level level, BlockPos pos) {
        int bestPower = 0;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            Block neighborBlock = neighborState.getBlock();

            if (neighborBlock instanceof MachineBlock) {
                int neighborPower = neighborState.getValue(POWER) - 1;
                if (neighborPower > bestPower) {
                    bestPower = neighborPower;
                }
            } else if (isRedstoneComponent(neighborBlock)) {
                int signal = level.getSignal(neighborPos, dir);
                if (signal > bestPower) {
                    bestPower = signal;
                }
            }
        }

        return Math.min(15, Math.max(0, bestPower));
    }

    private static boolean isRedstoneComponent(Block block) {
        return block.defaultBlockState().is(ModTags.REDSTONE_COMPONENTS);
    }

    // ========================================================================
    // COLOR TINTING (for visual gradient)
    // ========================================================================

    public static int getPowerColor(int power) {
        float t = power / 15.0f;
        int r = (int) (80 + t * (255 - 80));
        int g = (int) (80 + t * (48 - 80));
        int b = (int) (80 + t * (48 - 80));
        return (r << 16) | (g << 8) | b;
    }
}
