package org.jsonsum

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
fun jsonsum(j: String, hasherFactory: () -> Hasher = { Sha256Hasher() }): Digest {
    val p = JsonFactory().createParser(j)
    p.nextToken()
    val h = hasherFactory()
    jsonsum_impl(p, hasherFactory, h)
    return h.digest
}

private fun jsonsum_impl(
    p: JsonParser,
    hasherFactory: () -> Hasher = { Sha256Hasher() },
    sum: Hasher = FancyHasher(hasherFactory())
) {
    when (p.currentToken) {
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
            sum.update(TYPE_STRING)
            val stringSum = FancyHasher(hasherFactory())
            stringSum.update(p.valueAsString.toByteArray())
            sum.update(stringSum.digest.raw)
        }

        JsonToken.START_OBJECT -> {
            sum.update(TYPE_OBJECT)
            val objHash = ByteArray(sum.digestSize)
            val h = FancyHasher(hasherFactory())
            val seen = mutableSetOf<String>()
            p.nextToken()
            while (p.currentToken != JsonToken.END_OBJECT) {
                val field = p.text
                if (!seen.add(field)) {
                    throw IllegalArgumentException("Duplicate field '${field}'")
                }
                jsonsum_impl(p, hasherFactory, h)
                p.nextToken()
                jsonsum_impl(p, hasherFactory, h)
                p.nextToken()
                objHash.xor(h.digest.raw)
                h.reset()
            }
            sum.update(objHash)
        }

        JsonToken.START_ARRAY -> {
            sum.update(TYPE_ARRAY_START)
            p.nextToken()
            while (p.currentToken != JsonToken.END_ARRAY) {
                jsonsum_impl(p, hasherFactory, sum)
                p.nextToken()
            }
            sum.update(TYPE_ARRAY_END)
        }
        else -> throw RuntimeException("unexpected token ${p.currentToken}")
    }
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
