package com.laststar.game.world;

import com.laststar.game.core.Vec3;

import java.util.Random;

/**
 * A short-lived particle rendered as an additive billboard. Also used for the
 * shard pickup burst and enemy death effect.
 */
public final class Projectile {

    public Vec3 position;
    public Vec3 velocity;
    public float life;
    public float maxLife;
    public float size;
    public int colour;

    public Projectile(Vec3 position, Vec3 velocity, float life, float size, int colour) {
        this.position = position;
        this.velocity = velocity;
        this.life = life;
        this.maxLife = life;
        this.size = size;
        this.colour = colour;
    }

    public void update(float dt) {
        velocity = velocity.withY(velocity.y - 9f * dt);
        position = position.addScaled(velocity, dt);
        life -= dt;
    }

    public float lifeFraction() {
        return maxLife <= 0f ? 0f : Math.max(0f, Math.min(1f, life / maxLife));
    }

    /** Radial burst of sparks with a randomised direction. */
    public static Projectile burst(Vec3 origin, Random rnd, int colour) {
        float theta = (float) (rnd.nextDouble() * Math.PI * 2.0);
        float up = 0.25f + (float) rnd.nextDouble() * 1.5f;
        float speed = 1.6f + (float) rnd.nextDouble() * 3.4f;
        Vec3 dir = new Vec3((float) Math.cos(theta), up, (float) Math.sin(theta)).normalized();
        float life = 0.45f + (float) rnd.nextDouble() * 0.7f;
        return new Projectile(origin, dir.mul(speed), life, 0.05f + (float) rnd.nextDouble() * 0.1f, colour);
    }
}
