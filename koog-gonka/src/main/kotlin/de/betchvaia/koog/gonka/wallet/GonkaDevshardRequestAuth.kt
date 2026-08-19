package de.betchvaia.koog.gonka.wallet

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Wire-level request-signing primitive for Gonka's devshard host protocol, ported from
 * `devshard/transport/auth.go` (`github.com/gonka-ai/gonka`, verified against `main` on
 * 2026-08-19 — re-verify before relying on this; the devshard protocol is versioned and
 * moves quickly, e.g. tag `release/v0.2.15-devshard-v4.0.1` from 2026-07-31):
 *
 * ```
 * raw     = escrowId (UTF-8 bytes) || body || bigEndianUint64(unixTimestampSeconds)
 * message = sha256(raw)
 * ```
 *
 * `signatureMessage()` returns `message` above — i.e. it is already SHA-256-hashed, exactly
 * like Go's `signatureMessage()`. [GonkaSigner.sign] then hashes its input a *second* time
 * (mirroring go-ethereum's `crypto.Sign`, which `Secp256k1Signer.Sign` delegates to), so the
 * final digest that actually gets signed is `sha256(sha256(raw))` — see [GonkaSigner.sign]'s
 * KDoc on why that double-hash is intentional and must be preserved end-to-end.
 *
 * Headers `X-Devshard-Signature` / `X-Devshard-Timestamp` carry the resulting signature and
 * timestamp; the host protocol tolerates roughly ±30s of clock drift.
 *
 * NOT reachable from [GonkaWalletLLMClient] or anywhere else public in this wave: every real
 * use requires a live `escrowId`, which only exists after opening a devshard session against
 * the chain — protocol machinery this wave does not implement (see [GonkaWalletLLMClient]'s
 * KDoc for the full rationale). Kept internal and tested so a future wave has a verified
 * building block.
 */
internal object GonkaDevshardRequestAuth {

    internal const val HEADER_SIGNATURE: String = "X-Devshard-Signature"
    internal const val HEADER_TIMESTAMP: String = "X-Devshard-Timestamp"

    /**
     * Reproduces Go's `signatureMessage()` exactly: SHA-256 of `escrowId` (UTF-8) followed by
     * `body` followed by `timestampSeconds` as an 8-byte big-endian unsigned integer. The
     * returned value is already a SHA-256 digest — [sign] passes it into [GonkaSigner.sign],
     * which hashes it again, so the effective digest that gets signed is
     * `sha256(sha256(escrowId || body || timestampSeconds))`, matching the real protocol.
     */
    internal fun signatureMessage(escrowId: String, body: ByteArray, timestampSeconds: Long): ByteArray {
        val escrowIdBytes = escrowId.encodeToByteArray()
        val timestampBytes = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(timestampSeconds).array()
        val raw = escrowIdBytes + body + timestampBytes
        return MessageDigest.getInstance("SHA-256").digest(raw)
    }

    /** Composes [signatureMessage] with [GonkaSigner.sign] into a 65-byte recoverable signature. */
    internal fun sign(signer: GonkaSigner, escrowId: String, body: ByteArray, timestampSeconds: Long): ByteArray =
        signer.sign(signatureMessage(escrowId, body, timestampSeconds))
}
