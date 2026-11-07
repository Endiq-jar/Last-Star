package com.laststar.game.ui;

import com.laststar.game.core.Noise;
import com.laststar.game.world.Enemy;
import com.laststar.game.world.Player;
import com.laststar.game.world.Shard;
import com.laststar.game.world.SpellBook;
import com.laststar.game.world.World;
import com.laststar.game.world.lore.SpellCorpus;

/**
 * Builds the 2D interface for a frame: health/stamina bars, shard counter,
 * objective text, threat indicators, touch controls and the intro/victory
 * screens.
 */
public final class Hud {

    private final AtlasFont font;
    /** Reflects the audio preference so the icon can show a slash. */
    public boolean soundOn = true;
    private float time;
    private float fps;

    public Hud(AtlasFont font) {
        this.font = font;
    }

    public void update(float dt) {
        time += dt;
        if (dt > 0f) {
            float instant = 1f / dt;
            fps = fps <= 0f ? instant : fps * 0.94f + instant * 0.06f;
        }
    }

    public void draw(UiBatch b, World world, TouchControls controls, boolean showDebug) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;

        // Damage vignette and pickup flash come first so UI stays readable.
        Player p = world.player;
        if (p.damageFlash > 0.01f) {
            b.overlay(0.75f, 0.06f, 0.08f, Math.min(0.55f, p.damageFlash * 0.55f));
        }
        if (world.pickupFlash > 0.01f) {
            b.overlay(1f, 0.92f, 0.60f, world.pickupFlash * 0.20f);
        }
        if (world.victoryFlash > 0.01f) {
            b.overlay(1f, 0.95f, 0.75f, world.victoryFlash * 0.75f);
        }
        if (world.waterTime > 0.5f) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 6f);
            b.overlay(0.15f, 0.35f, 0.65f, 0.12f + 0.10f * pulse);
        }

        drawBars(b, world);
        drawLightBar(b, world);
        drawShardCounter(b, world);
        drawThreatArrows(b, world);
        boolean controlsVisible = world.phase == World.Phase.PLAYING
                || world.phase == World.Phase.DEFEAT;
        if (controlsVisible) {
            drawControls(b, world, controls);
            drawSpellSlots(b, world, controls);
        }
        drawMessages(b, world);

        if (showDebug) {
            b.text("FPS " + (int) fps, 12f, h - 18f, 1, 0.8f, 1f, 0.8f, 0.9f, font);
            b.text("SHARDS " + world.shardsCollected + " KILLS " + world.kills + " DEATHS " + world.deaths,
                    12f, h - 30f, 1, 1f, 1f, 1f, 0.85f, font);
        }

        switch (world.phase) {
            case INTRO:
                drawIntro(b, world);
                break;
            case VICTORY:
            case COMPLETE:
                drawVictory(b, world);
                break;
            case DEFEAT:
                drawDefeat(b, world);
                break;
            default:
                break;
        }
    }

    private void drawBars(UiBatch b, World world) {
        float x = 16f;
        float y = 14f;
        float barW = 148f;
        float barH = 11f;

        // Health.
        b.quad(x - 3f, y - 3f, barW + 6f, barH + 6f, 0f, 0f, 0f, 0.42f);
        b.quad(x, y, barW, barH, 0.12f, 0.05f, 0.07f, 1f);
        float hp = world.player.healthFraction();
        float hpW = barW * hp;
        float hue = Noise.lerp(0.03f, 0.36f, hp);
        float[] rgb = hsv(hue, 0.75f, 0.95f);
        b.quad(x, y, hpW, barH, rgb[0], rgb[1], rgb[2], 1f);
        b.quad(x, y, hpW, barH * 0.35f, 1f, 1f, 1f, 0.18f);
        b.text("HP " + (int) world.player.health, x + 3f, y + 2f, 1, 1f, 1f, 1f, 0.95f, font);

        // Stamina.
        float sy = y + barH + 6f;
        b.quad(x, sy, barW, 6f, 0.06f, 0.10f, 0.13f, 0.9f);
        b.quad(x, sy, barW * world.player.sprintStamina, 6f, 0.35f, 0.78f, 0.98f, 0.95f);
    }

    /**
     * The Light bar, under the health and stamina bars.
     *
     * <p>Light is what every spell spends, so it belongs with the other
     * resources rather than next to the spell buttons. It is drawn in a warm
     * gold because it is the one resource the player is meant to want full
     * rather than fear empty.</p>
     */
    private void drawLightBar(UiBatch b, World world) {
        float x = 16f;
        float y = 14f + 11f + 6f + 6f + 5f;
        float barW = 148f;
        float barH = 8f;
        float f = world.spells.lightFraction();

        b.quad(x - 3f, y - 3f, barW + 6f, barH + 6f, 0f, 0f, 0f, 0.42f);
        b.quad(x, y, barW, barH, 0.11f, 0.09f, 0.04f, 1f);
        // Warm gold, brightening as the bar fills.
        float glow = 0.72f + 0.28f * f;
        b.quad(x, y, barW * f, barH, 1f, 0.82f * glow, 0.35f, 1f);
        b.quad(x, y, barW * f, barH * 0.4f, 1f, 1f, 0.86f, 0.24f);

        // A cast refused for lack of Light pulses the bar red, so the refusal
        // is visible where the resource is rather than only on the slot.
        if (world.spells.lightStarved > 0f) {
            b.quad(x, y, barW, barH, 1f, 0.25f, 0.20f,
                    world.spells.lightStarved * 0.7f);
        }
        b.text("LIGHT " + (int) world.spells.light, x + 3f, y - 1f, 1,
                1f, 0.95f, 0.80f, 0.95f, font);
    }

    /**
     * The four spell slots.
     *
     * <p>Each shows its school colour, the mastery it has earned as a ring
     * around the rim, and its cooldown as a disc that closes over the button.
     * The name sits under the slot: with four the player learns the layout in a
     * minute, but the label is what teaches it.</p>
     */
    private void drawSpellSlots(UiBatch b, World world, TouchControls controls) {
        for (int i = 0; i < controls.spell.length && i < world.spells.equipped.length; i++) {
            SpellBook.Page spell = world.spells.pageAt(i);
            TouchControls.Button btn = controls.spell[i];
            if (spell == null) {
                continue;
            }
            float press = btn.pressAmount;
            float radius = btn.radius * (1f - press * 0.08f);

            float[] c = schoolColour(spell.def.category);
            boolean ready = spell.ready(world.spells);

            b.glow(btn.x, btn.y, radius * 1.5f, 14, c[0], c[1], c[2],
                    ready ? 0.16f + press * 0.18f : 0.05f);
            b.circle(btn.x, btn.y, radius, 20, c[0] * 0.3f, c[1] * 0.3f,
                    c[2] * 0.3f, 0.62f);
            b.circle(btn.x, btn.y, radius * 0.86f, 18, c[0], c[1], c[2],
                    ready ? 0.66f + press * 0.24f : 0.30f);

            // Mastery ring: how far this page has grown.
            b.ring(btn.x, btn.y, radius * 0.99f, radius * 0.88f, 22,
                    1f, 0.94f, 0.72f, 0.22f + spell.progress() * 0.62f);

            // Cooldown: a dark disc that shrinks away as the spell returns.
            if (spell.cooldownLeft > 0f && spell.def.cooldown > 0f) {
                float left = spell.cooldownLeft / spell.def.cooldown;
                b.circle(btn.x, btn.y, radius * 0.84f * left, 16, 0f, 0f, 0f, 0.55f);
            }

            b.text((i + 1) + "", btn.x - 2f, btn.y - 4f, 1, 1f, 1f, 1f,
                    ready ? 0.95f : 0.45f, font);
            b.text(shortName(spell.def.name), btn.x - 2f, btn.y + radius + 2f, 1,
                    1f, 1f, 1f, 0.62f, font);
            if (spell.level > 1) {
                b.text("L" + spell.level, btn.x + radius - 3f,
                        btn.y - radius - 2f, 1, 1f, 0.92f, 0.70f, 0.85f, font);
            }
        }
    }

    /** Slot colour per spell school, so the bar is readable at a glance. */
    private static float[] schoolColour(SpellCorpus.Category category) {
        switch (category) {
            case ELEMENTAL:
                return new float[]{0.98f, 0.52f, 0.24f};
            case DEFENSIVE:
                return new float[]{0.42f, 0.82f, 0.98f};
            case ARCANE:
                return new float[]{0.72f, 0.52f, 0.98f};
            case SPATIAL:
                return new float[]{0.55f, 0.95f, 0.72f};
            case ENHANCEMENT:
                return new float[]{0.98f, 0.84f, 0.42f};
            case SUMMONING:
                return new float[]{0.62f, 0.90f, 0.88f};
            case ILLUSION:
                return new float[]{0.86f, 0.58f, 0.98f};
            case FORBIDDEN:
                return new float[]{0.95f, 0.32f, 0.42f};
            default:
                return new float[]{0.90f, 0.90f, 0.94f};
        }
    }

    /** First word of a spell's name, which is what fits under a slot. */
    private static String shortName(String name) {
        int space = name.indexOf(' ');
        return space < 0 ? name : name.substring(0, space);
    }

    private void drawShardCounter(UiBatch b, World world) {
        float cx = b.virtualWidth - 84f;
        float cy = 28f;
        b.glow(cx, cy, 46f, 14, 1f, 0.88f, 0.45f, 0.30f);
        b.text(world.shardsCollected + "/" + World.SHARD_COUNT, cx - 22f, cy - 15f, 2,
                1f, 0.95f, 0.75f, 1f, font);
        // Six pips so progress reads at a glance.
        for (int i = 0; i < World.SHARD_COUNT; i++) {
            float px = cx - 30f + i * 12f;
            float py = cy + 16f;
            boolean filled = i < world.shardsCollected;
            b.circle(px, py, 3.4f, 8, filled ? 1f : 0.35f, filled ? 0.9f : 0.35f,
                    filled ? 0.5f : 0.4f, filled ? 1f : 0.6f);
        }
    }

    /** Off-screen arrows pointing at nearby shards and enemies. */
    private void drawThreatArrows(UiBatch b, World world) {
        float cx = b.virtualWidth * 0.5f;
        float cy = b.virtualHeight * 0.5f;
        float radius = Math.min(b.virtualWidth, b.virtualHeight) * 0.38f;

        for (Shard s : world.shards) {
            if (s.collected) {
                continue;
            }
            com.laststar.game.core.Vec3 to = s.position.sub(world.player.position);
            float dist = to.horizontalLength();
            if (dist > 55f) {
                continue;
            }
            float ang = to.headingDegrees() - world.player.yaw;
            ang = ((ang + 540f) % 360f) - 180f;
            float a = (float) Math.toRadians(ang);
            float px = cx + (float) Math.sin(a) * radius;
            float py = cy - (float) Math.cos(a) * radius * 0.5f;
            float alpha = Noise.clamp01(1f - dist / 55f) * 0.75f;
            b.glow(px, py, 7f + 3f * (float) Math.sin(time * 3f + s.index), 8,
                    1f, 0.88f, 0.42f, alpha);
        }

        for (Enemy e : world.enemies) {
            if (!e.alive) {
                continue;
            }
            float dist = e.position.distanceTo(world.player.position);
            if (dist > 22f) {
                continue;
            }
            float ang = e.position.sub(world.player.position).headingDegrees() - world.player.yaw;
            ang = ((ang + 540f) % 360f) - 180f;
            float a = (float) Math.toRadians(ang);
            float px = cx + (float) Math.sin(a) * radius * 0.82f;
            float py = cy - (float) Math.cos(a) * radius * 0.42f;
            float alpha = Noise.clamp01(1f - dist / 22f);
            b.circle(px, py, 3f, 8, 1f, 0.28f, 0.22f, alpha);
        }
    }

    private void drawControls(UiBatch b, World world, TouchControls controls) {
        // Movement stick.
        float baseX = controls.stickBaseX;
        float baseY = controls.stickBaseY;
        b.ring(baseX, baseY, controls.stickRadius, controls.stickRadius - 3.5f, 26,
                0.9f, 0.95f, 1f, 0.30f);
        b.circle(baseX, baseY, controls.stickRadius * 0.52f, 20, 0.85f, 0.92f, 1f, 0.20f);
        b.circle(controls.stickKnobX, controls.stickKnobY, controls.stickRadius * 0.42f, 20,
                1f, 0.97f, 0.88f, 0.72f);
        b.circle(controls.stickKnobX, controls.stickKnobY, controls.stickRadius * 0.28f, 16,
                1f, 0.88f, 0.55f, 0.95f);

        drawButton(b, controls.attack, 1f, 0.42f, 0.30f, "HIT");
        drawButton(b, controls.heavy, 0.98f, 0.62f, 0.24f, "HVY");
        drawButton(b, controls.dodge, 0.62f, 0.90f, 0.72f, "DGE");
        drawButton(b, controls.parry, 0.72f, 0.78f, 1f, "PRY");
        drawButton(b, controls.jump, 0.40f, 0.78f, 1f, "JMP");
        drawButton(b, controls.sprint, 0.95f, 0.80f, 0.35f, "RUN");

        // The charge meter sits on the heavy button, where the thumb already
        // is, rather than in a bar somewhere across the screen.
        if (world.combat.stance == com.laststar.game.world.CombatKit.Stance.CHARGING) {
            float frac = world.combat.chargeFraction;
            float r = controls.heavy.radius;
            b.circle(controls.heavy.x, controls.heavy.y, r * 0.94f, 24,
                    1f, 0.72f, 0.26f, 0.18f + frac * 0.5f);
            if (frac >= 1f) {
                // Fully wound: a ring, so the player knows the release is now.
                b.ring(controls.heavy.x, controls.heavy.y, r * 1.25f, r * 1.05f, 20,
                        1f, 0.86f, 0.42f, 0.9f);
            }
        }

        // The counter window after a perfect dodge, drawn on the parry button
        // because that is the button that will spend it.
        if (world.combat.counterWindow > 0f) {
            float r = controls.parry.radius * 1.28f;
            b.ring(controls.parry.x, controls.parry.y, r, r * 0.8f, 18,
                    1f, 0.90f, 0.55f, 0.55f
                            + 0.35f * (float) Math.sin(world.worldTime * 18f));
        }

        // Pause icon.
        float px = controls.pause.x;
        float py = controls.pause.y;
        float a = 0.35f + controls.pause.pressAmount * 0.4f;
        b.quad(px - 6f, py - 8f, 4f, 16f, 1f, 1f, 1f, a);
        b.quad(px + 2f, py - 8f, 4f, 16f, 1f, 1f, 1f, a);

        // Sound icon: speaker body plus waves, or a slash when muted.
        float sx2 = controls.sound.x;
        float sy2 = controls.sound.y;
        float sa = 0.35f + controls.sound.pressAmount * 0.4f;
        b.quad(sx2 - 7f, sy2 - 4f, 5f, 8f, 1f, 1f, 1f, sa);
        b.quad(sx2 - 3f, sy2 - 7f, 4f, 14f, 1f, 1f, 1f, sa);
        if (soundOn) {
            b.ring(sx2 + 3f, sy2, 7f, 5.5f, 10, 1f, 1f, 1f, sa);
        } else {
            b.quad(sx2 - 8f, sy2 - 1f, 18f, 2.5f, 1f, 0.5f, 0.4f, sa);
        }
    }

    private void drawButton(UiBatch b, TouchControls.Button btn,
                            float r, float g, float bl, String label) {
        float press = btn.pressAmount;
        float radius = btn.radius * (1f - press * 0.10f);
        b.glow(btn.x, btn.y, radius * 1.35f, 16, r, g, bl, 0.10f + press * 0.14f);
        b.circle(btn.x, btn.y, radius, 22, r * 0.35f, g * 0.35f, bl * 0.35f, 0.55f);
        b.circle(btn.x, btn.y, radius * 0.86f, 20, r, g, bl, 0.62f + press * 0.28f);
        b.circle(btn.x, btn.y, radius * 0.86f, 20, 1f, 1f, 1f, 0.10f);
        int scale = radius > 26f ? 2 : 1;
        b.textCentred(label, btn.x, btn.y - (Font.GLYPH_H * scale) * 0.5f, scale,
                1f, 1f, 1f, 0.92f, font);
    }

    private void drawMessages(UiBatch b, World world) {
        if (world.message == null || world.message.isEmpty() || world.messageTimer <= 0f) {
            return;
        }
        float fade = Noise.clamp01(world.messageTimer / 0.6f);
        float y = b.virtualHeight * 0.24f;
        float width = font.textWidth(world.message, 2) + 26f;
        b.quad(b.virtualWidth * 0.5f - width * 0.5f, y - 8f, width, 32f,
                0f, 0f, 0f, 0.38f * fade);
        b.textCentred(world.message, b.virtualWidth * 0.5f, y, 2, 1f, 0.97f, 0.9f, fade, font);
    }

    private void drawIntro(UiBatch b, World world) {
        float t = Noise.clamp01(world.phaseTimer / 2.2f);
        float alpha = t < 0.15f ? t / 0.15f : 1f;
        b.overlay(0.02f, 0.03f, 0.06f, 0.55f * alpha);
        float cx = b.virtualWidth * 0.5f;
        float cy = b.virtualHeight * 0.42f;
        b.glow(cx, cy - 6f, 210f, 30, 1f, 0.86f, 0.48f, 0.13f * alpha);
        b.textCentred("LAST STAR", cx, cy - 26f, 5, 1f, 0.93f, 0.70f, alpha, font);
        b.textCentred("A FRAGMENTED WORLD. SIX SHARDS. ONE LAST LIGHT.",
                cx, cy + 22f, 1, 0.85f, 0.92f, 1f, alpha * 0.9f, font);
        b.textCentred("MOVE WITH THE LEFT THUMB - LOOK WITH THE RIGHT",
                cx, cy + 44f, 1, 0.7f, 0.8f, 0.9f, alpha * (float) Math.min(1.0, t * 1.4), font);
    }

    private void drawVictory(UiBatch b, World world) {
        float t = Noise.clamp01(world.phaseTimer / 1.2f);
        b.overlay(0.03f, 0.04f, 0.08f, 0.62f * t);
        float cx = b.virtualWidth * 0.5f;
        float cy = b.virtualHeight * 0.40f;
        b.glow(cx, cy, 250f, 32, 1f, 0.9f, 0.55f, 0.20f * t);
        b.textCentred("THE SKY IS WHOLE AGAIN", cx, cy - 14f, 3, 1f, 0.94f, 0.72f, t, font);
        String stats = "KILLS " + world.kills + "    DEATHS " + world.deaths;
        b.textCentred(stats, cx, cy + 26f, 1, 0.9f, 0.95f, 1f, t, font);
        b.textCentred("TAP THE CENTRE TO BEGIN THE NEXT NIGHT", cx + 0f,
                cy + 52f, 1, 0.75f, 0.85f, 0.95f, 0.85f * t, font);
    }

    private void drawDefeat(UiBatch b, World world) {
        float alpha = Noise.clamp01(world.phaseTimer / 1.0f);
        b.overlay(0.15f, 0.02f, 0.03f, 0.45f * alpha);
        float cx = b.virtualWidth * 0.5f;
        float cy = b.virtualHeight * 0.42f;
        b.textCentred("THE LIGHT FADES", cx, cy - 10f, 3, 1f, 0.55f, 0.5f, alpha, font);
    }

    /** HSV -> RGB helper for the health bar gradient. */
    private static float[] hsv(float h, float s, float v) {
        float r;
        float g;
        float bl;
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        switch (i % 6) {
            case 0:
                r = v;
                g = t;
                bl = p;
                break;
            case 1:
                r = q;
                g = v;
                bl = p;
                break;
            case 2:
                r = p;
                g = v;
                bl = t;
                break;
            case 3:
                r = p;
                g = q;
                bl = v;
                break;
            case 4:
                r = t;
                g = p;
                bl = v;
                break;
            default:
                r = v;
                g = p;
                bl = q;
                break;
        }
        return new float[]{r, g, bl};
    }
}
