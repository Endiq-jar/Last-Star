package com.laststar.game.world;

import com.laststar.game.core.Noise;
import com.laststar.game.core.Vec3;

/**
 * An enemy "Warden": chases the player, attacks in melee range and telegraphs
 * its swing so the fight is readable on a small screen.
 */
public final class Enemy extends Entity {

    public enum State {
        IDLE,
        CHASE,
        ATTACK,
        STAGGER,
        DEAD
    }

    /** Distance at which the Warden stops and starts swinging. */
    public static final float ATTACK_RANGE = 1.9f;
    /** Seconds between swings. */
    public static final float ATTACK_COOLDOWN = 1.25f;
    public static final float ATTACK_WINDUP = 0.35f;
    public static final float DAMAGE = 9f;
    public static final float MOVE_SPEED = 2.35f;
    public static final float AGGRO_RANGE = 24f;
    public static final float LEASH_RANGE = 52f;

    /**
     * The creature's name, from the region it was spawned in.
     *
     * <p>Hunts in the journal name a creature, so a kill has to be able to say
     * what it was. Regions stock different Hollow creatures, which is what
     * makes a hunt in Frostmere feel different from one in Suncrest.</p>
     */
    public String name = "HOLLOW WARDEN";

    public State state = State.IDLE;
    public float attackTimer;
    public float windupTimer;
    public boolean swingLanded;
    /** Seconds of stagger left; set when the player lands a hit. */
    public float staggerTimer;
    /**
     * Seconds of slowed movement left, from a spell that applies it.
     *
     * <p>Slowing is a different thing from staggering: a stagger stops the
     * attack, a slow makes it harder to close the distance. Spells in the
     * corpus use both, so the enemy tracks them separately.</p>
     */
    public float slowTimer;
    /** Animation phase, driven by movement speed. */
    public float animPhase;
    /** Bounds of the guard post; enemies drift back when too far away. */
    private final Vec3 spawn;

    public Enemy(Vec3 spawn, float health) {
        super(spawn);
        this.spawn = spawn;
        this.maxHealth = health;
        this.health = health;
        this.radius = 0.55f;
        this.height = 2.25f;
    }

    public Vec3 spawnPoint() {
        return spawn;
    }

    /**
     * Advances one enemy.
     *
     * @param dt        delta seconds
     * @param player    the player entity (may be dead)
     * @param world     world for terrain queries
     * @param onHit     invoked when a swing connects
     */
    public void update(float dt, Entity player, World world, HitListener onHit) {
        tickTimers(dt);
        if (slowTimer > 0f) {
            slowTimer = Math.max(0f, slowTimer - dt);
        }
        if (!alive) {
            state = State.DEAD;
            velocity = Vec3.ZERO;
            return;
        }

        if (staggerTimer > 0f) {
            staggerTimer -= dt;
            state = State.STAGGER;
            velocity = velocity.mul(0.82f);
            world.integrateVertical(this, dt);
            return;
        }

        float speedScale = slowTimer > 0f ? 0.45f : 1f;
        float dist = player.alive ? horizontalDistanceTo(player) : Float.MAX_VALUE;
        boolean playerInRange = dist < AGGRO_RANGE;
        boolean tooFarFromHome = position.distanceTo(spawn) > LEASH_RANGE;

        if (!player.alive || tooFarFromHome) {
            state = State.IDLE;
            moveToward(spawn, MOVE_SPEED * 0.55f, dt);
            maybeRegen(dt);
            return;
        }

        if (!playerInRange && state == State.IDLE) {
            velocity = Vec3.ZERO;
            return;
        }

        if (attackTimer > 0f) {
            attackTimer -= dt;
        }

        if (dist <= ATTACK_RANGE) {
            state = State.ATTACK;
            velocity = Vec3.ZERO;
            // Face the player so the swing reads correctly.
            face(player.position, dt, 12f);

            if (windupTimer > 0f) {
                windupTimer -= dt;
                if (windupTimer <= 0f && !swingLanded) {
                    swingLanded = true;
                    if (player.alive && horizontalDistanceTo(player) <= ATTACK_RANGE + 0.55f) {
                        onHit.onHit(this, player, DAMAGE);
                    }
                    attackTimer = ATTACK_COOLDOWN;
                }
            } else if (attackTimer <= 0f) {
                windupTimer = ATTACK_WINDUP;
                swingLanded = false;
            }
        } else {
            state = State.CHASE;
            moveToward(player.position, MOVE_SPEED * speedScale, dt);
        }
    }

    private void maybeRegen(float dt) {
        if (health < maxHealth) {
            health = Math.min(maxHealth, health + dt * 4f);
        }
    }

    private void moveToward(Vec3 target, float speed, float dt) {
        Vec3 delta = target.sub(position);
        Vec3 dir = delta.normalizedXZ();
        if (dir.lengthSq() < 1e-6f) {
            velocity = Vec3.ZERO;
            return;
        }
        // Stop just short of the target so enemies do not pile into one point.
        float stopDist = 1.4f;
        if (delta.horizontalLength() < stopDist) {
            velocity = Vec3.ZERO;
            return;
        }
        velocity = dir.mul(speed);
        position = position.addScaled(velocity, dt);
        face(position.add(dir), dt, 8f);
        animPhase += dt * speed * 2.2f;
    }

    private void face(Vec3 lookAt, float dt, float rate) {
        float wanted = lookAt.sub(position).headingDegrees();
        yaw = approachAngle(yaw, wanted, rate * dt);
    }

    /** Shortest-path angular interpolation in degrees. */
    public static float approachAngle(float current, float target, float maxStep) {
        float delta = target - current;
        while (delta > 180f) {
            delta -= 360f;
        }
        while (delta < -180f) {
            delta += 360f;
        }
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        return current + Math.signum(delta) * maxStep;
    }

    /** Telegraph amount 0..1, used by the renderer to flash before a swing. */
    public float windupFraction() {
        return state == State.ATTACK
                ? Noise.clamp01(1f - windupTimer / ATTACK_WINDUP)
                : 0f;
    }

    public interface HitListener {
        void onHit(Enemy source, Entity target, float damage);
    }
}
