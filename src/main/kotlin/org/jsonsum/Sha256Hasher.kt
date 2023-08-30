package org.jsonsum

import java.security.MessageDigest

open class Sha256Hasher : Hasher {
    private val md = MessageDigest.getInstance("SHA-256")
    private var valid = true

    override fun reset() {
        md.reset()
        valid = true
    }

    override fun update(data: ByteArray) {
        if (!valid) {
            throw IllegalStateException("MessageDigest was read out and must be reset")
        }
        md.update(data)
    }

    override val digest: Digest
        get() {
            if (!valid) {
                throw IllegalStateException("MessageDigest was read out and must be reset")
            }
            valid = false
            return Digest(md.digest())
        }

    override val digestSize: Int
        get() = md.digestLength
}
