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
    /** Cosmetics chosen in the wardrobe: an offset into the palette below. */
    public int skinTone = 3;
    /** Robe tint, mirrored from the wardrobe's hair/cosmetic swatches. */
    public float[] robeColour = {0.16f, 0.44f, 0.47f};

    /** Skin palette shared with the wardrobe screen. */
    private static final float[][] SKIN = {
            {0.949f, 0.898f, 0.831f}, {0.953f, 0.851f, 0.776f}, {0.937f, 0.784f, 0.667f},
            {0.910f, 0.729f, 0.573f},
            {0.749f, 0.545f, 0.373f}, {0.667f, 0.443f, 0.290f}, {0.596f, 0.365f, 0.235f},
            {0.541f, 0.318f, 0.180f},
            {0.478f, 0.239f, 0.157f}, {0.400f, 0.196f, 0.145f}, {0.298f, 0.169f, 0.129f},
            {0.227f, 0.137f, 0.106f},
            {0.235f, 0.180f, 0.145f}, {0.435f, 0.835f, 0.784f}, {0.180f, 0.639f, 0.667f},
            {0.353f, 0.545f, 0.851f}
    };

    /** The selected skin colour as {r, g, b}, clamped to the palette. */
    public float[] skinColour() {
        int i = skinTone < 0 ? 0 : (skinTone >= SKIN.length ? SKIN.length - 1 : skinTone);
        return SKIN[i];
    }

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
