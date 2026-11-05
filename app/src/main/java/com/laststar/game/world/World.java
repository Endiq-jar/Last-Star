package com.laststar.game.world;

import com.laststar.game.core.Noise;
import com.laststar.game.core.Terrain;
import com.laststar.game.core.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The authoritative game simulation: terrain, player, enemies, the star shards
 * that must be collected, and all collision/combat rules.
 *
 * <p>Pure Java by design. Rendering, sound and input live in
 * {@code com.laststar.game.gl} / {@code .ui} / {@code .audio} and only read the
 * state published here.</p>
 */
public final class World {

    public enum Phase {
        /** Intro fade-in, player cannot move yet. */
        INTRO,
        /** Normal gameplay. */
        PLAYING,
        /** All shards collected: escape/celebrate. */
        VICTORY,
        /** Player health reached zero: respawn countdown. */
        DEFEAT,
        /** Player confirmed a new run after victory. */
        COMPLETE
    }

    public static final int SHARD_COUNT = 6;
    /** Radius within which a shard is collected. */
    public static final float SHARD_PICKUP_RADIUS = 1.55f;
    /** Time the player may stay in water before taking damage, in seconds. */
    public static final float WATER_SAFE_TIME = 4.5f;

    public final Terrain terrain;
    public final Player player;
    public final List<Enemy> enemies = new ArrayList<>();
    public final List<Shard> shards = new ArrayList<>();
    public final List<Pickup> pickups = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();

    public Phase phase = Phase.INTRO;
    public float phaseTimer;
    public float worldTime;
    public int shardsCollected;
    public int kills;
    public int deaths;
    public float waterTime;
    public float messageTimer;
    public String message = "";
    public float messagePriority;

    // Simple 0..1 screen-effect signals consumed by the renderer.
    public float pickupFlash;
    public float victoryFlash;
    public float shakeAmount;
    public float hitStop;

    public final int worldSeed;
    private final Random rng;
    private float respawnTimer;
    private int level;

    /** Stat hooks so the HUD/tests can observe outcomes without polling. */
    public EventListener events = new EventListener() {
        @Override
        public void onMessage(String text, float seconds) {
        }

        @Override
        public void onShard(int collected, int total) {
        }

        @Override
        public void onDefeat() {
        }

        @Override
        public void onVictory() {
        }
    };

    /**
     * 0 = easy, 1 = normal, 2 = hard. Set before {@link #beginRun()} to tune the
     * run's starting health and sprint stamina.
     */
    public int difficulty = 1;

    public World(long seed) {
        this.worldSeed = (int) seed;
        this.rng = new Random(seed);
        this.terrain = new Terrain(220f, 110, seed);
        this.player = new Player(spawnPoint());
        this.player.yaw = 180f;
        buildLevel(1);
    }

    /** Starts a fresh run at level 1, applying the current difficulty. */
    public void beginRun() {
        level = 0;
        shardsCollected = 0;
        deaths = 0;
        kills = 0;
        waterTime = 0f;
        victoryFlash = 0f;
        player.respawn(spawnPoint());
        applyDifficulty();
        buildLevel(1);
        phase = Phase.INTRO;
        phaseTimer = 0f;
    }

    /** Scales the player's starting resources to the chosen difficulty. */
    public void applyDifficulty() {
        float health;
        float stamina;
        switch (difficulty) {
            case 0:
                health = 140f;
                stamina = 1.35f;
                break;
            case 2:
                health = 80f;
                stamina = 0.8f;
                break;
            case 1:
            default:
                health = 100f;
                stamina = 1f;
                break;
        }
        player.maxHealth = health;
        player.health = health;
        player.sprintStamina = stamina;
    }

    public Vec3 spawnPoint() {
        return new Vec3(0f, terrain.heightAt(0f, 0f) + 0.05f, 0f);
    }

    // ------------------------------------------------------------------
    // Level construction
    // ------------------------------------------------------------------

    private void buildLevel(int levelIndex) {
        this.level = levelIndex;
        enemies.clear();
        shards.clear();
        pickups.clear();
        projectiles.clear();

        Random r = new Random(worldSeed * 31L + levelIndex * 7919L);

        // Shards are spread around the map on reachable ground.
        List<Vec3> spots = new ArrayList<>();
        int guard = 0;
        while (shards.size() < SHARD_COUNT && guard++ < 4000) {
            float angle = (float) (r.nextDouble() * Math.PI * 2.0);
            float dist = 24f + (float) r.nextDouble() * 62f;
            float x = (float) Math.cos(angle) * dist;
            float z = (float) Math.sin(angle) * dist;
            float h = terrain.heightAt(x, z);
            if (h < terrain.waterLevel + 0.6f) {
                continue;
            }
            if (terrain.slopeAt(x, z) > 0.55f) {
                continue;
            }
            boolean tooClose = false;
            for (Vec3 s : spots) {
                if (s.sub(new Vec3(x, h, z)).horizontalLength() < 14f) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }
            Vec3 p = new Vec3(x, h + 1.15f, z);
            spots.add(new Vec3(x, h, z));
            shards.add(new Shard(p, shards.size()));
        }

        // Ensure exactly SHARD_COUNT even on pathological seeds.
        while (shards.size() < SHARD_COUNT) {
            float angle = (float) (shards.size() * Math.PI * 0.7);
            float dist = 18f + shards.size() * 9f;
            float x = (float) Math.cos(angle) * dist;
            float z = (float) Math.sin(angle) * dist;
            float h = terrain.heightAt(x, z);
            shards.add(new Shard(new Vec3(x, h + 1.15f, z), shards.size()));
        }

        // Enemies guard each shard plus a few roamers.
        int enemyCount = 5 + levelIndex * 2;
        for (int i = 0; i < enemyCount; i++) {
            Vec3 anchor;
            if (i < shards.size()) {
                Vec3 s = shards.get(i).position;
                float a = (float) (r.nextDouble() * Math.PI * 2.0);
                float d = 3.5f + (float) r.nextDouble() * 4f;
                anchor = new Vec3(s.x + (float) Math.cos(a) * d, 0f, s.z + (float) Math.sin(a) * d);
            } else {
                float a = (float) (r.nextDouble() * Math.PI * 2.0);
                float d = 30f + (float) r.nextDouble() * 45f;
                anchor = new Vec3((float) Math.cos(a) * d, 0f, (float) Math.sin(a) * d);
            }
            anchor = anchor.withY(terrain.heightAt(anchor.x, anchor.z) + 0.05f);
            float hp = 60f + (float) r.nextDouble() * 25f + levelIndex * 14f;
            enemies.add(new Enemy(anchor, hp));
        }

        // Health pickups scattered near spawn and shards.
        for (int i = 0; i < 4; i++) {
            float a = (float) (r.nextDouble() * Math.PI * 2.0);
            float d = 8f + (float) r.nextDouble() * 60f;
            float x = (float) Math.cos(a) * d;
            float z = (float) Math.sin(a) * d;
            if (terrain.heightAt(x, z) < terrain.waterLevel + 0.4f) {
                continue;
            }
            pickups.add(new Pickup(new Vec3(x, terrain.heightAt(x, z) + 0.85f, z), Pickup.Type.HEALTH));
        }

        shardsCollected = 0;
    }

    public int level() {
        return level;
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    /**
     * Advances the world by {@code dt} seconds.
     *
     * @param input player intent for this frame
     */
    public void update(float dt, InputState input) {
        if (hitStop > 0f) {
            hitStop = Math.max(0f, hitStop - dt);
            dt *= 0.15f;
        }
        worldTime += dt;
        phaseTimer += dt;

        if (messageTimer > 0f) {
            messageTimer -= dt;
            if (messageTimer <= 0f) {
                message = "";
                messagePriority = 0f;
            }
        }
        if (pickupFlash > 0f) {
            pickupFlash = Math.max(0f, pickupFlash - dt * 2.4f);
        }
        if (victoryFlash > 0f) {
            victoryFlash = Math.max(0f, victoryFlash - dt * 0.8f);
        }
        if (shakeAmount > 0f) {
            shakeAmount = Math.max(0f, shakeAmount - dt * 2.2f);
        }

        switch (phase) {
            case INTRO:
                player.yaw = Enemy.approachAngle(player.yaw, 180f, dt * 60f);
                updateEnvironment(dt, input);
                if (phaseTimer > 2.2f) {
                    phase = Phase.PLAYING;
                    showMessage("Collect the 6 star shards", 3.5f, 1f);
                }
                return;
            case DEFEAT:
                respawnTimer -= dt;
                updateEnvironment(dt, input);
                if (respawnTimer <= 0f) {
                    player.respawn(spawnPoint());
                    phase = Phase.PLAYING;
                    phaseTimer = 0f;
                    showMessage("Rise again, Last Star", 2.2f, 1f);
                }
                return;
            case VICTORY:
                updateEnvironment(dt, input);
                if (phaseTimer > 4.0f || input.interactPressed) {
                    phase = Phase.COMPLETE;
                    phaseTimer = 0f;
                    events.onVictory();
                }
                return;
            case COMPLETE:
                return;
            case PLAYING:
            default:
                break;
        }

        player.tick(dt);
        handleMovement(dt, input);
        handleCombat(dt, input);
        updateEnemies(dt);
        updateShards(dt);
        updatePickups(dt);
        updateEnvironment(dt, input);
        updateProjectiles(dt);

        checkDefeat();
    }

    private void handleMovement(float dt, InputState in) {
        // Camera-relative movement. The camera yaw is supplied by the camera.
        float yawRad = (float) Math.toRadians(in.cameraYaw);
        float sin = (float) Math.sin(yawRad);
        float cos = (float) Math.cos(yawRad);

        float ax = in.moveX;
        float ay = in.moveY;
        float mag = (float) Math.sqrt(ax * ax + ay * ay);
        if (mag > 1f) {
            ax /= mag;
            ay /= mag;
            mag = 1f;
        }

        // forward = (sin, cos) and right = up x forward = (cos, 0, -sin)
        float dx = sin * ay + cos * ax;
        float dz = cos * ay - sin * ax;
        Vec3 wish = new Vec3(dx, 0f, dz);

        boolean wantSprint = in.sprintHeld && mag > 0.35f && player.sprintStamina > 0.05f;
        player.sprinting = wantSprint;
        float speed = wantSprint ? Player.SPRINT_SPEED : Player.WALK_SPEED;

        if (wantSprint) {
            player.sprintStamina = Math.max(0f, player.sprintStamina - dt * 0.28f);
        } else {
            player.sprintStamina = Math.min(1f, player.sprintStamina + dt * 0.42f);
        }

        // Slow down in water and on steep slopes.
        float wx = player.position.x;
        float wz = player.position.z;
        float ground = terrain.heightAt(wx, wz);
        if (ground < terrain.waterLevel) {
            speed *= 0.55f;
        }
        float slope = terrain.slopeAt(wx, wz);
        if (slope > 0.35f) {
            speed *= 1f - Math.min(0.45f, (slope - 0.35f) * 1.2f);
        }

        Vec3 targetVel = wish.mul(speed);
        // Smooth acceleration; higher value = snappier.
        float accel = player.onGround ? 14f : 5f;
        float k = 1f - (float) Math.exp(-accel * dt);
        player.velocity = player.velocity.lerp(targetVel, k);

        player.position = player.position.add(new Vec3(player.velocity.x * dt, 0f, player.velocity.z * dt));

        // Keep player inside world bounds.
        float half = terrain.size * 0.5f - 3f;
        player.position = player.position.clampXZ(half);

        if (mag > 0.12f) {
            float wantedYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            player.yaw = Enemy.approachAngle(player.yaw, wantedYaw, dt * 620f);
            player.animPhase += dt * (2.2f + player.velocity.horizontalLength() * 0.85f);
        } else {
            player.animPhase += dt * 0.6f;
        }

        // Jump / gravity.
        if (in.jumpPressed && player.onGround) {
            player.verticalSpeed = Player.JUMP_SPEED;
            player.onGround = false;
        }
        integrateVertical(player, dt);
    }

    /** Applies gravity and snaps the entity to the ground. */
    public void integrateVertical(Entity e, float dt) {
        float ground = groundHeight(e.position.x, e.position.z);
        e.verticalSpeed += Player.GRAVITY * dt;
        e.position = e.position.withY(e.position.y + e.verticalSpeed * dt);

        if (e.position.y <= ground) {
            e.position = e.position.withY(ground);
            if (e.verticalSpeed < 0f) {
                e.verticalSpeed = 0f;
            }
            e.onGround = true;
        } else if (e.position.y > ground + 0.02f) {
            e.onGround = false;
        }
    }

    /** Ground height including a small allowance so the model's feet sit on the terrain. */
    public float groundHeight(float x, float z) {
        return terrain.heightAt(x, z);
    }

    private void handleCombat(float dt, InputState in) {
        if (!player.canAttack()) {
            return;
        }
        if (in.attackPressed || (in.attackHeld && player.attackCooldown <= 0.12f && player.attackAnim <= 0f)) {
            // require an edge for held-fire so taps feel deterministic
            if (!in.attackPressed && !in.attackHeld) {
                return;
            }
            player.startAttack();
            shakeAmount = Math.min(1f, shakeAmount + 0.18f);
            resolveMeleeHit();
        }
    }

    /** Damages every enemy inside the melee cone. */
    public int resolveMeleeHit() {
        int hits = 0;
        Vec3 origin = player.centre();
        Vec3 facing = new Vec3((float) Math.sin(Math.toRadians(player.yaw)), 0f,
                (float) Math.cos(Math.toRadians(player.yaw)));
        for (Enemy e : enemies) {
            if (!e.alive) {
                continue;
            }
            Vec3 toEnemy = e.centre().sub(origin);
            if (toEnemy.length() > Player.MELEE_RANGE + e.radius) {
                continue;
            }
            Vec3 flat = toEnemy.normalizedXZ();
            float dot = flat.dot(facing);
            float angle = (float) Math.toDegrees(Math.acos(Noise.clamp(dot, -1f, 1f)));
            if (angle > Player.MELEE_ARC_DEGREES * 0.5f) {
                continue;
            }
            e.damage(Player.MELEE_DAMAGE);
            e.staggerTimer = 0.18f;
            hits++;
            if (!e.alive) {
                kills++;
                spawnDeathBurst(e.position, e.height);
                showMessage("Warden down", 1.1f, 0.3f);
            }
        }
        if (hits > 0) {
            hitStop = 0.055f;
            shakeAmount = Math.min(1f, shakeAmount + 0.42f);
        }
        return hits;
    }

    private void updateEnemies(float dt) {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            e.update(dt, player, this, enemyHitListener);
            float half = terrain.size * 0.5f - 4f;
            e.position = e.position.clampXZ(half);
        }
    }

    /** Anonymous inner class instead of a method reference: avoids needing
     *  java.lang.invoke.LambdaMetafactory, which android.jar does not ship. */
    private final Enemy.HitListener enemyHitListener = new Enemy.HitListener() {
        @Override
        public void onHit(Enemy source, Entity target, float damage) {
            onEnemyHit(source, target, damage);
        }
    };

    private void onEnemyHit(Enemy source, Entity target, float damage) {
        if (target == player) {
            player.damage(damage);
            shakeAmount = Math.min(1f, shakeAmount + 0.55f);
            if (!player.alive) {
                onPlayerDeath();
            }
        }
    }

    private void updateShards(float dt) {
        for (Shard s : shards) {
            s.spin += dt * 55f;
            s.bob += dt * 2.1f;
            if (s.collected) {
                continue;
            }
            Vec3 toPlayer = player.centre().sub(s.position);
            if (toPlayer.length() < SHARD_PICKUP_RADIUS + player.radius) {
                s.collected = true;
                shardsCollected++;
                pickupFlash = 1f;
                shakeAmount = Math.min(1f, shakeAmount + 0.25f);
                for (int i = 0; i < 26; i++) {
                    projectiles.add(Projectile.burst(s.position, rng, 0xFFB7F0FF));
                }
                showMessage("Shard " + shardsCollected + " / " + SHARD_COUNT, 1.8f, 0.6f);
                events.onShard(shardsCollected, SHARD_COUNT);
                if (shardsCollected >= SHARD_COUNT) {
                    triggerVictory();
                } else if (shardsCollected % 2 == 0) {
                    player.heal(18f);
                    player.maxHealth += 6f;
                }
            }
        }
    }

    private void updatePickups(float dt) {
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup p = pickups.get(i);
            p.spin += dt * 40f;
            p.bob += dt * 1.8f;
            if (player.centre().sub(p.position).length() < 1.4f) {
                player.heal(p.type.healAmount);
                pickupFlash = Math.max(pickupFlash, 0.7f);
                showMessage(p.type.label, 1.4f, 0.4f);
                pickups.remove(i);
            }
        }
    }

    private void updateEnvironment(float dt, InputState in) {
        float ground = terrain.heightAt(player.position.x, player.position.z);
        boolean inWater = ground < terrain.waterLevel && player.position.y < terrain.waterLevel + 0.6f;
        if (inWater && phase == Phase.PLAYING) {
            waterTime += dt;
            if (waterTime > WATER_SAFE_TIME) {
                player.damage(6f * dt * 4f);
                if (!player.alive) {
                    onPlayerDeath();
                }
            }
        } else {
            waterTime = Math.max(0f, waterTime - dt * 2f);
        }
    }

    private void updateProjectiles(float dt) {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            p.update(dt);
            float ground = terrain.heightAt(p.position.x, p.position.z);
            if (p.life <= 0f || p.position.y < ground - 0.2f) {
                projectiles.remove(i);
            }
        }
    }

    private void spawnDeathBurst(Vec3 at, float height) {
        for (int i = 0; i < 22; i++) {
            projectiles.add(Projectile.burst(at.withY(at.y + height * 0.5f), rng, 0xFFFF6A5A));
        }
    }

    private void checkDefeat() {
        if (!player.alive && phase == Phase.PLAYING) {
            onPlayerDeath();
        }
    }

    private void onPlayerDeath() {
        if (phase == Phase.DEFEAT) {
            return;
        }
        deaths++;
        phase = Phase.DEFEAT;
        phaseTimer = 0f;
        respawnTimer = 2.6f;
        shakeAmount = 1f;
        showMessage("You fell...", 2.4f, 1f);
        events.onDefeat();
    }

    private void triggerVictory() {
        phase = Phase.VICTORY;
        phaseTimer = 0f;
        victoryFlash = 1f;
        showMessage("All shards restored", 3f, 1f);
    }

    /** Starts a fresh, harder run on the same terrain seed. */
    public void restart() {
        deaths = 0;
        kills = 0;
        player.respawn(spawnPoint());
        player.maxHealth += 20f;
        player.health = player.maxHealth;
        player.sprintStamina = 1f;
        buildLevel(level + 1);
        phase = Phase.PLAYING;
        phaseTimer = 0f;
        victoryFlash = 0f;
        waterTime = 0f;
        showMessage("Level " + level, 2.2f, 1f);
    }

    public void showMessage(String text, float seconds, float priority) {
        if (messageTimer > 0f && priority < messagePriority) {
            return;
        }
        message = text;
        messageTimer = seconds;
        messagePriority = priority;
        events.onMessage(text, seconds);
    }

    /** Number of shards still out there. */
    public int shardsRemaining() {
        return SHARD_COUNT - shardsCollected;
    }

    /** Nearest living enemy distance, used by the HUD threat indicator. */
    public float nearestEnemyDistance() {
        float best = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.alive) {
                best = Math.min(best, e.position.distanceTo(player.position));
            }
        }
        return best;
    }

    public int aliveEnemies() {
        int n = 0;
        for (Enemy e : enemies) {
            if (e.alive) {
                n++;
            }
        }
        return n;
    }

    public interface EventListener {
        void onMessage(String text, float seconds);

        void onShard(int collected, int total);

        void onDefeat();

        void onVictory();
    }
}
