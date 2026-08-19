package de.betchvaia.koog.gonka.wallet

import fr.acinq.secp256k1.Secp256k1
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.security.MessageDigest

private const val TOY_PRIVATE_KEY_HEX = "0000000000000000000000000000000000000000000000000000000000000001"

private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

class GonkaDevshardRequestAuthTest : StringSpec({

    "HEADER_SIGNATURE and HEADER_TIMESTAMP match Gonka's devshard wire contract exactly" {
        GonkaDevshardRequestAuth.HEADER_SIGNATURE shouldBe "X-Devshard-Signature"
        GonkaDevshardRequestAuth.HEADER_TIMESTAMP shouldBe "X-Devshard-Timestamp"
    }

    "signatureMessage matches sha256 of an independently-built escrowId || body || bigEndianUint64(ts) layout" {
        val escrowId = "escrow-42"
        val body = "{\"model\":\"gonka-test\"}".encodeToByteArray()
        val timestamp = 1_733_000_000L

        val raw = escrowId.encodeToByteArray() +
            body +
            ByteBuffer.allocate(8).putLong(timestamp).array()
        val expected = sha256(raw)

        GonkaDevshardRequestAuth.signatureMessage(escrowId, body, timestamp) shouldBe expected
    }

    "signatureMessage layout changes if escrowId, body or timestamp change" {
        val body = "payload".encodeToByteArray()
        val base = GonkaDevshardRequestAuth.signatureMessage("escrow-a", body, 1000L)

        base.contentEquals(GonkaDevshardRequestAuth.signatureMessage("escrow-b", body, 1000L)) shouldBe false
        base.contentEquals(GonkaDevshardRequestAuth.signatureMessage("escrow-a", body, 1001L)) shouldBe false
        base.contentEquals(
            GonkaDevshardRequestAuth.signatureMessage("escrow-a", "other".encodeToByteArray(), 1000L),
        ) shouldBe false
    }

    "sign composes signatureMessage with GonkaSigner.sign and recovers to the signer's pubkey" {
        GonkaSigner.fromPrivateKeyHex(TOY_PRIVATE_KEY_HEX).use { signer ->
            val escrowId = "escrow-99"
            val body = "{}".encodeToByteArray()
            val timestamp = 1_733_000_500L

            val signature = GonkaDevshardRequestAuth.sign(signer, escrowId, body, timestamp)
            signature.size shouldBe 65

            val expectedHash = sha256(GonkaDevshardRequestAuth.signatureMessage(escrowId, body, timestamp))
            val compact = signature.copyOfRange(0, 64)
            val recId = signature[64].toInt()
            val recovered = Secp256k1.ecdsaRecover(compact, expectedHash, recId)
            Secp256k1.pubKeyCompress(recovered) shouldBe signer.compressedPublicKey
        }
    }

    "sign ultimately signs sha256(sha256(escrowId || body || timestamp)), matching Gonka's double-hash protocol" {
        GonkaSigner.fromPrivateKeyHex(TOY_PRIVATE_KEY_HEX).use { signer ->
            val escrowId = "escrow-99"
            val body = "{}".encodeToByteArray()
            val timestamp = 1_733_000_500L

            val raw = escrowId.encodeToByteArray() +
                body +
                ByteBuffer.allocate(8).putLong(timestamp).array()
            val expectedFinalDigest = sha256(sha256(raw))

            val signature = GonkaDevshardRequestAuth.sign(signer, escrowId, body, timestamp)
            val compact = signature.copyOfRange(0, 64)
            val recId = signature[64].toInt()
            val recovered = Secp256k1.ecdsaRecover(compact, expectedFinalDigest, recId)

            Secp256k1.pubKeyCompress(recovered) shouldBe signer.compressedPublicKey
        }
    }
})
