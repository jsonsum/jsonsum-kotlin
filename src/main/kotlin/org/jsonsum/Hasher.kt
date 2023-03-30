package org.jsonsum

interface Hasher {
    fun reset()
    fun update(data: ByteArray)
    val digest: Digest
    val digestSize: Int
}
