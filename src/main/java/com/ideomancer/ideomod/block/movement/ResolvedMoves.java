package com.ideomancer.ideomod.block.movement;

import net.minecraft.world.level.block.piston.PistonStructureResolver;

import javax.annotation.Nullable;

/**
 * The piston structure resolver that most recently succeeded on this thread.
 *
 * Vanilla's PistonBaseBlock.moveBlocks builds a resolver, resolves it, and then asks it for the
 * blocks to move. PistonStructureResolverMixin records each successful resolver here, and the
 * piston mixins read it at the moment moveBlocks asks for that list: so they save machine data
 * (server) and capture framed-block render data (client) from vanilla's own answer, instead of
 * building and resolving a second resolver of their own on every piston move.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public final class ResolvedMoves {

    private static final ThreadLocal<PistonStructureResolver> LAST = new ThreadLocal<>();

    private ResolvedMoves() {}

    public static void record(PistonStructureResolver resolver) {
        LAST.set(resolver);
    }

    /** The resolver that last succeeded on this thread, cleared as it is taken. */
    @Nullable
    public static PistonStructureResolver take() {
        PistonStructureResolver resolver = LAST.get();
        LAST.remove();
        return resolver;
    }
}
