package com.laststar.game.world;

import com.laststar.game.core.Vec3;

/** The Last Star: movement, jump and melee state. */
public final class Player extends Entity {

    public static final float WALK_SPEED = 5.4f;
    public static final float SPRINT_SPEED = 7.6f;
    public static final float JUMP_SPEED = 6.4f;
    public static final float GRAVITY = -18.5f;
    public static final float ATTACK_COOLDOWN = 0.42f;
    public static final float ATTACK_DURATION = 0.26f;
    public static final float MELEE_RANGE = 2.5f;
    public static final float MELEE_DAMAGE = 24f;
    public static final float MELEE_ARC_DEGREES = 110f;

    public float attackCooldown;
    public float attackAnim;
    public int combo;
    public float sprintStamina = 1f;
    public boolean sprinting;
    public float animPhase;
    public float invulnerability;
    public float breathe;

    public Player(Vec3 position) {
        super(position);
        this.radius = 0.4f;
        this.height = 1.72f;
        this.maxHealth = 120f;
        this.health = 120f;
    }

    public void respawn(Vec3 at) {
        position = at;
        velocity = Vec3.ZERO;
        verticalSpeed = 0f;
        health = maxHealth;
        alive = true;
        attackCooldown = 0f;
        attackAnim = 0f;
        invulnerability = 1.2f;
        hitFlash = 0f;
    }

    public boolean canAttack() {
        return attackCooldown <= 0f && alive;
    }

    public void startAttack() {
        attackCooldown = ATTACK_COOLDOWN;
        attackAnim = ATTACK_DURATION;
        combo = (combo + 1) % 3;
    }

    /** 0..1 progress through the current swing, or 0 when idle. */
    public float attackFraction() {
        return attackAnim <= 0f ? 0f : 1f - (attackAnim / ATTACK_DURATION);
    }

    /** Normalised damage-taken amount, drives the red screen flash. */
    public float damageFlash;

    @Override
    public void damage(float amount) {
        if (invulnerability > 0f || !alive) {
            return;
        }
        super.damage(amount);
        damageFlash = Math.min(1f, damageFlash + amount / 30f);
        invulnerability = 0.55f;
    }

    public void tick(float dt) {
        tickTimers(dt);
        if (attackCooldown > 0f) {
            attackCooldown -= dt;
        }
        if (attackAnim > 0f) {
            attackAnim -= dt;
        }
        if (invulnerability > 0f) {
            invulnerability -= dt;
        }
        if (damageFlash > 0f) {
            damageFlash = Math.max(0f, damageFlash - dt * 1.8f);
        }
        breathe += dt;
    }
}
