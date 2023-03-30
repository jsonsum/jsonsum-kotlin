package org.jsonsum

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.io.JsonEOFException
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
    ).map { t ->
        DynamicTest.dynamicTest(t.name) {
            val c1 = jsonsum(t.left)
            val c2 = jsonsum(t.right)
            if (t.equal) {
                assertEquals(c1, c2)
            } else {
                assertNotEquals(c1, c2)
            }
        }
    }

    @TestFactory
    fun checksum(): List<DynamicTest> = listOf(
        Pair("""{}""", 3711965057u),
        Pair("""[]""", 223132457u),
        Pair("""""""", 2018536706u),
        Pair(""""hi"""", 3572422966u),
        Pair("""0""", 2102537547u),
        Pair("""0.0""", 2102537547u),
        Pair("""0e0""", 2102537547u),
        Pair("""true""", 2238339752u),
        Pair("""false""", 1993550816u),
        Pair("""null""", 2013832146u),
        Pair("""{"hi":1,"ho":2}""", 1308407541u),
        Pair("""[[],[2]]""", 2077149373u),
        Pair("""[[[2]]]""", 3539679565u),
        Pair("""[{},"ho",{"hi":2}]""", 2520557453u),
        Pair("""[{"ho":{"hi":2}}]""", 4250229204u),
        Pair("""[{"hi":{"hi":2}}]""", 2963545766u),
        Pair("""{"1":1,"2":2}""", 593357170u),
        Pair("""[{"1":1},{"1":1}]""", 2843930034u),
    ).map { (j, sum) ->
        DynamicTest.dynamicTest(j) {
            assertEquals(sum, jsonsum(j).uint)
        }
    }

    @TestFactory
    fun allTheSame(): List<DynamicTest> = listOf(
        Pair(listOf("0", "0.0", "0e0", "0e1", "0e100", "0.0e100", "-0", "-0.0", "-0.0e1"), 2102537547u),
        Pair(listOf("1", "1.0", "10.0e-1", "0.1e1", "10000000000000000000000000000000000000000e-40"), 2089830268u),
        Pair(listOf(/*"\"\t\"",*/ "\"\\t\"", "\"\\u0009\""), 3529881795u), // unescaped tab is not allowed
        Pair(listOf("\"/\"", "\"\\/\""), 3541612589u),
    ).flatMap { (nums, sum) ->
        nums.map { n ->
            DynamicTest.dynamicTest(n.toString()) {
                assertEquals(sum, jsonsum(n).uint)
            }
        }
    }


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
