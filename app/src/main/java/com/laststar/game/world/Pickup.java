package com.laststar.game.world;

import com.laststar.game.core.Vec3;

/** A health/energy pickup sitting on the ground. */
public final class Pickup {

    public enum Type {
        HEALTH("Health crystal", 35f),
        ENERGY("Stamina crystal", 0f);

        public final String label;
        public final float healAmount;

        Type(String label, float healAmount) {
            this.label = label;
            this.healAmount = healAmount;
        }
    }

    public final Vec3 position;
    public final Type type;
    public float spin;
    public float bob;

    public Pickup(Vec3 position, Type type) {
        this.position = position;
        this.type = type;
    }

    public float bobOffset() {
        return (float) Math.sin(bob) * 0.11f;
    }
}
