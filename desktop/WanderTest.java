import com.laststar.game.core.Vec3;
import com.laststar.game.world.Enemy;
import com.laststar.game.world.InputState;
import com.laststar.game.world.Wander;
import com.laststar.game.world.World;

/**
 * Verifies the Wander: the spell-book, the Light it burns, and the four pages
 * it starts with.
 *
 * <p>Driven through {@link World#update} rather than by calling the spell
 * methods directly, because the failures that matter live in the wiring -- the
 * pickup never firing, casting sitting in a phase that never runs, mastery
 * never being awarded. A test that calls {@code cast()} in isolation would pass
 * with every one of those broken.</p>
 */
public final class WanderTest {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        World world = new World(12345L);
        world.beginRun();
        // Clear the intro card so the run is in PLAYING.
        for (int i = 0; i < 160; i++) {
            world.update(1f / 60f, new InputState());
        }

        check("the Wander is placed in the world", world.wanderPosition != null);
        check("not held at the start", !world.hasWander);
        check("four pages", world.wander.spells.length == 4);
        check("all four awake at the start", world.wander.unlockedCount() == 4);
        check("Light starts full", world.wander.light == Wander.MAX_LIGHT);

        // Nothing can be cast before it is found.
        world.wander.light = Wander.MAX_LIGHT;
        castSlot(world, 0);
        check("no spell fires without the book",
                world.wander.light == Wander.MAX_LIGHT);

        // Walk onto it.
        Vec3 spot = world.wanderPosition;
        world.player.position = spot.withY(spot.y);
        world.update(1f / 60f, new InputState());
        check("walking into the book picks it up", world.hasWander);
        check("the pickup spot is cleared", world.wanderPosition == null);

        // Light recovers on its own.
        world.wander.light = 10f;
        for (int i = 0; i < 60; i++) {
            world.update(1f / 60f, new InputState());
        }
        check("Light regenerates over a second", world.wander.light > 14f);

        // Casting spends Light and starts a cooldown.
        world.wander.light = Wander.MAX_LIGHT;
        float before = world.wander.light;
        castSlot(world, 0);
        check("casting spends Light", world.wander.light < before);
        check("casting starts a cooldown",
                world.wander.spells[0].cooldownLeft > 0f);
        check("cannot recast while cooling down", !world.wander.canCast(0));

        for (int i = 0; i < 60; i++) {
            world.update(1f / 60f, new InputState());
        }
        check("the cooldown expires", world.wander.canCast(0));

        // A cast with too little Light is refused, and says so.
        world.wander.light = 1f;
        check("cannot cast without Light", !world.wander.canCast(0));
        castSlot(world, 3);
        check("a starved cast is reported", world.wander.lightStarved > 0f);

        // Ward buys time rather than dealing damage.
        world.wander.light = Wander.MAX_LIGHT;
        world.wander.spells[2].cooldownLeft = 0f;
        castSlot(world, 2);
        check("Ward grants invulnerability",
                world.player.invulnerability >= 2f);

        // Spells land on enemies.
        world.wander.light = Wander.MAX_LIGHT;
        world.wander.spells[1].cooldownLeft = 0f;   // Gale Cut, the sweep
        Enemy target = placeEnemyInFront(world, 3f);
        float hp = target.health;
        castSlot(world, 1);
        check("a sweep damages an enemy in front", target.health < hp);

        // Mastery rises from use.
        int levelBefore = world.wander.spells[1].level;
        for (int i = 0; i < 400; i++) {
            world.wander.light = Wander.MAX_LIGHT;
            world.wander.spells[1].cooldownLeft = 0f;
            // Keep something in range to hit, or no experience is awarded.
            placeEnemyInFront(world, 3f);
            castSlot(world, 1);
        }
        check("mastery rises with use",
                world.wander.spells[1].level > levelBefore);

        // A Light pickup tops up and teaches the book something.
        world.wander.light = 5f;
        float xpBefore = world.wander.spells[0].xp;
        world.wander.absorbLight();
        check("a Light pickup restores Light", world.wander.light > 30f);
        check("a Light pickup teaches the book",
                world.wander.spells[0].xp > xpBefore);

        // Mastery is capped.
        Wander.Spell spell = world.wander.spells[0];
        spell.level = Wander.MAX_LEVEL;
        spell.xp = 0f;
        spell.awardXp(100000f);
        check("mastery stops at the cap", spell.level == Wander.MAX_LEVEL);
        check("a maxed spell reads as full", spell.progress() == 1f);

        System.out.printf("%nchecks: %d   failures: %d%n", checks, failures);
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL");
        if (failures != 0) {
            System.exit(1);
        }
    }

    /** Presses one spell slot for a single frame. */
    private static void castSlot(World world, int slot) {
        InputState in = new InputState();
        in.castSlot = slot;
        world.update(1f / 60f, in);
    }

    /** Moves a live enemy directly in front of the player and returns it. */
    private static Enemy placeEnemyInFront(World world, float distance) {
        Enemy e = world.enemies.get(0);
        e.alive = true;
        e.health = 100000f;
        float rad = (float) Math.toRadians(world.player.yaw);
        Vec3 forward = new Vec3((float) Math.sin(rad), 0f, (float) Math.cos(rad));
        e.position = world.player.position.add(forward.mul(distance));
        return e;
    }

    private static void check(String what, boolean ok) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + what);
        }
    }
}
