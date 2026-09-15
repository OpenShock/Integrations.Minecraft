package openshock.integrations.minecraft

//? if >=1.21.4 {
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.SpriteSet
import net.minecraft.core.particles.SimpleParticleType
import kotlin.math.atan2
import kotlin.math.sqrt

// The base class for a sprite particle was TextureSheetParticle until 1.21.11 folded it into
// SingleQuadParticle, which now takes the sprite in its constructor and asks for a Layer instead
// of a ParticleRenderType. Everything else about this file is the same on both.
//? if >=1.21.11 {
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.util.RandomSource
//?} else {
/*import net.minecraft.client.particle.TextureSheetParticle
*///?}

/**
 * One straight piece of a bolt: a streak that lies along the segment it was sent for.
 *
 * This is what stops the arcs reading as a row of dots. Vanilla has no particle shaped like a line
 * - its sparks are round blobs - so however carefully you space them, a bolt built out of them is
 * a dotted line and looks like scattered sparkle. Here each particle *is* a segment: the server
 * sends the midpoint and the vector from one end to the other, and this turns that into a streak
 * of the right length pointing the right way, so consecutive ones join into a continuous jagged
 * line.
 *
 * ### The velocity is not a velocity
 *
 * [ServerLevel.sendParticles] with a count of zero sends the three offsets as the particle's
 * speed, which is the only way to get three arbitrary numbers down to a client per particle
 * without a packet of our own. So [openshock.integrations.minecraft.ShockEffects] puts the
 * segment vector there instead: its length is the length of the streak and its direction is the
 * direction of the streak. The particle then stands perfectly still, which it has to - a streak
 * that drifts stops lining up with its neighbours and the bolt comes apart.
 *
 * ### Pointing it
 *
 * The quad always faces the camera, and [roll] spins it in screen space. So all that is needed is
 * the angle of the segment *as the camera sees it*: project the world direction onto the camera's
 * up and left vectors and take the arctangent. The texture streak runs along the quad's local Y,
 * which is screen-up, so rotating by that angle lays it along the segment.
 *
 * Worked out once per tick from the camera rather than per frame, which would need a render
 * override and three versions of one. A tick of lag on a rotation nobody is tracking is invisible,
 * and the particle only lives five of them.
 */
//? if >=1.21.11 {
class ShockArcParticle private constructor(
    level: ClientLevel,
    x: Double,
    y: Double,
    z: Double,
    segmentX: Double,
    segmentY: Double,
    segmentZ: Double,
    sprite: TextureAtlasSprite,
) : SingleQuadParticle(level, x, y, z, 0.0, 0.0, 0.0, sprite) {
//?} else {
/*class ShockArcParticle private constructor(
    level: ClientLevel,
    x: Double,
    y: Double,
    z: Double,
    segmentX: Double,
    segmentY: Double,
    segmentZ: Double,
    sprite: SpriteSet,
) : TextureSheetParticle(level, x, y, z, 0.0, 0.0, 0.0) {
*///?}

    /** The segment as a unit vector, kept for [aim]. */
    private val dirX: Double
    private val dirY: Double
    private val dirZ: Double

    /** Faded out over the last of these, so a strike decays rather than blinks off. */
    private val fadeFrom: Int

    init {
        val length = sqrt(segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ)
            .coerceAtLeast(0.001)

        dirX = segmentX / length
        dirY = segmentY / length
        dirZ = segmentZ / length

        // quadSize is the half-extent, so this makes the streak exactly as long as the segment,
        // plus a little so neighbours overlap instead of leaving a seam at every joint.
        quadSize = (length * 0.5 * 1.2).toFloat()

        // Just longer than the two ticks between bursts, so one set of arcs is still fading as
        // the next is struck and the flicker has no gaps in it. Any longer and they pile up into
        // a haze instead of reading as separate flashes.
        lifetime = LIFETIME
        fadeFrom = 1

        // Nothing moves it: no gravity, no friction, no collision, no drift.
        hasPhysics = false
        gravity = 0f
        friction = 1f

        aim()
    }

    override fun tick() {
        super.tick()

        if (age >= fadeFrom) {
            alpha = 1f - (age - fadeFrom).toFloat() / (lifetime - fadeFrom).toFloat()
        }

        aim()
    }

    /** Lays the streak along its segment, as seen from where the camera is now. */
    private fun aim() {
        // 26.2 dropped the get- prefix, and the private field it used to hide behind is still
        // there - so this has to name the method rather than let Kotlin pick a property.
        //? if >=26.2 {
        val camera: Camera = Minecraft.getInstance().gameRenderer.mainCamera()
        //?} else {
        /*val camera: Camera = Minecraft.getInstance().gameRenderer.getMainCamera()
        *///?}

        //? if >=1.21.11 {
        val up = camera.upVector()
        val left = camera.leftVector()
        //?} else {
        /*val up = camera.getUpVector()
        val left = camera.getLeftVector()
        *///?}

        val alongUp = dirX * up.x() + dirY * up.y() + dirZ * up.z()
        val alongLeft = dirX * left.x() + dirY * left.y() + dirZ * left.z()

        // Rotating the quad's local +Y by roll counter-clockwise gives (-sin, cos) in screen
        // space, and that has to line up with (right, up) - hence left rather than right on top.
        // A streak is symmetric end to end, so being half a turn out would not show either way.
        roll = atan2(alongLeft, alongUp).toFloat()
    }

    //? if >=1.21.11 {
    override fun getLayer(): SingleQuadParticle.Layer = SingleQuadParticle.Layer.TRANSLUCENT
    //?} else {
    /*override fun getRenderType(): ParticleRenderType = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
    *///?}

    companion object {
        /** A tick longer than the gap between bursts, so the flicker never leaves a blank frame. */
        private const val LIFETIME = 3
    }

    /**
     * Handed the sprite sheet by whichever loader is registering us - both of them supply one, and
     * this particle only ever uses the single frame in it.
     */
    class Provider(private val sprites: SpriteSet) : ParticleProvider<SimpleParticleType> {

        //? if >=1.21.11 {
        override fun createParticle(
            options: SimpleParticleType,
            level: ClientLevel,
            x: Double,
            y: Double,
            z: Double,
            segmentX: Double,
            segmentY: Double,
            segmentZ: Double,
            random: RandomSource,
        ): Particle = ShockArcParticle(
            level, x, y, z, segmentX, segmentY, segmentZ, sprites.get(random),
        )
        //?} else {
        /*override fun createParticle(
            options: SimpleParticleType,
            level: ClientLevel,
            x: Double,
            y: Double,
            z: Double,
            segmentX: Double,
            segmentY: Double,
            segmentZ: Double,
        ): Particle = ShockArcParticle(
            level, x, y, z, segmentX, segmentY, segmentZ, sprites,
        ).also { it.pickSprite(sprites) }
        *///?}
    }
}
//?}
