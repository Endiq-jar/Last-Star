package com.laststar.game.world;

import com.laststar.game.core.Vec3;

/** A collectible star shard floating above the terrain. */
public final class Shard {

    public final Vec3 position;
    public final int index;
    public boolean collected;
    public float spin;
    public float bob;

    public Shard(Vec3 position, int index) {
        this.position = position;
        this.index = index;
    }

    /** Render height including the float bob, relative to the base position. */
    public float bobOffset() {
        return (float) Math.sin(bob) * 0.14f;
    }
}
