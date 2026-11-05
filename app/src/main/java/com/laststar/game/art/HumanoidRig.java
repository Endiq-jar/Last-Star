package com.laststar.game.art;

import com.laststar.game.core.Mat4;
import com.laststar.game.gl.Geometry;
import com.laststar.game.gl.MeshData;

/**
 * A procedurally generated, skeletally animated humanoid.
 *
 * <p>Each limb is a small generated mesh whose vertices are stored relative to
 * its joint. At draw time the parts are posed by rotating them about their
 * joint; because the parts are tiny (a few dozen triangles each) this costs
 * almost nothing and gives a genuine animated character with no rigged assets
 * and no animation import pipeline.</p>
 *
 * <p>Two palettes are provided: the player (rim-lit explorer in teal and
 * amber) and the Wardens (charcoal and crimson, with glowing eyes).</p>
 */
public final class HumanoidRig {

    /** A single rigid body part. */
    public static final class Part {
        public final MeshData mesh;
        /** Joint position in model space. */
        public final float jx, jy, jz;

        Part(MeshData mesh, float jx, float jy, float jz) {
            this.mesh = mesh;
            this.jx = jx;
            this.jy = jy;
            this.jz = jz;
        }
    }

    public final Part head;
    public final Part torso;
    public final Part hips;
    public final Part leftArm;
    public final Part rightArm;
    public final Part leftLeg;
    public final Part rightLeg;
    public final Part weapon;
    public final Part cape;
    /** Total model height in metres. */
    public final float height;
    public final float eyeHeight;

    private HumanoidRig(Part head, Part torso, Part hips, Part leftArm, Part rightArm,
                        Part leftLeg, Part rightLeg, Part weapon, Part cape,
                        float height, float eyeHeight) {
        this.head = head;
        this.torso = torso;
        this.hips = hips;
        this.leftArm = leftArm;
        this.rightArm = rightArm;
        this.leftLeg = leftLeg;
        this.rightLeg = rightLeg;
        this.weapon = weapon;
        this.cape = cape;
        this.height = height;
        this.eyeHeight = eyeHeight;
    }

    /** Palette describing a character's look. */
    public static final class Palette {
        public final float[] body;
        public final float[] accent;
        public final float[] skin;
        public final float[] dark;
        public final float[] glow;

        public Palette(float[] body, float[] accent, float[] skin, float[] dark, float[] glow) {
            this.body = body;
            this.accent = accent;
            this.skin = skin;
            this.dark = dark;
            this.glow = glow;
        }

        /** Teal jacket, amber trim: the Last Star. */
        public static Palette player() {
            return new Palette(
                    new float[]{0.26f, 0.66f, 0.78f},
                    new float[]{1.00f, 0.78f, 0.30f},
                    new float[]{0.86f, 0.67f, 0.51f},
                    new float[]{0.11f, 0.14f, 0.20f},
                    new float[]{0.55f, 0.92f, 1.00f});
        }

        /** Charcoal armour, crimson trim, hot eyes: the Wardens. */
        public static Palette warden() {
            return new Palette(
                    new float[]{0.34f, 0.33f, 0.40f},
                    new float[]{0.86f, 0.20f, 0.22f},
                    new float[]{0.42f, 0.39f, 0.44f},
                    new float[]{0.08f, 0.08f, 0.10f},
                    new float[]{1.00f, 0.42f, 0.28f});
        }
    }

    /** Builds the player rig (slim explorer, 1.72 m). */
    public static HumanoidRig player() {
        return build(Palette.player(), 1f);
    }

    /** Builds a Warden rig (bulkier, 2.25 m). */
    public static HumanoidRig warden() {
        return build(Palette.warden(), 1.28f);
    }

    private static HumanoidRig build(Palette p, float bulk) {
        float h = 1.72f * bulk;
        float shoulderY = h * 0.80f;
        float hipY = h * 0.48f;

        Part torso = new Part(Geometry.box(0.44f * bulk, h * 0.34f, 0.26f * bulk,
                p.body[0], p.body[1], p.body[2], 1f,
                0f, h * 0.145f, 0f), 0f, shoulderY - h * 0.34f, 0f);

        Part hips = new Part(Geometry.box(0.36f * bulk, h * 0.13f, 0.24f * bulk,
                p.dark[0], p.dark[1], p.dark[2], 1f,
                0f, h * 0.065f, 0f), 0f, hipY, 0f);

        float headR = h * 0.088f;
        MeshData headMesh = MeshData.merge(
                Geometry.sphere(headR, 8, 10, p.skin[0], p.skin[1], p.skin[2], 1f),
                Geometry.box(0.30f * bulk, 0.10f * bulk, 0.30f * bulk,
                        p.accent[0], p.accent[1], p.accent[2], 1f,
                        0f, -headR * 0.55f, 0f));
        Part head = new Part(headMesh, 0f, h * 0.90f, 0f);

        float armLen = h * 0.30f;
        MeshData armMesh = MeshData.merge(
                Geometry.box(0.115f * bulk, armLen, 0.13f * bulk,
                        p.body[0], p.body[1], p.body[2], 1f,
                        0f, -armLen * 0.5f, 0f),
                Geometry.box(0.125f * bulk, 0.10f * bulk, 0.14f * bulk,
                        p.skin[0], p.skin[1], p.skin[2], 1f,
                        0f, -armLen, 0f));
        float shoulderX = 0.255f * bulk;
        Part leftArm = new Part(armMesh, -shoulderX, shoulderY, 0f);
        Part rightArm = new Part(armMesh, shoulderX, shoulderY, 0f);

        float legLen = h * 0.46f;
        MeshData legMesh = MeshData.merge(
                Geometry.box(0.155f * bulk, legLen, 0.17f * bulk,
                        p.dark[0], p.dark[1], p.dark[2], 1f,
                        0f, -legLen * 0.5f, 0f),
                Geometry.box(0.17f * bulk, 0.09f * bulk, 0.26f * bulk,
                        p.accent[0] * 0.7f, p.accent[1] * 0.7f, p.accent[2] * 0.7f, 1f,
                        0f, -legLen, 0.045f));
        float legX = 0.105f * bulk;
        Part leftLeg = new Part(legMesh, -legX, hipY, 0f);
        Part rightLeg = new Part(legMesh, legX, hipY, 0f);

        // Weapon: a blade of light for the player, a cleaver for the wardens.
        MeshData weaponMesh = MeshData.merge(
                Geometry.box(0.045f, h * 0.06f, 0.045f, 0.25f, 0.25f, 0.28f, 1f, 0f, h * 0.03f, 0f),
                Geometry.box(0.09f, h * 0.30f, 0.012f,
                        p.glow[0], p.glow[1], p.glow[2], 1f,
                        0f, h * 0.21f, 0f),
                Geometry.cone(0.045f, h * 0.07f, 4,
                        p.glow[0], p.glow[1], p.glow[2], 1f));
        Part weapon = new Part(weaponMesh, shoulderX, shoulderY - armLen * 0.95f, 0.13f);

        // Cape / back banner for silhouette.
        MeshData capeMesh = Geometry.box(0.34f * bulk, h * 0.34f, 0.03f,
                p.accent[0], p.accent[1], p.accent[2], 0.95f,
                0f, -h * 0.17f, -0.02f);
        Part cape = new Part(capeMesh, 0f, shoulderY - h * 0.02f, -0.13f * bulk);

        return new HumanoidRig(head, torso, hips, leftArm, rightArm, leftLeg, rightLeg,
                weapon, cape, h, h * 0.90f + headR * 0.6f);
    }

    /** Reusable scratch matrices so posing does not allocate per frame. */
    public static final class Pose {
        public final Mat4[] partMatrices = new Mat4[9];
        public final Mat4[] partColorsGlow = null;

        public Pose() {
            for (int i = 0; i < partMatrices.length; i++) {
                partMatrices[i] = new Mat4();
            }
        }
    }

    /**
     * Computes the world matrix for each part of the rig.
     *
     * @param out        receives 8 matrices in the order: torso, hips, head,
     *                   leftArm, rightArm, leftLeg, rightLeg, weapon, cape
     * @param modelRoot  matrix placing the character at its world position and heading
     * @param animPhase  locomotion cycle phase in radians
     * @param moveAmount 0 (idle) .. 1 (running)
     * @param attack     0..1 swing progress
     * @param airTime    true while jumping/falling
     * @param flash      0..1 hit flash amount
     */
    public void pose(Mat4[] out, Mat4 modelRoot, float animPhase, float moveAmount,
                     float attack, boolean airTime, float flash) {
        float swing = (float) Math.sin(animPhase) * 0.85f * moveAmount;
        float bob = (float) Math.abs(Math.cos(animPhase)) * 0.055f * moveAmount;

        boolean attacking = attack > 0f;
        // Attack sweeps the right arm from raised to low across ~110 degrees.
        float attackCurve = attack <= 0f ? 0f : (float) Math.sin(attack * Math.PI);
        float rightArmSwing = attacking ? -2.35f * attackCurve : -swing;
        float leftArmSwing = attacking ? 0.55f * attackCurve : swing;
        float torsoTwist = attacking ? -22f * attackCurve : 0f;

        Mat4 root = new Mat4().copyFrom(modelRoot);
        if (bob > 0f) {
            root.m[13] += bob;
        }

        // 0 torso
        compose(out[0], root, torso.jx, torso.jy, torso.jz, 0f, -torsoTwist, 0f, 1f);
        // 1 hips
        compose(out[1], root, hips.jx, hips.jy, hips.jz, 0f, -torsoTwist * 0.4f, 0f, 1f);
        // 2 head (counter-rotates slightly to look ahead)
        compose(out[2], root, head.jx, head.jy, head.jz,
                airTime ? -6f : (float) Math.sin(animPhase * 0.5f) * 2f,
                torsoTwist * 0.7f, 0f, 1f);
        // 3 left arm
        compose(out[3], root, leftArm.jx, leftArm.jy, leftArm.jz,
                (float) Math.toDegrees(leftArmSwing), 0f, -8f, 1f);
        // 4 right arm
        compose(out[4], root, rightArm.jx, rightArm.jy, rightArm.jz,
                (float) Math.toDegrees(rightArmSwing), 0f, 8f, 1f);
        // 5 left leg
        compose(out[5], root, leftLeg.jx, leftLeg.jy, leftLeg.jz,
                airTime ? 28f : (float) Math.toDegrees(swing * 0.9f), 0f, 0f, 1f);
        // 6 right leg
        compose(out[6], root, rightLeg.jx, rightLeg.jy, rightLeg.jz,
                airTime ? -14f : (float) Math.toDegrees(-swing * 0.9f), 0f, 0f, 1f);
        // 7 weapon follows the right arm
        compose(out[7], root, weapon.jx, weapon.jy, weapon.jz,
                (float) Math.toDegrees(rightArmSwing), 0f, 8f, 1f);
        // 8 cape trails behind, flaring with speed
        float capeAngle = -14f - moveAmount * 26f + (float) Math.sin(animPhase * 0.7f) * 4f;
        compose(out[8], root, cape.jx, cape.jy, cape.jz, capeAngle, 0f, 0f, 1f);
    }

    private static void compose(Mat4 out, Mat4 parent, float jx, float jy, float jz,
                                float rotXDeg, float rotYDeg, float rotZDeg, float scale) {
        Mat4 joint = Mat4.translation(jx, jy, jz);
        Mat4 rot = new Mat4();
        if (rotYDeg != 0f) {
            rot.setMultiply(Mat4.rotationY(rotYDeg), rot);
        }
        if (rotXDeg != 0f) {
            rot.setMultiply(Mat4.rotationX(rotXDeg), rot);
        }
        Mat4 local = new Mat4().setMultiply(joint, rot);
        out.setMultiply(parent, local);
    }

    /** Number of parts posed by {@link #pose}. */
    public static final int PART_COUNT = 9;
}
