package com.ideomancer.ideomod.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Machine Seat -- a Machine Base holding a boat (The Ideomancer, 2026-11-12).
 *
 * A block with an open top. Right-click sits you in it; sneak + right-click opens the
 * machine GUI (the boat slot). While you sit, the move keys send a redstone pulse out of
 * the face they point to, relative to where you look: forward out of the face you look
 * toward, back out of the one behind you, left and right out of the sides. Jump sends a
 * pulse out of the bottom face. Holding a key repeats the pulse (config seat.repeat_interval).
 *
 * The rider sits on a MachineSeatEntity, which follows the block when a piston or any
 * mover carries it, so a Seat can steer the flying machine it rides on.
 *
 * Shares MachineFrameBlockEntity: the input state lives on the seat entity, and the
 * pulses go through RedstoneRodSignalManager, which every signal path already reads.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineSeatBlock extends AbstractMachineBlock {

    /** Inside of the seat: walls 2 pixels thick, floor 4 pixels high (a cauldron's proportions). */
    private static final VoxelShape INSIDE = Block.box(2.0, 4.0, 2.0, 14.0, 16.0, 14.0);
    private static final VoxelShape SHAPE = Shapes.join(Shapes.block(), INSIDE, BooleanOp.ONLY_FIRST);

    public MachineSeatBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.SEAT;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return INSIDE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) {
            return super.use(state, level, pos, player, hand, hit); // the machine GUI
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        MachineSeatEntity seat = MachineSeatEntity.findAt(level, pos);
        if (seat != null && seat.isVehicle()) return InteractionResult.CONSUME; // someone is sitting
        if (seat == null) {
            seat = MachineSeatEntity.create(level, pos);
            level.addFreshEntity(seat);
        }
        player.startRiding(seat);
        return InteractionResult.CONSUME;
    }
}
