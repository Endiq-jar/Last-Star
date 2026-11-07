package com.laststar.game.core;

import com.laststar.game.world.InputState;
import com.laststar.game.world.World;

/**
 * Third-person orbit camera used by both the Android renderer and the desktop
 * harness.
 *
 * <p>It follows the player with critically-damped smoothing, orbits with a
 * yaw/pitch pair driven by the right thumb stick, and pulls in when terrain
 * would otherwise occlude the character.</p>
 */
public final class Camera {

    public static final float MIN_PITCH = -38f;
    public static final float MAX_PITCH = 62f;
    public static final float MIN_DISTANCE = 2.2f;

    public float yaw = 180f;
    public float pitch = 19f;
    public float distance = 7.4f;
    public float defaultDistance = 7.4f;
    public float fov = 62f;

    public Vec3 position = new Vec3(0f, 3f, -6f);
    public Vec3 target = new Vec3(0f, 1.5f, 0f);
    public Vec3 up = Vec3.UP;
    public float shakeX;
    public float shakeY;

    private Vec3 smoothedTarget = new Vec3(0f, 1.5f, 0f);
    private float time;

    public void update(float dt, World world, InputState cameraInput, float shakeAmount) {
        time += dt;
        if (cameraInput != null) {
            yaw += cameraInput.lookX;
            pitch += cameraInput.lookY;
            pitch = Noise.clamp(pitch, MIN_PITCH, MAX_PITCH);
        }

        Vec3 desiredTarget = world.player.centre();
        float k = 1f - (float) Math.exp(-12f * dt);
        smoothedTarget = smoothedTarget.lerp(desiredTarget, k);
        target = smoothedTarget;

        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float cosPitch = (float) Math.cos(pitchRad);
        // offset points from the camera towards the target: for a positive
        // pitch the camera sits higher than the target and looks down.
        Vec3 offset = new Vec3(
                (float) Math.sin(yawRad) * cosPitch,
                -(float) Math.sin(pitchRad),
                (float) Math.cos(yawRad) * cosPitch).normalized();

        // Terrain-aware pull-in so the player is never hidden behind a hill.
        float wanted = defaultDistance;
        float step = 0.5f;
        for (float d = MIN_DISTANCE; d <= defaultDistance; d += step) {
            Vec3 probe = target.addScaled(offset, -d);
            if (probe.y < world.heightAt(probe.x, probe.z) + 0.35f) {
                wanted = Math.max(MIN_DISTANCE, d - step);
                break;
            }
        }
        distance += (wanted - distance) * (1f - (float) Math.exp(-9f * dt));

        Vec3 camPos = target.addScaled(offset, -distance);
        float ground = world.heightAt(camPos.x, camPos.z) + 0.55f;
        if (camPos.y < ground) {
            camPos = camPos.withY(ground);
        }

        if (shakeAmount > 0.001f) {
            float amp = shakeAmount * 0.16f;
            shakeX = (float) Math.sin(time * 61.3f) * amp;
            shakeY = (float) Math.cos(time * 47.7f) * amp * 0.8f;
        } else {
            shakeX = 0f;
            shakeY = 0f;
        }

        position = camPos.add(new Vec3(shakeX, shakeY, 0f));
    }

    public Mat4 viewMatrix() {
        return Mat4.lookAt(position, target.add(new Vec3(shakeX * 0.4f, shakeY * 0.4f, 0f)), up);
    }

    public Mat4 projectionMatrix(float aspect) {
        // The far plane has to clear the distant mountain ring (430 m) with
        // room to spare, or the whole range is clipped away silently.
        return Mat4.perspective(fov, aspect, 0.08f, 1400f);
    }

    /** Applies a look input, clamped so the camera never flips. */
    public void applyLook(float dx, float dy) {
        yaw += dx;
        pitch = Noise.clamp(pitch + dy, MIN_PITCH, MAX_PITCH);
    }

    /** Snaps the camera behind the player, used on spawn/respawn. */
    public void snapBehind(World world) {
        smoothedTarget = world.player.centre();
        target = smoothedTarget;
        yaw = world.player.yaw;
        pitch = 19f;
        distance = defaultDistance;
    }

    /**
     * Yaw used for camera-relative movement. The camera looks along
     * {@code (sin(yaw), cos(yaw))}, so this is simply the camera yaw.
     */
    public float movementYaw() {
        return yaw;
    }
}
