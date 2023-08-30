package org.jsonsum

import java.nio.ByteBuffer
import java.util.*

data class Digest(val raw: ByteArray) {
    val hex
        get() = HexFormat.of().formatHex(raw)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Digest

        if (!raw.contentEquals(other.raw)) return false

        return true
    }

    override fun hashCode(): Int {
        return raw.contentHashCode()
    }
}
