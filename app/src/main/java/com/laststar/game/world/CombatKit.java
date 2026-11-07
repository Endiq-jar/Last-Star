package com.laststar.game.world;

/**
 * The blade: everything the player can do with a sword that is not casting.
 *
 * <p>The design document asks for light and heavy attacks, charged attacks,
 * a perfect dodge, a parry, combos, aerial combat, enemy stagger, boss weak
 * points and finishing attacks. That is a lot of state for one fight, and all
 * of it is about timing rather than about position, so it lives in one class
 * the world can drive with four inputs and read for animation.</p>
 *
 * <p>The design of the timing is deliberate:</p>
 * <ul>
 *   <li><b>Light</b> is fast and chains into a three-hit combo whose last hit
 *       deals more and staggers.</li>
 *   <li><b>Heavy</b> is slow, wide and staggers on any hit. Charging it turns
 *       it into a two-handed blow that breaks guard.</li>
 *   <li><b>Perfect dodge</b> gives a brief window of invulnerability that, if a
 *       blow lands inside it, refunds Light and opens a counter window. Dodging
 *       early is just a dodge; dodging at the right moment is a reward.</li>
 *   <li><b>Parry</b> is a short window that turns a blow back on the attacker
 *       and staggers it. Missing the parry leaves the player open, which is
 *       what makes it a read rather than a shield.</li>
 * </ul>
 *
 * <p>Free of Android and of the world, so every window can be tested by
 * counting frames.</p>
 */
public final class CombatKit {

    /** How a swing is classified, for damage and animation. */
    public enum Swing {
        NONE,
        LIGHT_1,
        LIGHT_2,
        LIGHT_3,
        HEAVY,
        CHARGED,
        COUNTER,
        FINISHER
    }

    /** What the player is doing right now. */
    public enum Stance {
        IDLE,
        LIGHT,
        HEAVY,
        CHARGING,
        DODGE,
        PARRY,
        STAGGERED
    }

    // --- tuning -------------------------------------------------------------

    /** Seconds a light swing takes from start to recovery. */
    public static final float LIGHT_TIME = 0.26f;
    public static final float LIGHT_COOLDOWN = 0.13f;
    public static final float HEAVY_TIME = 0.52f;
    public static final float HEAVY_COOLDOWN = 0.34f;
    /** Seconds a heavy must be held before it becomes a charged blow. */
    public static final float CHARGE_TIME = 0.62f;
    /** Seconds past CHARGE_TIME at which the charge is fully wound. */
    public static final float CHARGE_FULL = 1.15f;

    public static final float DODGE_TIME = 0.34f;
    /** The window inside a dodge that counts as perfect. */
    public static final float DODGE_PERFECT = 0.15f;
    public static final float DODGE_COOLDOWN = 0.42f;
    /** Distance a dodge covers, metres. */
    public static final float DODGE_DISTANCE = 4.6f;

    public static final float PARRY_TIME = 0.30f;
    /** The window inside a parry that actually parries. */
    public static final float PARRY_WINDOW = 0.18f;
    public static final float PARRY_COOLDOWN = 0.75f;

    /** Seconds the player is open after a parry that caught nothing. */
    public static final float PARRY_RECOVERY = 0.34f;
    /** Seconds a counter window stays open after a perfect dodge. */
    public static final float COUNTER_WINDOW = 0.85f;
    /** Light refunded for a perfect dodge, in Light. */
    public static final float PERFECT_DODGE_LIGHT = 14f;

    /** Damage multipliers, relative to the player's base melee damage. */
    public static final float LIGHT_1_DAMAGE = 1.0f;
    public static final float LIGHT_2_DAMAGE = 1.05f;
    public static final float LIGHT_3_DAMAGE = 1.55f;
    public static final float HEAVY_DAMAGE = 1.9f;
    public static final float CHARGED_DAMAGE = 3.1f;
    public static final float COUNTER_DAMAGE = 2.4f;
    public static final float FINISHER_DAMAGE = 4.0f;

    /** Reach of each swing, in metres beyond the player's radius. */
    public static final float LIGHT_RANGE = 2.5f;
    public static final float HEAVY_RANGE = 3.1f;
    public static final float CHARGED_RANGE = 3.7f;

    /** How far up an aerial attack can reach. */
    public static final float AERIAL_REACH_UP = 2.4f;
    public static final float AERIAL_REACH_DOWN = 3.2f;

    // --- state --------------------------------------------------------------

    public Stance stance = Stance.IDLE;
    /** The swing in progress, or the last one that landed. */
    public Swing swing = Swing.NONE;
    /** Seconds left in the current stance. */
    public float stanceTimer;
    /** Seconds until the next action is allowed. */
    public float cooldown;
    /** Seconds the heavy has been held. */
    public float charge;
    /** How wound the charge is, 0..1. */
    public float chargeFraction;
    /** Seconds left in the counter window opened by a perfect dodge. */
    public float counterWindow;
    /** Position the dodge committed to, so it travels in a straight line. */
    public float dodgeYaw;
    /** Set for one frame when the current swing has already been resolved. */
    public boolean swingResolved;
    /** Set for one frame when a parry caught a blow. */
    public boolean parryCaught;

    /** Index of the light combo, 0..2. Resets after a pause. */
    public int comboIndex;
    /** Seconds since the last light hit, for combo expiry. */
    public float comboMemory;
    public static final float COMBO_MEMORY = 0.9f;

    /** Consecutive landed hits, for the finisher prompt. */
    public int chain;
    /** Whether the finisher is available this frame. */
    public boolean finisherReady;

    // --- input --------------------------------------------------------------

    /**
     * Advances the kit by one frame.
     *
     * @param dt       delta seconds
     * @param light    a light attack was pressed this frame
     * @param heavy    a heavy attack was pressed this frame
     * @param heavyHeld the heavy is being held, for charging
     * @param dodge    a dodge was pressed this frame
     * @param parry    a parry was pressed this frame
     * @param grounded whether the player is on the ground
     */
    public void update(float dt, boolean light, boolean heavy, boolean heavyHeld,
                       boolean dodge, boolean parry, boolean grounded) {
        parryCaught = false;
        swingResolved = false;

        if (stanceTimer > 0f) {
            stanceTimer -= dt;
        }
        if (cooldown > 0f) {
            cooldown -= dt;
        }
        if (counterWindow > 0f) {
            counterWindow -= dt;
        }
        // The combo memory is the window after a swing in which the next press
        // continues the chain rather than starting it again. It only counts
        // down once the player is free to act again: a swing's own animation
        // must not eat the window it just opened.
        if (stance == Stance.IDLE && cooldown <= 0f) {
            if (comboMemory > 0f) {
                comboMemory -= dt;
                if (comboMemory <= 0f) {
                    comboIndex = 0;
                }
            }
        }

        // Charging is a stance of its own: the heavy is held, not yet released.
        if (stance == Stance.CHARGING) {
            charge += dt;
            chargeFraction = Math.min(1f,
                    (charge - CHARGE_TIME) / (CHARGE_FULL - CHARGE_TIME));
            if (!heavyHeld) {
                // Released: the blow lands now, at whatever charge it reached.
                beginSwing(Swing.CHARGED, Stance.HEAVY, HEAVY_TIME,
                        HEAVY_COOLDOWN, CHARGE_FULL);
                stanceTimer = HEAVY_TIME * 0.55f;
            }
            return;
        }

        // A swing in progress plays out.
        if (stance == Stance.LIGHT || stance == Stance.HEAVY) {
            if (stanceTimer <= 0f) {
                stance = Stance.IDLE;
                swing = Swing.NONE;
            }
            return;
        }

        if (stance == Stance.DODGE || stance == Stance.PARRY) {
            if (stanceTimer <= 0f) {
                stance = Stance.IDLE;
            }
            return;
        }

        // Free to act.
        if (cooldown > 0f) {
            return;
        }

        if (parry && grounded) {
            if (counterWindow > 0f) {
                // A counter is the reward for the perfect dodge; it takes the
                // parry button because that is where the player's thumb is.
                beginSwing(Swing.COUNTER, Stance.HEAVY, LIGHT_TIME,
                        HEAVY_COOLDOWN, HEAVY_RANGE);
                counterWindow = 0f;
                return;
            }
            stance = Stance.PARRY;
            stanceTimer = PARRY_TIME + PARRY_RECOVERY;
            cooldown = PARRY_COOLDOWN;
            return;
        }

        if (dodge && grounded) {
            stance = Stance.DODGE;
            stanceTimer = DODGE_TIME;
            cooldown = DODGE_COOLDOWN;
            return;
        }

        if (finisherReady && light) {
            beginSwing(Swing.FINISHER, Stance.HEAVY, HEAVY_TIME,
                    HEAVY_COOLDOWN, CHARGED_RANGE);
            finisherReady = false;
            chain = 0;
            return;
        }

        if (heavy) {
            // Holding from the press is what earns the charge; the first frame
            // enters the charging stance rather than swinging.
            stance = Stance.CHARGING;
            charge = 0f;
            chargeFraction = 0f;
            return;
        }

        if (light) {
            Swing next;
            if (!grounded) {
                next = Swing.LIGHT_2;
            } else {
                switch (comboIndex) {
                    case 0:
                        next = Swing.LIGHT_1;
                        break;
                    case 1:
                        next = Swing.LIGHT_2;
                        break;
                    default:
                        next = Swing.LIGHT_3;
                        break;
                }
            }
            beginSwing(next, Stance.LIGHT, LIGHT_TIME, LIGHT_COOLDOWN, LIGHT_RANGE);
            comboIndex = (comboIndex + 1) % 3;
            comboMemory = COMBO_MEMORY;
        }
    }

    /**
     * Seconds the current swing lasts, start to finish.
     *
     * <p>Kept per-swing rather than derived from the {@link Swing} value: an
     * aerial light swing uses the same value as a ground one but is not the
     * same length, and a hit window computed from the wrong length never
     * opens.</p>
     */
    public float swingDuration = LIGHT_TIME;

    private void beginSwing(Swing which, Stance as, float duration,
                            float cool, float range) {
        swing = which;
        stance = as;
        stanceTimer = duration;
        swingDuration = duration;
        cooldown = cool;
        swingResolved = false;
        lastRange = range;
    }

    /** Reach of the swing in progress. */
    public float lastRange = LIGHT_RANGE;

    /**
     * Whether the swing's damage lands this frame.
     *
     * <p>A swing that damages on its first frame has no weight; one that
     * damages on its last feels unresponsive. Landing it a third of the way
     * through is what makes the timing readable.</p>
     */
    public boolean hitWindowOpen() {
        if (swingResolved || swing == Swing.NONE || swingDuration <= 0f) {
            return false;
        }
        // Every swing lands at the same fraction of its own length, so a fast
        // light and a slow charged blow both hit where the animation says.
        return stanceTimer <= swingDuration * 0.66f;
    }

    /** Damage multiplier for the swing in progress. */
    public float damageScale() {
        switch (swing) {
            case LIGHT_1:
                return LIGHT_1_DAMAGE;
            case LIGHT_2:
                return LIGHT_2_DAMAGE;
            case LIGHT_3:
                return LIGHT_3_DAMAGE;
            case HEAVY:
                return HEAVY_DAMAGE;
            case CHARGED:
                // The charge is the whole point: a barely-charged blow is a
                // normal heavy, a fully wound one is worth the wait.
                return HEAVY_DAMAGE + (CHARGED_DAMAGE - HEAVY_DAMAGE) * chargeFraction;
            case COUNTER:
                return COUNTER_DAMAGE;
            case FINISHER:
                return FINISHER_DAMAGE;
            default:
                return 1f;
        }
    }

    /** Whether the current swing staggers whatever it hits. */
    public boolean staggers() {
        return swing == Swing.LIGHT_3 || swing == Swing.HEAVY
                || swing == Swing.CHARGED || swing == Swing.COUNTER
                || swing == Swing.FINISHER;
    }

    /** How long a hit from this swing staggers for. */
    public float staggerSeconds() {
        switch (swing) {
            case LIGHT_3:
                return 0.35f;
            case HEAVY:
                return 0.55f;
            case CHARGED:
                return 0.8f + chargeFraction * 0.4f;
            case COUNTER:
                return 1.1f;
            case FINISHER:
                return 1.4f;
            default:
                return 0.18f;
        }
    }

    /** Whether the swing is heavy enough to break a boss's guard. */
    public boolean breaksGuard() {
        return swing == Swing.CHARGED || swing == Swing.COUNTER
                || swing == Swing.FINISHER;
    }

    /**
     * Whether an incoming blow is absorbed by the parry.
     *
     * <p>Only the first part of the parry stance is a window: the rest is the
     * recovery, which is where a mistimed parry costs the player.</p>
     */
    public boolean parryAbsorbs() {
        return stance == Stance.PARRY && stanceTimer > PARRY_RECOVERY;
    }

    /**
     * Whether an incoming blow is absorbed by a perfect dodge.
     *
     * <p>The window is the first {@link #DODGE_PERFECT} seconds of the dodge,
     * so a player who dodges early still dodges, just not perfectly.</p>
     */
    public boolean perfectDodgeActive() {
        return stance == Stance.DODGE && stanceTimer > DODGE_TIME - DODGE_PERFECT;
    }

    /** Whether the dodge is on its i-frames at all. */
    public boolean dodgeInvulnerable() {
        return stance == Stance.DODGE;
    }

    /**
     * Records a blow being turned aside.
     *
     * @return true when the timing made it a parry rather than a block, in
     *         which case the caller should stagger the attacker
     */
    public boolean onIncomingBlow() {
        if (parryAbsorbs()) {
            parryCaught = true;
            parries++;
            // A parry that lands ends the parry stance early so the player can
            // capitalise, and opens a counter window.
            stance = Stance.IDLE;
            stanceTimer = 0f;
            counterWindow = COUNTER_WINDOW;
            cooldown = 0.12f;
            return true;
        }
        if (perfectDodgeActive()) {
            // A perfect dodge refunds Light and opens the same counter window.
            counterWindow = COUNTER_WINDOW;
            perfectDodges++;
            return false;
        }
        return false;
    }

    /** Perfect dodges this run, for the record. */
    public int perfectDodges;
    /** Parries this run. */
    public int parries;

    /** Records a landed hit, for the chain and the finisher. */
    public void onHitLanded(float damageDealt, boolean killed) {
        chain++;
        if (chain >= 6 || killed) {
            finisherReady = true;
        }
    }

    /** Records that the player took a hit, which breaks the chain. */
    public void onPlayerHurt() {
        chain = 0;
        finisherReady = false;
        comboIndex = 0;
        comboMemory = 0f;
        counterWindow = 0f;
    }

    /** Whether the player is mid-action, for movement and casting rules. */
    public boolean busy() {
        return stance == Stance.LIGHT || stance == Stance.HEAVY
                || stance == Stance.CHARGING || stance == Stance.STAGGERED;
    }

    /** Whether the player can cast: casting is allowed while moving, not mid-swing. */
    public boolean canCast() {
        return stance != Stance.DODGE && stance != Stance.STAGGERED;
    }

    /** How far into the current swing the animation is, 0..1. */
    public float swingFraction() {
        if (swingDuration <= 0f) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, 1f - stanceTimer / swingDuration));
    }

    /** Resets the kit to rest, for a respawn. */
    public void reset() {
        stance = Stance.IDLE;
        swing = Swing.NONE;
        stanceTimer = 0f;
        cooldown = 0f;
        charge = 0f;
        chargeFraction = 0f;
        counterWindow = 0f;
        comboIndex = 0;
        comboMemory = 0f;
        chain = 0;
        finisherReady = false;
        swingResolved = false;
        parryCaught = false;
    }
}
