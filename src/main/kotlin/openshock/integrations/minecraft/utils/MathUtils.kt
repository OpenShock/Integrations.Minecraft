package openshock.integrations.minecraft.utils

class MathUtils {

    companion object {
        fun lerp(a: UShort, b: UShort, f: Float): UShort {
            return (a.toFloat() * (1.0 - f) + (b.toFloat() * f)).toUInt().toUShort()
        }

        fun lerp(a: Byte, b: Byte, f: Float): Byte {
            return (a * (1.0 - f) + (b * f)).toInt().toByte()
        }
    }
}