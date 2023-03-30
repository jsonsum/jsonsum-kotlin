package org.jsonsum

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque

val TYPE_NULL = "n".toByteArray()
val TYPE_TRUE = "t".toByteArray()
val TYPE_FALSE = "f".toByteArray()
val TYPE_NUMBER = "i".toByteArray()
val TYPE_STRING = "s".toByteArray()
val TYPE_OBJECT = "o".toByteArray()
val TYPE_ARRAY_START = "[".toByteArray()
val TYPE_ARRAY_END = "]".toByteArray()

/**
 * jsonsum checksums json messages' content and structure. It ignores the
 * order of keys (by simply xoring all hashes of key:value pairs inside an
 * object) while verifying that the same key does not appear more than once
 * per object
 */
fun jsonsum(j: String, hasherFactory: () -> Hasher = { CRC32Hasher() }): Digest {
    val p = JsonFactory().createParser(j)
    val stack = ArrayDeque<Triple<FancyHasher, ByteArray, MutableSet<String>>>()
    var seen = mutableSetOf<String>()
    var sum = FancyHasher(hasherFactory())
    val stringSum = FancyHasher(hasherFactory())
    var objHash = ByteArray(sum.digestSize)
    var token = p.nextToken()
    while (token != null) {
        when (token) {
            JsonToken.VALUE_NULL -> sum.update(TYPE_NULL)
            JsonToken.VALUE_TRUE -> sum.update(TYPE_TRUE)
            JsonToken.VALUE_FALSE -> sum.update(TYPE_FALSE)
            JsonToken.VALUE_NUMBER_FLOAT, JsonToken.VALUE_NUMBER_INT -> {
                sum.update(TYPE_NUMBER)
                // encode number normalized to <significant digits>e<exponent>
                // (decimal-ascii-encoded, both possibly negative), so that 2
                // and 2.0 yield the same hash
                val dv = p.decimalValue.stripTrailingZeros() // automagically does the above
                sum.update(dv.unscaledValue().toString().toByteArray())
                sum.update("e".toByteArray())
                sum.update((-dv.scale()).toString().toByteArray())
            }

            JsonToken.FIELD_NAME, JsonToken.VALUE_STRING -> {
                if (token == JsonToken.FIELD_NAME) {
                    // a new key:value pair starts, xor the previous one's checksum (0 for the first pair) into objSum
                    objHash.xor(sum.digest.raw)
                    sum.reset()
                }
                sum.update(TYPE_STRING)
                stringSum.reset()
                stringSum.update(p.valueAsString.toByteArray())
                sum.update(stringSum.digest.raw)
                if (token == JsonToken.FIELD_NAME) {
                    if (!seen.add(p.valueAsString)) {
                        throw IllegalArgumentException("Duplicate field '${p.valueAsString}'")
                    }
                }
            }

            JsonToken.START_OBJECT -> {
                sum.update(TYPE_OBJECT)
                stack.addLast(Triple(sum, objHash, seen))
                sum = FancyHasher(hasherFactory())
                seen = mutableSetOf()
                objHash = ByteArray(sum.digestSize)
            }

            JsonToken.END_OBJECT -> {
                objHash.xor(sum.digest.raw)
                val innerHash = objHash
                stack.removeLast().let {
                    sum = it.first
                    objHash = it.second
                    seen = it.third
                }
                sum.update(innerHash)
            }

            JsonToken.START_ARRAY -> sum.update(TYPE_ARRAY_START)
            JsonToken.END_ARRAY -> sum.update(TYPE_ARRAY_END)
            else -> throw RuntimeException("unexpected token $token")
        }
        token = p.nextToken()
    }
    return sum.digest
}

private fun ByteArray.xor(other: ByteArray) {
    assert(this.size == other.size)
    for (i in this.indices) {
        this[i] = (this[i].toInt() xor other[i].toInt()).toByte()
    }
}

class FancyHasher(h: Hasher) : Hasher by h {
    private val bbInt = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)

    fun updateInt(i: Int) {
        bbInt.putInt(0, i)
        this.update(bbInt.array())
    }
}
