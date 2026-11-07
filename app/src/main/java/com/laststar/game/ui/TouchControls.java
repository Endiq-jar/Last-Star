package com.laststar.game.ui;

import android.content.Context;
import android.view.MotionEvent;

import com.laststar.game.world.InputState;

/**
 * Multi-touch controls drawn and hit-tested in virtual coordinates.
 *
 * <p>Layout: a floating movement stick on the left, a camera drag zone on the
 * right, and three buttons (attack / jump / sprint) in the bottom-right corner.
 * Every control keeps track of the pointer id that owns it, so dragging with
 * one thumb never steals the other's input.</p>
 */
public final class TouchControls {

    /** Virtual design space; the renderer scales this to the real surface. */
    public static final float DESIGN_W = 720f;
    public static final float DESIGN_H = 405f;

    // --- stick state -------------------------------------------------
    public float stickBaseX = 104f;
    public float stickBaseY = 305f;
    public float stickKnobX = 104f;
    public float stickKnobY = 305f;
    public float stickRadius = 46f;
    public boolean stickActive;
    private int stickPointer = -1;

    // --- look state --------------------------------------------------
    public float lookPointerX;
    public float lookPointerY;
    public boolean lookActive;
    private int lookPointer = -1;
    public float lookSensitivity = 0.16f;

    // --- buttons -----------------------------------------------------
    public final Button attack = new Button(636f, 330f, 33f);
    /** The heavy: tap to swing, hold to charge. Smaller and inboard of ATTACK. */
    public final Button heavy = new Button(690f, 372f, 24f);
    /** Dodge, and the parry that shares its corner of the screen. */
    public final Button dodge = new Button(578f, 384f, 22f);
    public final Button parry = new Button(690f, 320f, 20f);
    public final Button jump = new Button(566f, 336f, 24f);
    public final Button sprint = new Button(690f, 268f, 22f);
    public final Button pause = new Button(196f, 26f, 15f);
    public final Button sound = new Button(236f, 26f, 15f);
    /**
     * The four spell slots, along the bottom edge under the thumb.
     *
     * <p>Out of the way of the stick and the look-drag, close enough to reach
     * without moving the hand already holding the phone, and spread wide
     * enough that a thumb cannot hit two at once.</p>
     */
    public final Button[] spell = {
            new Button(452f, 352f, 18f),
            new Button(505f, 352f, 18f),
            new Button(558f, 352f, 18f),
            new Button(611f, 352f, 18f)
    };

    private int attackPointer = -1;
    private int heavyPointer = -1;
    private int dodgePointer = -1;
    private int parryPointer = -1;
    private int jumpPointer = -1;
    private int sprintPointer = -1;
    private int pausePointer = -1;
    private int soundPointer = -1;
    /** Pointer currently pressing each spell button, or -1. */
    private final int[] spellPointer = {-1, -1, -1, -1};

    /** Set when the pause button is tapped; cleared by the activity. */
    public boolean pauseRequested;
    /** Set when the sound button is tapped; cleared by the activity. */
    public boolean soundToggleRequested;

    /** A circular on-screen button. */
    public static final class Button {
        public float x;
        public float y;
        public float radius;
        public float pressAmount;

        Button(float x, float y, float radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }

        public boolean contains(float px, float py) {
            float dx = px - x;
            float dy = py - y;
            return dx * dx + dy * dy <= (radius * 1.35f) * (radius * 1.35f);
        }
    }

    /**
     * Applies one touch event to the control state.
     *
     * @param event     raw motion event (virtual coordinates already applied)
     * @param out       input state to update
     * @param scaleX    factor converting screen x to virtual x
     * @param scaleY    factor converting screen y to virtual y
     */
    public void handleTouch(MotionEvent event, InputState out, float scaleX, float scaleY) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                int id = event.getPointerId(index);
                float x = event.getX(index) * scaleX;
                float y = event.getY(index) * scaleY;
                onDown(id, x, y, out);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    float x = event.getX(i) * scaleX;
                    float y = event.getY(i) * scaleY;
                    onMove(id, x, y, out);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int index = event.getActionIndex();
                int id = event.getPointerId(index);
                float x = event.getX(index) * scaleX;
                float y = event.getY(index) * scaleY;
                onUp(id, x, y, out);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                reset(out);
                break;
            default:
                break;
        }
    }

    private void onDown(int id, float x, float y, InputState out) {
        if (pause.contains(x, y) && pausePointer < 0) {
            pausePointer = id;
            pause.pressAmount = 1f;
            pauseRequested = true;
            return;
        }
        if (sound.contains(x, y) && soundPointer < 0) {
            soundPointer = id;
            sound.pressAmount = 1f;
            soundToggleRequested = true;
            return;
        }
        // Spells first: they sit inside the right-hand look area, and a drag
        // that starts on a spell button must cast rather than swing the camera.
        for (int i = 0; i < spell.length; i++) {
            if (spell[i].contains(x, y) && spellPointer[i] < 0) {
                spellPointer[i] = id;
                spell[i].pressAmount = 1f;
                out.castSlot = i;
                return;
            }
        }
        if (attack.contains(x, y) && attackPointer < 0) {
            attackPointer = id;
            attack.pressAmount = 1f;
            out.attackHeld = true;
            out.attackPressed = true;
            return;
        }
        // The heavy is checked before dodge and parry because it is the largest
        // of the three and the one a thumb is most likely to land on.
        if (heavy.contains(x, y) && heavyPointer < 0) {
            heavyPointer = id;
            heavy.pressAmount = 1f;
            out.heavyHeld = true;
            out.heavyPressed = true;
            return;
        }
        if (dodge.contains(x, y) && dodgePointer < 0) {
            dodgePointer = id;
            dodge.pressAmount = 1f;
            out.dodgePressed = true;
            return;
        }
        if (parry.contains(x, y) && parryPointer < 0) {
            parryPointer = id;
            parry.pressAmount = 1f;
            out.parryPressed = true;
            return;
        }
        if (jump.contains(x, y) && jumpPointer < 0) {
            jumpPointer = id;
            jump.pressAmount = 1f;
            out.jumpPressed = true;
            return;
        }
        if (sprint.contains(x, y) && sprintPointer < 0) {
            sprintPointer = id;
            sprint.pressAmount = 1f;
            out.sprintHeld = true;
            return;
        }
        // Left half: movement stick (dynamic base follows the thumb down).
        if (x < DESIGN_W * 0.46f && x > 8f && !stickActive) {
            stickPointer = id;
            stickActive = true;
            stickBaseX = Math.max(stickRadius + 8f, x);
            stickBaseY = y;
            stickKnobX = x;
            stickKnobY = y;
            return;
        }
        // Right half: camera drag.
        if (!lookActive) {
            lookPointer = id;
            lookActive = true;
            lookPointerX = x;
            lookPointerY = y;
        }
    }

    private void onMove(int id, float x, float y, InputState out) {
        if (id == stickPointer) {
            float dx = x - stickBaseX;
            float dy = y - stickBaseY;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > stickRadius) {
                dx = dx / len * stickRadius;
                dy = dy / len * stickRadius;
                // Drift the base along so the stick never "sticks" at its edge.
                stickBaseX += (x - stickBaseX) * 0.06f;
                stickBaseY += (y - stickBaseY) * 0.06f;
            }
            stickKnobX = stickBaseX + dx;
            stickKnobY = stickBaseY + dy;
            out.moveX = dx / stickRadius;
            out.moveY = -dy / stickRadius;
            return;
        }
        if (id == lookPointer && lookActive) {
            float dx = x - lookPointerX;
            float dy = y - lookPointerY;
            lookPointerX = x;
            lookPointerY = y;
            out.lookX += dx * lookSensitivity;
            out.lookY -= dy * lookSensitivity * 0.75f;
        }
    }

    private void onUp(int id, float x, float y, InputState out) {
        if (id == stickPointer) {
            stickPointer = -1;
            stickActive = false;
            stickBaseX = 104f;
            stickBaseY = 305f;
            stickKnobX = stickBaseX;
            stickKnobY = stickBaseY;
            out.moveX = 0f;
            out.moveY = 0f;
            return;
        }
        if (id == lookPointer) {
            lookPointer = -1;
            lookActive = false;
            return;
        }
        if (id == attackPointer) {
            attackPointer = -1;
            attack.pressAmount = 0f;
            out.attackHeld = false;
            return;
        }
        if (id == heavyPointer) {
            heavyPointer = -1;
            heavy.pressAmount = 0f;
            // Releasing is what swings a charged heavy, so the release edge is
            // what the world watches.
            out.heavyHeld = false;
            return;
        }
        if (id == dodgePointer) {
            dodgePointer = -1;
            dodge.pressAmount = 0f;
            return;
        }
        if (id == parryPointer) {
            parryPointer = -1;
            parry.pressAmount = 0f;
            return;
        }
        if (id == jumpPointer) {
            jumpPointer = -1;
            jump.pressAmount = 0f;
            return;
        }
        if (id == sprintPointer) {
            sprintPointer = -1;
            sprint.pressAmount = 0f;
        heavy.pressAmount = 0f;
        dodge.pressAmount = 0f;
        parry.pressAmount = 0f;
        heavyPointer = -1;
        dodgePointer = -1;
        parryPointer = -1;
        out.heavyHeld = false;
            out.sprintHeld = false;
            return;
        }
        if (id == pausePointer) {
            pausePointer = -1;
            pause.pressAmount = 0f;
            return;
        }
        if (id == soundPointer) {
            soundPointer = -1;
            sound.pressAmount = 0f;
            return;
        }
        // Pressing a spell must release it, or the button stays lit and the
        // pointer leaks onto whatever that finger touches next.
        for (int i = 0; i < spell.length; i++) {
            if (id == spellPointer[i]) {
                spellPointer[i] = -1;
                spell[i].pressAmount = 0f;
            }
        }
    }

    public void reset(InputState out) {
        stickPointer = -1;
        lookPointer = -1;
        attackPointer = -1;
        jumpPointer = -1;
        sprintPointer = -1;
        pausePointer = -1;
        soundPointer = -1;
        for (int i = 0; i < spellPointer.length; i++) {
            spellPointer[i] = -1;
            spell[i].pressAmount = 0f;
        }
        stickActive = false;
        lookActive = false;
        stickBaseX = 104f;
        stickBaseY = 305f;
        stickKnobX = stickBaseX;
        stickKnobY = stickBaseY;
        attack.pressAmount = 0f;
        jump.pressAmount = 0f;
        sprint.pressAmount = 0f;
        heavy.pressAmount = 0f;
        dodge.pressAmount = 0f;
        parry.pressAmount = 0f;
        heavyPointer = -1;
        dodgePointer = -1;
        parryPointer = -1;
        out.heavyHeld = false;
        pause.pressAmount = 0f;
        sound.pressAmount = 0f;
        out.reset();
    }

    /** Decays button press animations. */
    public void update(float dt) {
        decay(attack, dt);
        decay(jump, dt);
        decay(sprint, dt);
        decay(pause, dt);
        decay(sound, dt);
    }

    private static void decay(Button b, float dt) {
        if (b.pressAmount > 0f) {
            b.pressAmount = Math.max(0f, b.pressAmount - dt * 4f);
        }
    }

    /** Left-handed layout: mirrors stick and buttons. */
    public void setLeftHanded(boolean leftHanded) {
        if (leftHanded) {
            lookSensitivity = 0.16f;
            attack.x = 84f;
            heavy.x = 30f;
            dodge.x = 142f;
            parry.x = 30f;
            jump.x = 154f;
            sprint.x = 30f;
            pause.x = 524f;
            sound.x = 564f;
        } else {
            attack.x = 636f;
            heavy.x = 690f;
            dodge.x = 578f;
            parry.x = 690f;
            jump.x = 566f;
            sprint.x = 690f;
            pause.x = 196f;
            sound.x = 236f;
        }
    }

    /** Scales from screen pixels to virtual units. */
    public static float scaleFor(int screenW, int screenH) {
        float sx = DESIGN_W / Math.max(1f, screenW);
        float sy = DESIGN_H / Math.max(1f, screenH);
        // Letterbox: use the larger scale so the virtual box always fits.
        return Math.max(sx, sy);
    }
}
