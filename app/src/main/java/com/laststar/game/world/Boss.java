package com.laststar.game.world;

import com.laststar.game.core.Vec3;

/**
 * A Hollow boss: four phases at most, each one a new fight.
 *
 * <p>The document is specific that a phase must introduce a new attack pattern
 * or a new arena mechanic, not simply more health. So a phase here is a
 * behaviour change with a name, a telegraph and a rule, and the boss's health
 * bar is only the clock that moves it from one to the next.</p>
 *
 * <p>Bosses also carry a weak point. Striking it does several times the damage
 * of a normal hit and staggers the boss, which is what turns a fight from
 * attrition into a read.</p>
 */
public final class Boss {

    /** What the boss is doing. */
    public enum Phase {
        /** Settling in: slow swings, the player learns the telegraph. */
        FIRST,
        /** Adds a second pattern, or opens the arena. */
        SECOND,
        /** The arena changes: hazards, a closing floor, light that fades. */
        THIRD,
        /** Everything at once, and the weak point finally exposed. */
        FINAL
    }

    /** A single attack a boss can throw. */
    public enum Attack {
        /** A wide swing at whoever is close. */
        SWEEP,
        /** A lunge that covers ground and leaves the boss open behind. */
        LUNGE,
        /** A ground slam that sends out a ring of force. */
        SLAM,
        /** A volley of Hollow light at range. */
        VOLLEY,
        /** A summon: the phase's creatures join in. */
        CALL,
        /** The arena itself attacks: falling light, closing walls, dark water. */
        ARENA
    }

    public final String name;
    public final int regionIndex;

    public float health;
    public float maxHealth;
    public int level;
    /** Compact stance state: what it is winding up and how far through. */
    public Attack currentAttack = Attack.SWEEP;
    public float windup;
    public float windupLength = 0.6f;
    public float cooldown;
    public float staggerTimer;
    public boolean alive = true;
    /** Set for one frame when an attack should resolve. */
    public boolean resolvePending;

    /** The weak point, as an offset from the body's centre. */
    public Vec3 weakPointOffset = new Vec3(0f, 1.5f, 0.4f);
    /** How long the weak point stays open per phase. */
    public float weakPointWindow;
    /** Seconds the weak point is exposed, counting down. */
    public float weakPointOpen;
    /** Strikes to the weak point needed before it staggers. */
    public int weakPointStrikes;
    /** Does the boss have a guard that only heavy blows break? */
    public boolean guarded;

    /** The phase, derived from health fraction. */
    public Phase phase;

    /** Seconds since the fight began, for the arena mechanics. */
    public float fightTime;
    /** Rotates through the current phase's patterns. */
    private int attackCursor;

    // --- phase tuning -------------------------------------------------------

    static final float PHASE_2_AT = 0.70f;
    static final float PHASE_3_AT = 0.42f;
    static final float PHASE_4_AT = 0.18f;

    /** Base health by region level; the fight should last, not drag. */
    public static float healthFor(int level) {
        return 340f + level * 46f;
    }

    public Boss(String name, int regionIndex, int level) {
        this.name = name;
        this.regionIndex = regionIndex;
        this.level = Math.max(1, level);
        this.maxHealth = healthFor(this.level);
        this.health = maxHealth;
        enterPhase(Phase.FIRST);
    }

    /** Which phase a health fraction falls in. */
    public static Phase phaseFor(float fraction) {
        if (fraction <= PHASE_4_AT) {
            return Phase.FINAL;
        }
        if (fraction <= PHASE_3_AT) {
            return Phase.THIRD;
        }
        if (fraction <= PHASE_2_AT) {
            return Phase.SECOND;
        }
        return Phase.FIRST;
    }

    /** The one-line rule that changes when this phase begins. */
    public static String ruleFor(Phase p) {
        switch (p) {
            case FIRST:
                return "IT IS TESTING YOU.";
            case SECOND:
                return "TWO HANDS NOW.";
            case THIRD:
                return "THE GROUND ANSWERS IT.";
            case FINAL:
                return "NOTHING LEFT BUT THE WEAK POINT.";
            default:
                return "";
        }
    }

    /** How many attacks the boss can choose from in this phase. */
    public int patternCount() {
        switch (phase) {
            case FIRST:
                return 2;
            case SECOND:
                return 3;
            case THIRD:
                return 4;
            case FINAL:
                return 5;
            default:
                return 1;
        }
    }

    /**
     * The attacks available in this phase.
     *
     * <p>Public because the fight's variety is a property worth asserting and
     * worth showing: a phase that adds no pattern has not changed the fight.</p>
     */
    public Attack[] patterns() {
        switch (phase) {
            case FIRST:
                return new Attack[]{Attack.SWEEP, Attack.LUNGE};
            case SECOND:
                return new Attack[]{Attack.LUNGE, Attack.SLAM, Attack.SWEEP};
            case THIRD:
                return new Attack[]{Attack.SLAM, Attack.VOLLEY, Attack.ARENA, Attack.LUNGE};
            case FINAL:
            default:
                return new Attack[]{Attack.SLAM, Attack.VOLLEY, Attack.ARENA,
                        Attack.CALL, Attack.LUNGE};
        }
    }

    /** Picks the attack for this phase, cycling so patterns stay readable. */
    private Attack pickAttack() {
        Attack[] list = patterns();
        // A rotating counter rather than a function of time: time-based picks
        // can land on the same index twice and, over a short fight, skip a
        // pattern entirely. A counter guarantees every one is seen.
        attackCursor = (attackCursor + 1) % list.length;
        return list[attackCursor];
    }

    /** Telegraph lengths, so the player can read the wind-up. */
    private float windupFor(Attack a) {
        switch (a) {
            case SWEEP:
                return 0.55f;
            case LUNGE:
                return 0.75f;
            case SLAM:
                return 0.9f;
            case VOLLEY:
                return 0.65f;
            case CALL:
                return 1.1f;
            case ARENA:
            default:
                return 0.8f;
        }
    }

    private float cooldownFor(Attack a) {
        switch (a) {
            case SWEEP:
                return 0.9f;
            case LUNGE:
                return 1.1f;
            case SLAM:
                return 1.3f;
            case VOLLEY:
                return 1.0f;
            case CALL:
                return 1.6f;
            case ARENA:
            default:
                return 1.5f;
        }
    }

    /**
     * Advances the boss.
     *
     * @param dt       delta seconds
     * @param distance horizontal distance to the player
     */
    public void update(float dt, float distance) {
        if (!alive) {
            return;
        }
        fightTime += dt;
        resolvePending = false;

        // The phase is checked before anything else, and deliberately so: a
        // phase change staggers the boss, so a check that ran after the
        // stagger test would be swallowed by the stagger the previous phase
        // change caused, and the fight would stall a phase behind.
        Phase next = phaseFor(health / maxHealth);
        if (next != phase) {
            enterPhase(next);
        }

        if (staggerTimer > 0f) {
            staggerTimer -= dt;
            return;
        }
        if (weakPointOpen > 0f) {
            weakPointOpen -= dt;
        }

        if (cooldown > 0f) {
            cooldown -= dt;
            return;
        }
        if (windup > 0f) {
            windup -= dt;
            if (windup <= 0f) {
                // The attack lands here; the world reads resolvePending.
                resolvePending = true;
                cooldown = cooldownFor(currentAttack);
            }
            return;
        }
        // Out of range of everything but a volley: close the distance first.
        if (distance > 22f) {
            return;
        }
        currentAttack = pickAttack();
        windup = windupFor(currentAttack);
        windupLength = windup;
    }

    private void enterPhase(Phase next) {
        phase = next;
        // Each phase changes the fight rather than only the numbers.
        switch (next) {
            case FIRST:
                guarded = false;
                weakPointWindow = 1.4f;
                break;
            case SECOND:
                // The guard goes up: only a charged, countering or finishing
                // blow gets through it.
                guarded = true;
                weakPointWindow = 1.5f;
                break;
            case THIRD:
                // The arena starts attacking, and the weak point is briefer.
                guarded = true;
                weakPointWindow = 1.2f;
                break;
            case FINAL:
                // The guard drops but the weak point is permanently exposed:
                // the fight becomes a race rather than a siege.
                guarded = false;
                weakPointWindow = 3.0f;
                weakPointOpen = weakPointWindow;
                break;
            default:
                break;
        }
        // A phase change is a moment of pause, which is what makes it read as
        // an event rather than a number changing.
        windup = 0f;
        cooldown = 0.9f;
        staggerTimer = 0.6f;
    }

    /**
     * Applies damage, honouring the guard and the weak point.
     *
     * @param amount      the incoming damage
     * @param hitWeakPoint whether the blow landed on the weak point
     * @param breaksGuard whether the blow is heavy enough to beat the guard
     * @return the damage actually dealt, for the damage numbers
     */
    public float damage(float amount, boolean hitWeakPoint, boolean breaksGuard) {
        if (!alive) {
            return 0f;
        }
        float dealt = amount;
        if (guarded) {
            // A guarded boss shrugs off anything that is not a committed blow.
            dealt = breaksGuard ? amount : amount * 0.15f;
        }
        if (hitWeakPoint && weakPointOpen > 0f) {
            // The weak point is worth the read: several times the damage, and
            // it counts toward the stagger.
            dealt *= 3.5f;
            weakPointStrikes++;
            if (weakPointStrikes >= 3) {
                weakPointStrikes = 0;
                staggerTimer = 1.6f;
                weakPointOpen = 0f;
            }
        }
        health = Math.max(0f, health - dealt);
        if (health <= 0f) {
            alive = false;
        }
        return dealt;
    }

    /** Opens the weak point, after a heavy attack or a phase change. */
    public void exposeWeakPoint() {
        if (!alive) {
            return;
        }
        weakPointOpen = weakPointWindow;
    }

    /** Whether the weak point can be struck right now. */
    public boolean weakPointExposed() {
        return alive && weakPointOpen > 0f;
    }

    /** 0..1 health, for the bar. */
    public float fraction() {
        return maxHealth <= 0f ? 0f : health / maxHealth;
    }

    /** 0..1 wind-up progress, for the telegraph. */
    public float windupFraction() {
        return windupLength <= 0f ? 0f : 1f - Math.max(0f, windup / windupLength);
    }

    /** The weak point's position for a boss standing at {@code centre}. */
    public Vec3 weakPointPosition(Vec3 centre) {
        return centre.add(weakPointOffset);
    }

    /** Whether a blow at {@code point} lands on the weak point. */
    public boolean hitsWeakPoint(Vec3 centre, Vec3 point, float tolerance) {
        if (!weakPointExposed()) {
            return false;
        }
        return point.sub(weakPointPosition(centre)).length() <= tolerance;
    }
}
