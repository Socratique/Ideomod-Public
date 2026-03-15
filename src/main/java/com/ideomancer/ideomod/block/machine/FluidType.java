package com.ideomancer.ideomod.block.machine;

import net.minecraft.util.StringRepresentable;

/**
 * Fluid state for the Machine Fluid Pipe blockstate property.
 *
 * Drives both the visual (texture swap via blockstate variants)
 * and the behavioral state (what fluid the pipe is holding).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public enum FluidType implements StringRepresentable {

    /** Empty bucket -- no fluid. */
    EMPTY("empty"),

    /** Water bucket. */
    WATER("water"),

    /** Lava bucket. */
    LAVA("lava");

    private final String name;

    FluidType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
