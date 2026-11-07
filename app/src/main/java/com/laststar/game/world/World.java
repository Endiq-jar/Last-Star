package com.laststar.game.world;

import com.laststar.game.core.Noise;
import com.laststar.game.core.Terrain;
import com.laststar.game.world.lore.Elaris;
import com.laststar.game.world.lore.QuestCorpus;
import com.laststar.game.world.lore.SpellCorpus;
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

    /**
     * A page of the corpus lying in the world, waiting to be found.
     *
     * <p>The design document ties new spells to exploration rather than to a
     * shop. A lost Wander on the ground is the literal form of that: walk to it
     * and the page wakes in the book.</p>
     */
    public static final class FoundPage {
        public final Vec3 position;
        /** Index into {@link SpellCorpus#SPELLS}. */
        public final int spell;
        public float spin;

        public FoundPage(Vec3 position, int spell) {
            this.position = position;
            this.spell = spell;
        }
    }
    /** Radius within which a shard is collected. */
    public static final float SHARD_PICKUP_RADIUS = 1.55f;
    /** Time the player may stay in water before taking damage, in seconds. */
    public static final float WATER_SAFE_TIME = 4.5f;

    /**
     * The height field. Not final: travelling to another settlement rebuilds it
     * from that region's own parameters, and everything that reads it reads it
     * through this field.
     */
    public Terrain terrain;
    public final Player player;
    public final List<Enemy> enemies = new ArrayList<>();
    public final List<Shard> shards = new ArrayList<>();
    public final List<Pickup> pickups = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    /** The spell-book and the player's Light. */
    public final SpellBook spells = new SpellBook();
    /** The journal, and the thing that advances it. */
    public final QuestLog quests = new QuestLog();
    /** Which of the fourteen settlements the player is standing in. */
    public int regionIndex;
    /** Coin, from quests and kills. */
    public int gold;
    /** Runs finished, carried across sessions. */
    public int runsCompleted;
    /** Pages lying in the world waiting to be found, as spell indices. */
    public final List<FoundPage> lostPages = new ArrayList<FoundPage>();
    /** Spin clock for a found page, so the renderer can animate it. */
    public float wanderSpin;

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

    /** Region the world is currently built for. */
    private Elaris.Region region = Elaris.REGIONS[0];

    public World(long seed) {
        this.worldSeed = (int) seed;
        this.rng = new Random(seed);
        this.regionIndex = 0;
        this.terrain = new Terrain(220f, 110, seed);
        this.player = new Player(spawnPoint());
        this.player.yaw = 180f;
        quests.refreshAvailability();
        buildLevel(1);
    }

    /** The settlement this world is built for. */
    public Elaris.Region region() {
        return region;
    }

    /**
     * Rebuilds the world for a different settlement.
     *
     * <p>Terrain is regenerated with the region's own parameters and the level
     * is rebuilt, which is what makes fourteen places feel like fourteen places
     * rather than one field with different names. Progress in the book and the
     * journal is untouched: travelling does not cost the player anything.</p>
     */
    public void enterRegion(int index) {
        if (index < 0 || index >= Elaris.REGION_COUNT) {
            return;
        }
        regionIndex = index;
        region = Elaris.regions()[index];
        Terrain next = Terrain.forRegion(region, 220f, 110, worldSeed + index * 7919L);
        // Terrain is final, so the region swap rebuilds through the field's
        // holder rather than assigning. See the terrain field's declaration.
        terrain = next;
        player.respawn(spawnPoint());
        buildLevel(level());
        quests.report(QuestCorpus.Kind.TRAVEL, region.name, 1);
        showMessage(region.name + " -- " + region.identity, 2.8f, 0.6f);
    }

    /** Adds coin, from a kill, a quest or a find. */
    public void addGold(int amount) {
        gold = Math.max(0, gold + amount);
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
            Enemy enemy = new Enemy(anchor, hp);
            // Regions stock their own Hollow creatures, so what a hunt names is
            // decided by where the player is standing.
            if (region.creatures.length > 0) {
                enemy.name = region.creatures[i % region.creatures.length];
            }
            enemies.add(enemy);
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

        // Scatter the region's lost pages. A page lying in the world is how the
        // document says new magic should be found -- by exploring, not by
        // shopping -- so each region hides the spells that belong to it.
        spawnLostPages();

        shardsCollected = 0;
    }

    /**
     * Puts this region's discoverable spells on the ground.
     *
     * <p>Only pages the player does not already own are placed, so a second
     * visit to a region does not litter it with books the book already holds.
     * The search spirals outward so finds are spread across the map rather than
     * clustered where the player lands.</p>
     */
    private void spawnLostPages() {
        lostPages.clear();
        int[] found = SpellCorpus.inRegion(regionIndex);
        Random r = new Random(worldSeed + regionIndex * 3119L + level());
        for (int i = 0; i < found.length; i++) {
            int spell = found[i];
            if (spells.pages[spell].awake) {
                continue;
            }
            Vec3 spot = findOpenGround(terrain, r, 18f, 96f);
            if (spot != null) {
                lostPages.add(new FoundPage(spot, spell));
            }
        }
    }

    /** Random open dry ground between two radii, or null. */
    private static Vec3 findOpenGround(Terrain terrain, Random r,
                                       float minRadius, float maxRadius) {
        for (int i = 0; i < 220; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            float d = minRadius + r.nextFloat() * (maxRadius - minRadius);
            float x = (float) Math.cos(a) * d;
            float z = (float) Math.sin(a) * d;
            float h = terrain.heightAt(x, z);
            if (h < terrain.waterLevel + 0.8f || terrain.slopeAt(x, z) > 0.45f) {
                continue;
            }
            return new Vec3(x, h + 0.30f, z);
        }
        return null;
    }

    /**
     * Spiral search for open dry ground for the Wander.
     *
     * <p>Falls back to the spawn point so the book can always be found even on
     * a seed where every candidate is rejected; losing it would mean losing
     * the spell system.</p>
     */
    private static Vec3 findWanderSpot(Terrain terrain) {
        for (float radius = 9f; radius < 44f; radius += 1.5f) {
            int steps = 16 + (int) (radius * 2f);
            for (int i = 0; i < steps; i++) {
                double a = i * Math.PI * 2.0 / steps;
                float x = (float) Math.cos(a) * radius;
                float z = (float) Math.sin(a) * radius;
                float h = terrain.heightAt(x, z);
                if (h < terrain.waterLevel + 1.1f) {
                    continue;
                }
                if (terrain.slopeAt(x, z) > 0.34f) {
                    continue;
                }
                return new Vec3(x, h + 0.30f, z);
            }
        }
        return new Vec3(0f, terrain.heightAt(0f, 0f) + 0.30f, 0f);
    }

    public int level() {
        return level;
    }

    /**
     * Restores the level number from a save.
     *
     * <p>Deliberately does not rebuild the level: the save also restores the
     * player's exact position, and respawning into a freshly built level would
     * move them. The level is rebuilt on the next run.
     */
    void restoreLevel(int restored) {
        level = Math.max(1, restored);
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
        // The book is always open: Light recovers and mastery creeps up during
        // the intro card and after a death too, not only while fighting.
        spells.tick(dt);
        wanderSpin += dt * 45f;

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
        handleCasting(dt, input);
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

    /**
     * Collects the Wander when walked into, and resolves casts.
     *
     * <p>Spells resolve here rather than inside a spell class because hitting
     * something is a world question: which enemies are near, what is in front,
     * what staggers. The Wander owns cost and mastery; the World owns the
     * consequences.</p>
     */
    private void handleCasting(float dt, InputState in) {
        // Lost pages lying in the world. Walking near one wakes it.
        for (int i = lostPages.size() - 1; i >= 0; i--) {
            FoundPage page = lostPages.get(i);
            page.spin += dt * 60f;
            Vec3 to = page.position.sub(player.position);
            if (to.horizontalLength() < 1.9f && Math.abs(to.y) < 2.4f) {
                lostPages.remove(i);
                if (spells.awaken(page.spell)) {
                    showMessage("THE WANDER REMEMBERS: " + spells.pages[page.spell].def.name,
                            2.8f, 0.7f);
                    pickupFlash = 1f;
                    shakeAmount = Math.min(1f, shakeAmount + 0.2f);
                    quests.report(QuestCorpus.Kind.RECOVER_SPELL,
                            spells.pages[page.spell].def.name, 1);
                }
            }
        }
        if (in.castSlot < 0) {
            return;
        }
        SpellBook.Page page = spells.cast(in.castSlot);
        if (page != null) {
            resolveCast(page, in.castSlot);
        }
    }

    /** Applies a cast spell's effect to the world. */
    /**
     * Applies a cast page's effect to the world.
     *
     * <p>The corpus describes a spell by its shape and by the traits its mastery
     * has switched on. Both matter: a Bolt with Explodes is a different spell
     * from a Bolt without it, and the same code resolves every spell in the
     * corpus because the shape decides how it lands and the traits decide what
     * it leaves behind.</p>
     */
    private void resolveCast(SpellBook.Page page, int slot) {
        SpellCorpus.Def spell = page.def;
        Vec3 facing = new Vec3((float) Math.sin(Math.toRadians(player.yaw)), 0f,
                (float) Math.cos(Math.toRadians(player.yaw)));
        Vec3 origin = player.centre();
        int hits = 0;

        switch (spell.shape) {
            case WARD:
                hits = castWard(page);
                break;

            case SWEEP:
                hits = castSweep(page, facing, origin);
                break;

            case BOLT:
                hits = castBolt(page, facing);
                break;

            case STARFALL:
                hits = castStarfall(page, facing);
                break;

            case FIELD:
                hits = castField(page, origin);
                break;

            case DASH:
                hits = castDash(page, facing);
                break;

            case BEAM:
                hits = castBeam(page, facing);
                break;

            case TRAP:
                hits = castTrap(page, facing);
                break;

            case SUMMON:
                hits = castSummon(page);
                break;

            default:
                break;
        }

        if (hits > 0) {
            hitStop = 0.05f;
            shakeAmount = Math.min(1f, shakeAmount
                    + (spell.shape == SpellCorpus.Shape.STARFALL ? 0.5f : 0.24f));
            // Experience for landing the spell, scaled by how many it caught, so
            // accuracy is what levels a page rather than casting into the air.
            page.awardXp(6f + hits * 2f);
        }
        if (page.has(SpellCorpus.Trait.CONVERTS_TO_LIGHT)) {
            spells.light = Math.min(spells.lightCapacity, spells.light + 6f + hits * 2f);
        }
        if (page.has(SpellCorpus.Trait.HEALS_CASTER)) {
            player.heal(2f + hits * 1.5f);
        }
        if (page.has(SpellCorpus.Trait.SHIELDS_CASTER)) {
            player.invulnerability = Math.max(player.invulnerability, 1.6f);
        }
    }

    /** A held defence: time bought rather than damage dealt. */
    private int castWard(SpellBook.Page page) {
        SpellCorpus.Def spell = page.def;
        float seconds = 1.8f + page.level * 0.05f;
        player.invulnerability = Math.max(player.invulnerability, seconds);
        showMessage(spell.name + " -- WARDED", 1.0f, 0.35f);
        // A ward that reflects is a weapon: whatever is already on top of the
        // player takes the damage the ward would have absorbed.
        if (page.has(SpellCorpus.Trait.REFLECTS) && spell.baseDamage > 0f) {
            int hits = 0;
            for (int i = 0; i < enemies.size(); i++) {
                Enemy e = enemies.get(i);
                if (!e.alive) {
                    continue;
                }
                if (e.centre().sub(player.centre()).horizontalLength() < 4f) {
                    damageEnemy(e, page.damage());
                    applyTraits(page, e);
                    hits++;
                }
            }
            return hits;
        }
        return 0;
    }

    /** A cut centred on the caster, wide enough to work while surrounded. */
    private int castSweep(SpellBook.Page page, Vec3 facing, Vec3 origin) {
        SpellCorpus.Def spell = page.def;
        int hits = 0;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (!e.alive) {
                continue;
            }
            Vec3 to = e.centre().sub(origin);
            float dist = to.horizontalLength();
            if (dist > spell.range + e.radius) {
                continue;
            }
            if (dist > 0.001f) {
                Vec3 flat = new Vec3(to.x, 0f, to.z).normalized();
                if (flat.dot(facing) < -0.10f) {
                    continue;
                }
            }
            damageEnemy(e, page.damage());
            applyTraits(page, e);
            hits++;
        }
        return hits;
    }

    /** A single travelling strike at the first thing in front. */
    private int castBolt(SpellBook.Page page, Vec3 facing) {
        SpellCorpus.Def spell = page.def;
        int hits = 0;
        Enemy target = nearestEnemyInFront(facing, spell.range, 0.42f);
        while (target != null && hits < 4) {
            damageEnemy(target, page.damage());
            applyTraits(page, target);
            hits++;
            // Piercing bolts carry on to the next foe behind the first.
            if (!page.has(SpellCorpus.Trait.PIERCES)) {
                break;
            }
            target = nearestEnemyInFront(facing, spell.range, 0.42f, hits);
        }
        if (hits > 0 && page.has(SpellCorpus.Trait.SPLITS_INTO_THREE)) {
            // A split bolt finds two more targets anywhere nearby.
            int extra = 0;
            for (int i = 0; i < enemies.size() && extra < 2; i++) {
                Enemy e = enemies.get(i);
                if (!e.alive || e == target) {
                    continue;
                }
                if (e.centre().sub(player.centre()).horizontalLength() <= spell.range) {
                    damageEnemy(e, page.damage() * 0.6f);
                    applyTraits(page, e);
                    extra++;
                }
            }
            hits += extra;
        }
        return hits;
    }

    /** A heavy strike from above, that staggers and can execute. */
    private int castStarfall(SpellBook.Page page, Vec3 facing) {
        SpellCorpus.Def spell = page.def;
        int hits = 0;
        for (int n = 0; n < 3; n++) {
            Enemy target = nearestEnemyInFront(facing, spell.range, 0.10f, hits);
            if (target == null) {
                break;
            }
            damageEnemy(target, page.damage());
            applyTraits(page, target);
            hits++;
        }
        return hits;
    }

    /** A field left on the ground that keeps working. */
    private int castField(SpellBook.Page page, Vec3 origin) {
        SpellCorpus.Def spell = page.def;
        int hits = 0;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (!e.alive) {
                continue;
            }
            if (e.centre().sub(origin).horizontalLength() > spell.range + e.radius) {
                continue;
            }
            damageEnemy(e, page.damage());
            applyTraits(page, e);
            hits++;
        }
        // A field is persistent, so it keeps working for a while: modelled as a
        // lingering slow on everything it caught.
        if (hits > 0) {
            showMessage(spell.name, 0.9f, 0.3f);
        }
        return hits;
    }

    /** A dash that damages what it passes through. */
    private int castDash(SpellBook.Page page, Vec3 facing) {
        SpellCorpus.Def spell = page.def;
        Vec3 from = player.position;
        float distance = Math.min(spell.range, 12f);
        Vec3 to = player.position.add(facing.mul(distance));
        to = to.withY(groundHeight(to.x, to.z));
        player.position = to;
        // The corridor between where the player was and where they now are.
        int hits = 0;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (!e.alive) {
                continue;
            }
            if (distanceToSegment(e.position, from, to) < 2.6f + e.radius) {
                damageEnemy(e, page.damage());
                applyTraits(page, e);
                hits++;
            }
        }
        return hits;
    }

    /** A beam that holds on one target and everything in the line. */
    private int castBeam(SpellBook.Page page, Vec3 facing) {
        SpellCorpus.Def spell = page.def;
        Vec3 origin = player.centre();
        int hits = 0;
        int limit = page.has(SpellCorpus.Trait.SPLITS_INTO_THREE) ? 3 : 1;
        for (int i = 0; i < enemies.size() && hits < limit; i++) {
            Enemy e = enemies.get(i);
            if (!e.alive) {
                continue;
            }
            Vec3 to = e.centre().sub(origin);
            float dist = to.horizontalLength();
            if (dist > spell.range) {
                continue;
            }
            Vec3 flat = new Vec3(to.x, 0f, to.z).normalized();
            if (flat.dot(facing) < 0.94f && limit == 1) {
                continue;
            }
            if (flat.dot(facing) < 0.72f) {
                continue;
            }
            damageEnemy(e, page.damage());
            applyTraits(page, e);
            hits++;
        }
        return hits;
    }

    /** A trap that arms where it lands and catches what comes. */
    private int castTrap(SpellBook.Page page, Vec3 facing) {
        SpellCorpus.Def spell = page.def;
        Vec3 at = player.position.add(facing.mul(Math.min(spell.range, 10f)));
        int hits = 0;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (!e.alive) {
                continue;
            }
            if (e.centre().sub(at).horizontalLength() < 4.5f) {
                damageEnemy(e, page.damage());
                applyTraits(page, e);
                hits++;
            }
        }
        return hits;
    }

    /** A summoned ally: modelled as a burst of help from beside the player. */
    private int castSummon(SpellBook.Page page) {
        SpellCorpus.Def spell = page.def;
        int hits = 0;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (!e.alive) {
                continue;
            }
            if (e.centre().sub(player.centre()).horizontalLength() > spell.range) {
                continue;
            }
            damageEnemy(e, page.damage());
            applyTraits(page, e);
            hits++;
            if (hits >= 2) {
                break;
            }
        }
        if (page.has(SpellCorpus.Trait.SHIELDS_CASTER)) {
            player.invulnerability = Math.max(player.invulnerability, 2.0f);
        }
        showMessage(spell.name + " ANSWERS", 1.2f, 0.35f);
        return hits;
    }

    /** Applies whatever secondary behaviours a page's mastery has unlocked. */
    private void applyTraits(SpellBook.Page page, Enemy e) {
        if (!e.alive) {
            return;
        }
        if (page.has(SpellCorpus.Trait.STAGGERS_TARGET)) {
            e.staggerTimer = Math.max(e.staggerTimer, 0.5f);
        }
        if (page.has(SpellCorpus.Trait.SLOWS_TARGET)) {
            e.slowTimer = Math.max(e.slowTimer, 1.6f);
        }
        if (page.has(SpellCorpus.Trait.EXECUTES_WOUNDED)
                && e.health < e.maxHealth * 0.3f) {
            damageEnemy(e, e.health);
        }
        if (page.has(SpellCorpus.Trait.EXPLODES_ON_IMPACT)) {
            // Splash: everything close to the target takes a share.
            for (int i = 0; i < enemies.size(); i++) {
                Enemy other = enemies.get(i);
                if (!other.alive || other == e) {
                    continue;
                }
                if (other.centre().sub(e.centre()).horizontalLength() < 3.2f) {
                    other.damage(page.damage() * 0.35f);
                }
            }
        }
        if (page.has(SpellCorpus.Trait.CHAINS_TO_NEARBY)) {
            // One extra hop, which is what makes a chain spell feel like one.
            Enemy nearest = null;
            float best = Float.MAX_VALUE;
            for (int i = 0; i < enemies.size(); i++) {
                Enemy other = enemies.get(i);
                if (!other.alive || other == e) {
                    continue;
                }
                float d = other.centre().sub(e.centre()).horizontalLength();
                if (d < 7f && d < best) {
                    best = d;
                    nearest = other;
                }
            }
            if (nearest != null) {
                nearest.damage(page.damage() * 0.5f);
            }
        }
    }

    /** Shortest distance from a point to a line segment, flattened to XZ. */
    private static float distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        float abx = b.x - a.x;
        float abz = b.z - a.z;
        float apx = p.x - a.x;
        float apz = p.z - a.z;
        float len2 = abx * abx + abz * abz;
        float t = len2 <= 0.0001f ? 0f : (apx * abx + apz * abz) / len2;
        t = Math.max(0f, Math.min(1f, t));
        float dx = apx - abx * t;
        float dz = apz - abz * t;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    /** Damages an enemy and does the kill bookkeeping in one place. */
    private void damageEnemy(Enemy e, float amount) {
        if (!e.alive) {
            return;
        }
        e.damage(amount);
        if (!e.alive) {
            onEnemyKilled(e);
        }
    }

    /**
     * Everything that happens when a Hollow creature dies.
     *
     * <p>One place, because a kill should count the same whether it came from
     * the blade or from a spell, and because the quest bookkeeping is easy to
     * forget on a new damage route.</p>
     */
    private void onEnemyKilled(Enemy e) {
        kills++;
        addGold(3 + level());
        spawnDeathBurst(e.position, e.height);
        showMessage("HOLLOW DOWN", 1.1f, 0.3f);
        java.util.List<QuestCorpus.Def> finished =
                quests.report(QuestCorpus.Kind.SLAY, e.name, 1);
        payOut(finished);
    }

    /**
     * Grants a finished quest's rewards.
     *
     * <p>The document is specific that side quests must reward something worth
     * having rather than only currency, so a spell reward wakes a page, a Light
     * reward raises the ceiling, and the rest pay coin.</p>
     */
    private void payOut(java.util.List<QuestCorpus.Def> finished) {
        for (int i = 0; i < finished.size(); i++) {
            QuestCorpus.Def d = finished.get(i);
            addGold(d.gold);
            switch (d.reward) {
                case SPELL:
                    if (d.rewardSpell >= 0 && d.rewardSpell < spells.pages.length) {
                        spells.awaken(d.rewardSpell);
                    }
                    break;
                case LIGHT_CAPACITY:
                    spells.raiseCapacity(d.gold);
                    break;
                default:
                    break;
            }
            showMessage("QUEST COMPLETE: " + d.title, 3.0f, 0.8f);
        }
    }

    /**
     * Closest living enemy inside a cone in front of the player.
     *
     * @param minDot how tightly the cone hugs the facing direction; 1 is a
     *               laser, 0 is the whole front half
     */
    private Enemy nearestEnemyInFront(Vec3 facing, float range, float minDot) {
        return nearestEnemyInFront(facing, range, minDot, 0);
    }

    /**
     * As {@link #nearestEnemyInFront}, skipping the nearest {@code skip} of
     * them. Piercing bolts and multi-target strikes walk the list this way.
     */
    private Enemy nearestEnemyInFront(Vec3 facing, float range, float minDot, int skip) {
        Enemy best = null;
        float bestDist = Float.MAX_VALUE;
        Vec3 origin = player.centre();
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (!e.alive) {
                continue;
            }
            Vec3 to = e.centre().sub(origin);
            float dist = to.length();
            if (dist > range + e.radius || dist < 0.001f) {
                continue;
            }
            Vec3 flat = new Vec3(to.x, 0f, to.z).normalized();
            if (flat.dot(facing) < minDot) {
                continue;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = e;
            }
        }
        return best;
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
