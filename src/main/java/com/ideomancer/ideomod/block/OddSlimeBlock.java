package com.ideomancer.ideomod.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Odd Slime - a directional slime block variant.
 * 
 * CORE MECHANIC:
 * ==============
 * Odd Slime is sticky like regular slime, but ONLY when it's being pushed
 * in its FACING direction. Push it any other way and it acts like a normal
 * non-sticky block — neighbors don't come along.
 * 
 * Example: An Odd Slime facing NORTH sitting next to a stone block.
 *   - Piston pushes NORTH → Odd Slime grabs stone, both move.
 *   - Piston pushes SOUTH → Odd Slime slides past stone, stone stays.
 *   - Piston pushes UP → Same, no grab.
 * 
 * When active (pushed in FACING direction), it grabs ALL 6 neighbors
 * just like regular slime. The FACING controls WHEN it's sticky, not WHERE.
 * 
 * STICKINESS — WHY canStickTo IS PERMISSIVE:
 * ===========================================
 * Forge checks canStickTo BIDIRECTIONALLY: A.canStickTo(B) AND B.canStickTo(A).
 * Both must return true or the grab fails before the mixin even sees it.
 * 
 * So for Oddstone to grab us, we must say "yes" to Oddstone here.
 * For us to grab Machine Blocks when active, we must say "yes" here too.
 * 
 * The ASYMMETRIC behaviors (Odd Slime can't grab Oddstone back, Machine Block
 * can't grab Odd Slime) are enforced by the mixin's grab immunity rules,
 * which run after canStickTo and can block specific grabs.
 * 
 * Only TRUE mutual exclusions (slime, honey) are hard-blocked here.
 */
public class OddSlimeBlock extends DirectionalBlock {

    public OddSlimeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Placement: faces the direction the player is looking, like pistons.
     * The "active" direction is where the arrow/indicator points.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    // ========================================================================
    // STICKINESS
    // ========================================================================

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    /**
     * Type compatibility for stickiness. Permissive by design — only
     * hard-excludes slime and honey. Everything else returns true so
     * the Forge bidirectional check passes. The mixin layer handles:
     *   - Directional gating (only sticky when pushed in FACING direction)
     *   - Grab immunity (Odd Slime can't grab Oddstone back, etc.)
     */
    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        // Mutual exclusion with vanilla slime (never stick, period)
        if (other.getBlock() instanceof SlimeBlock) return false;
        // Mutual exclusion with honey (never stick, period)
        if (other.getBlock() instanceof HoneyBlock) return false;
        // Everything else (Oddstone, Machine Block, Odd Slime, normal blocks): yes
        // Asymmetric behaviors enforced by mixin grab immunity rules
        return true;
    }

    // ========================================================================
    // ROTATION / MIRROR SUPPORT
    // ========================================================================

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
