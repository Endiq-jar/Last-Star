package com.laststar.game.world;

/**
 * One frame of player intent, produced by the touch controls (or a keyboard on
 * desktop) and consumed by {@link World#update}.
 *
 * <p>Keeping input abstract means the simulation never touches Android APIs and
 * the headless test harness can drive full gameplay sessions.</p>
 */
public final class InputState {

    /** Movement stick, [-1,1] on each axis, already dead-zoned. */
    public float moveX;
    public float moveY;
    /** Look delta in screen-independent units for this frame. */
    public float lookX;
    public float lookY;
    /** Buttons: rising edge is reported separately so taps are never lost. */
    public boolean attackHeld;
    public boolean attackPressed;
    public boolean jumpPressed;
    public boolean sprintHeld;
    public boolean interactPressed;
    /** Camera yaw applied by the controller; the world copies it onto the player. */
    public float cameraYaw;

    public void clearFrameEdges() {
        attackPressed = false;
        jumpPressed = false;
        interactPressed = false;
        lookX = 0f;
        lookY = 0f;
    }

    public void reset() {
        moveX = 0f;
        moveY = 0f;
        lookX = 0f;
        lookY = 0f;
        attackHeld = false;
        attackPressed = false;
        jumpPressed = false;
        sprintHeld = false;
        interactPressed = false;
    }
}
