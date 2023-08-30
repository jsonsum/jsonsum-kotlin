package org.jsonsum

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.io.JsonEOFException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder

class JsonsumTest {
    data class EqualityTestData(
        val name: String,
        val left: String,
        val right: String,
        val equal: Boolean,
    )

    @TestFactory
    fun equality(): List<DynamicTest> = listOf(
        EqualityTestData("Different key order gives the same checksum", """{"hi":1,"ho":2}""", """{"ho":2,"hi":1}""", true),
        EqualityTestData("Different array nesting gives different checksums", """[[],[2]]""", """[[[2]]]""", false),
        EqualityTestData("Different object nesting gives different checksums", """[{},"ho",{"hi":2}]""", """[{"ho":{"hi":2}}]""", false),
        EqualityTestData("Encoding of numbers should not matter", """2""", """2.0""", true),
        EqualityTestData("Repeated fields in arrays should not cancel", """[{"1":1},{"1":1},{"1":1}]""", """[{"1":1}]""", false),
        EqualityTestData("Identical values should not cancel each other out", """{"a":1,"b":1}""", """{"a":2,"b":2}""", false),
        EqualityTestData("Identical subobjects should not cancel each other out", """{"a":{"c":{"hi":"ho"}},"b":{"d":{"x":1}}}""", """{"a":{"c":{"hi":"ho","extra":1}},"b":{"d":{"x":1,"extra":1}}}""", false),
    ).map { t ->
        DynamicTest.dynamicTest(t.name) {
            val c1 = jsonsum(t.left)
            val c2 = jsonsum(t.right)
            println("${t.name}: ${c1.hex}   ${c2.hex}")
            if (t.equal) {
                assertEquals(c1, c2)
            } else {
                assertNotEquals(c1, c2)
            }
        }
    }

    @TestFactory
    fun checksum(): List<DynamicTest> = loadJson("testdata.json")
        .flatMap { (name, inputs, sum) ->
            inputs.map { input ->
                DynamicTest.dynamicTest(name) {
                    assertEquals(sum, jsonsum(input).hex)
                }
            }
        }

    data class TestVector(
        val name: String,
        val inputs: List<String>,
        val sha256: String,
    )

    private fun loadJson(filename: String) = jacksonObjectMapper()
        .readValue<List<TestVector>>(
            javaClass.getResource("/$filename")!!
        )


    @TestFactory
    fun invalidJson() = listOf(
        Pair("""{"test":"hi"""", JsonEOFException::class.java),
        Pair("""{"test":}""", JsonParseException::class.java),
        Pair("""{"1":1,"2":2,"1":1}""", IllegalArgumentException::class.java),
    ).map { t ->
        DynamicTest.dynamicTest(t.first) {
            assertThrows(t.second) {
                jsonsum(t.first)
            }
        }
    }

    @Test
    fun unsignedEncoding() {
        val bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(0xffffffffL.toInt())
        assertArrayEquals(byteArrayOf(-1, -1, -1, -1), bb.array())
    }
}
