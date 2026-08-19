package de.betchvaia.koog.gonka.wallet

import fr.acinq.secp256k1.Secp256k1
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import java.security.MessageDigest
import java.util.Arrays

/**
 * secp256k1 key handling for [de.betchvaia.koog.gonka.GonkaAuth.Wallet] — address derivation
 * and the recoverable-ECDSA signing primitive, ported from Gonka's own
 * `devshard/signing/secp256k1.go` (`github.com/gonka-ai/gonka`, verified against `main` on
 * 2026-08-19):
 *
 * ```
 * addressFromCompressed(compressed) = bech32.Encode("gonka", ConvertBits(ripemd160(sha256(compressedPubkey)), 8, 5, true))
 * Sign(message) = crypto.Sign(sha256(message), privkey)   // go-ethereum's crypto package, a libsecp256k1 wrapper
 * ```
 *
 * [sign] is a faithful port of `Secp256k1Signer.Sign` above — it is NOT currently reachable
 * from any network dispatch path; see
 * [de.betchvaia.koog.gonka.wallet.GonkaWalletLLMClient]'s KDoc for why.
 *
 * SECURITY: [privateKey] is held as a `ByteArray` for this object's lifetime and is never
 * logged or included in exception messages. [close] best-effort zeroes it; see the caveats
 * on [de.betchvaia.koog.gonka.GonkaAuth.Wallet] about the limits of that guarantee on the JVM.
 */
internal class GonkaSigner private constructor(private val privateKey: ByteArray) : AutoCloseable {

    /** 33-byte compressed secp256k1 public key. */
    internal val compressedPublicKey: ByteArray = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privateKey))

    internal val compressedPublicKeyHex: String get() = compressedPublicKey.toHexString()

    /** The `gonka1...` bech32 address derived from [compressedPublicKey]. */
    internal val address: String by lazy {
        val sha256 = sha256(compressedPublicKey)
        val hash160 = ripemd160(sha256)
        val fiveBit = GonkaBech32.convertBits(hash160, fromBits = 8, toBits = 5, pad = true)
        GonkaBech32.encode("gonka", fiveBit)
    }

    /**
     * Returns a 65-byte recoverable ECDSA signature: `r(32) || s(32) || recoveryId(1)`.
     *
     * Mirrors Gonka's `Secp256k1Signer.Sign`: the message is hashed with SHA-256 exactly
     * once here; callers that pass in an already-hashed digest (e.g.
     * [GonkaDevshardRequestAuth.signatureMessage]'s output) will end up with the intended
     * double-hash — that is not a bug to "simplify" away, see [GonkaDevshardRequestAuth]'s KDoc.
     *
     * ACINQ's [Secp256k1.sign] returns a 64-byte compact `(r, s)` signature with no recovery
     * id. To match Gonka's 65-byte `r||s||v` format (needed for `Ecrecover`-style
     * verification, as go-ethereum's `crypto.Sign` produces), this brute-forces
     * `recid ∈ {0, 1}` via [Secp256k1.ecdsaRecover] and picks the id whose recovered public
     * key matches this signer's own [compressedPublicKey] — the standard construction other
     * language bindings use to interop with go-ethereum's `crypto.Sign`/`Ecrecover`, not a
     * novel scheme.
     */
    internal fun sign(message: ByteArray): ByteArray {
        val hash = sha256(message)
        val compact = Secp256k1.sign(hash, privateKey)
        val recId = (0..1).firstOrNull { candidate ->
            val recovered = Secp256k1.ecdsaRecover(compact, hash, candidate)
            Secp256k1.pubKeyCompress(recovered).contentEquals(compressedPublicKey)
        } ?: error("Unable to determine recovery id for signature - this should never happen for a valid key/signature pair")
        return compact + byteArrayOf(recId.toByte())
    }

    /**
     * Best-effort zeroing of [privateKey]. The JVM offers no hard guarantee this actually
     * clears every copy from memory (immutable `String`s the caller may have kept, JIT/GC
     * temporaries, and the JNI-backed secp256k1 library's own internal copies are outside
     * this class's control) — see the caveats on [de.betchvaia.koog.gonka.GonkaAuth.Wallet].
     *
     * A [sign] call after [close] does not silently produce a bad signature: the zeroed
     * byte array is not a valid secp256k1 private key, so libsecp256k1 itself rejects it and
     * [sign] propagates a [fr.acinq.secp256k1.Secp256k1Exception] — verified empirically by
     * this class's test suite, not merely assumed.
     */
    override fun close() {
        Arrays.fill(privateKey, 0)
    }

    internal companion object {

        /**
         * @throws IllegalArgumentException if [privateKeyHex] is not exactly 64 hex digits
         *   (optionally `0x`/`0X`-prefixed), is not valid hexadecimal, or is not a valid
         *   secp256k1 private key (zero, or greater than or equal to the curve order). Never
         *   includes [privateKeyHex] or any prefix/suffix of it in the exception message.
         */
        internal fun fromPrivateKeyHex(privateKeyHex: String): GonkaSigner {
            val trimmed = privateKeyHex.removePrefix("0x").removePrefix("0X")
            require(trimmed.length == 64) {
                "Invalid Gonka wallet private key: expected exactly 64 hex digits (32 bytes), got ${trimmed.length}"
            }
            val bytes = try {
                trimmed.hexToByteArray()
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid Gonka wallet private key: not valid hexadecimal", e)
            }
            require(Secp256k1.secKeyVerify(bytes)) {
                "Invalid Gonka wallet private key: not a valid secp256k1 private key (zero, or >= curve order)"
            }
            return GonkaSigner(bytes)
        }

        private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

        private fun ripemd160(data: ByteArray): ByteArray {
            val digest = RIPEMD160Digest()
            digest.update(data, 0, data.size)
            val output = ByteArray(digest.digestSize)
            digest.doFinal(output, 0)
            return output
        }
    }
}
