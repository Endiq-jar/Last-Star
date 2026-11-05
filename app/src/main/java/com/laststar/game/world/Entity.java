package com.laststar.game.world;

import com.laststar.game.core.Vec3;

/**
 * A moving thing in the world. Base class for the player, NPCs and enemies.
 *
 * <p>Kept free of Android types so the whole simulation can be exercised by the
 * desktop harness and the unit tests.</p>
 */
public class Entity {

    public Vec3 position;
    /** Facing angle in degrees; 0 looks toward +Z. */
    public float yaw;
    public float radius = 0.42f;
    public float height = 1.75f;
    public Vec3 velocity = Vec3.ZERO;
    public boolean onGround = true;
    /** Vertical speed, integrated by {@link World#integrateVertical}. */
    public float verticalSpeed;
    public boolean alive = true;
    public float health = 100f;
    public float maxHealth = 100f;
    /** Set for one frame when the entity takes damage, used by the renderer. */
    public float hitFlash;

    public Entity(Vec3 position) {
        this.position = position;
    }

    /** Centre of mass, approximately chest height. */
    public Vec3 centre() {
        return position.withY(position.y + height * 0.55f);
    }

    /** Eye/camera anchor. */
    public Vec3 eye() {
        return position.withY(position.y + height * 0.88f);
    }

    public float horizontalDistanceTo(Entity other) {
        return position.sub(other.position).horizontalLength();
    }

    public void damage(float amount) {
        if (!alive) {
            return;
        }
        health -= amount;
        hitFlash = 1f;
        if (health <= 0f) {
            health = 0f;
            alive = false;
        }
    }

    public void heal(float amount) {
        health = Math.min(maxHealth, health + amount);
        if (health > 0f) {
            alive = true;
        }
    }

    public float healthFraction() {
        return maxHealth <= 0f ? 0f : Math.max(0f, Math.min(1f, health / maxHealth));
    }

    /** Advance per-frame timers. */
    public void tickTimers(float dt) {
        if (hitFlash > 0f) {
            hitFlash = Math.max(0f, hitFlash - dt * 3.5f);
        }
    }
}
