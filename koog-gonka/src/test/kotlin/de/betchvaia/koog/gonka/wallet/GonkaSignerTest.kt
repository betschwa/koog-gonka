package de.betchvaia.koog.gonka.wallet

import fr.acinq.secp256k1.Secp256k1
import fr.acinq.secp256k1.Secp256k1Exception
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import java.security.MessageDigest

/** Toy key `1` — same key BIP-173's own published test vectors use. Not a funded/real key. */
private const val TOY_PRIVATE_KEY_HEX = "0000000000000000000000000000000000000000000000000000000000000001"

private const val GOLDEN_ADDRESS = "gonka1w508d6qejxtdg4y5r3zarvary0c5xw7k2gsyg6"
private const val GOLDEN_COMPRESSED_PUBKEY_HEX =
    "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"

private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

class GonkaSignerTest : StringSpec({

    "fromPrivateKeyHex derives the golden address and compressed pubkey for the toy key" {
        GonkaSigner.fromPrivateKeyHex(TOY_PRIVATE_KEY_HEX).use { signer ->
            signer.address shouldBe GOLDEN_ADDRESS
            signer.compressedPublicKeyHex shouldBe GOLDEN_COMPRESSED_PUBKEY_HEX
        }
    }

    "fromPrivateKeyHex accepts a 0x-prefixed key and derives the same address" {
        GonkaSigner.fromPrivateKeyHex("0x$TOY_PRIVATE_KEY_HEX").use { signer ->
            signer.address shouldBe GOLDEN_ADDRESS
        }
    }

    "fromPrivateKeyHex rejects a non-hex string without leaking it in the message" {
        val secret = "not-hex-at-all-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"
        val ex = shouldThrow<IllegalArgumentException> { GonkaSigner.fromPrivateKeyHex(secret) }
        ex.message?.shouldNotContain(secret)
    }

    "fromPrivateKeyHex rejects a key that is one hex digit too short" {
        shouldThrow<IllegalArgumentException> {
            GonkaSigner.fromPrivateKeyHex(TOY_PRIVATE_KEY_HEX.drop(1))
        }
    }

    "fromPrivateKeyHex rejects a key that is one hex digit too long" {
        shouldThrow<IllegalArgumentException> {
            GonkaSigner.fromPrivateKeyHex("0$TOY_PRIVATE_KEY_HEX")
        }
    }

    "fromPrivateKeyHex rejects the all-zero key" {
        shouldThrow<IllegalArgumentException> {
            GonkaSigner.fromPrivateKeyHex("0".repeat(64))
        }
    }

    "fromPrivateKeyHex exception message never contains the offending input" {
        val secret = "z".repeat(64)
        val ex = shouldThrow<IllegalArgumentException> { GonkaSigner.fromPrivateKeyHex(secret) }
        ex.message?.shouldNotContain(secret)
    }

    "sign produces a 65-byte signature that recovers to the signer's own compressed pubkey" {
        GonkaSigner.fromPrivateKeyHex(TOY_PRIVATE_KEY_HEX).use { signer ->
            val message = "hello gonka".encodeToByteArray()
            val signature = signer.sign(message)
            signature.size shouldBe 65

            val hash = sha256(message)
            val compact = signature.copyOfRange(0, 64)
            val recId = signature[64].toInt()
            val recovered = Secp256k1.ecdsaRecover(compact, hash, recId)
            Secp256k1.pubKeyCompress(recovered) shouldBe signer.compressedPublicKey
        }
    }

    "sign is deterministic for the same key and message" {
        GonkaSigner.fromPrivateKeyHex(TOY_PRIVATE_KEY_HEX).use { signer ->
            val message = "deterministic?".encodeToByteArray()
            signer.sign(message) shouldBe signer.sign(message)
        }
    }

    "sign is message-bound: tampering with the message changes the recovered address" {
        GonkaSigner.fromPrivateKeyHex(TOY_PRIVATE_KEY_HEX).use { signer ->
            val message = "original message".encodeToByteArray()
            val signature = signer.sign(message)
            val compact = signature.copyOfRange(0, 64)
            val recId = signature[64].toInt()

            val tampered = message.copyOf()
            tampered[0] = (tampered[0] + 1).toByte()
            val tamperedHash = sha256(tampered)

            val recoveredFromTampered = Secp256k1.ecdsaRecover(compact, tamperedHash, recId)
            Secp256k1.pubKeyCompress(recoveredFromTampered) shouldNotBe signer.compressedPublicKey
        }
    }

    "close zeroes the private key: a subsequent sign fails because the zeroed key is no longer valid" {
        val signer = GonkaSigner.fromPrivateKeyHex(TOY_PRIVATE_KEY_HEX)
        signer.close()

        // The all-zero byte array left behind by close() is not a valid secp256k1 private
        // key, so libsecp256k1 itself rejects it - proof the array was actually mutated,
        // surfaced as the library's own exception type rather than a silent bad signature.
        shouldThrow<Secp256k1Exception> {
            signer.sign("post-close sign".encodeToByteArray())
        }
    }
})
