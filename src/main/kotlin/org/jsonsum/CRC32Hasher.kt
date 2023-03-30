package org.jsonsum

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class CRC32Hasher : Hasher {
    private val crc = CRC32()
    private val bb = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)

    override fun reset() = crc.reset()

    override fun update(data: ByteArray) = crc.update(data)

    override val digest: Digest
        get() {
            bb.putInt(0, crc.value.toInt())
            return Digest(bb.array().copyOf())
        }

    override val digestSize: Int
        get() = 4
}
