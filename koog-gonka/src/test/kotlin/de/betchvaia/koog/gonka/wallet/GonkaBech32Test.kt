package de.betchvaia.koog.gonka.wallet

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Golden vector: private key `1`'s compressed pubkey and hash160 are the same ones used in
 * BIP-173's own published test vectors (for `bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4`),
 * independently re-derived in this wave's plan via Python (`ecdsa` + `hashlib`) and again
 * cross-checked here.
 */
private val HASH160_FOR_PRIVKEY_1: ByteArray =
    "751e76e8199196d454941c45d1b3a323f1433bd6".hexToByteArray()

class GonkaBech32Test : StringSpec({

    "encode with hrp gonka produces the golden gonka1... address" {
        val fiveBit = GonkaBech32.convertBits(HASH160_FOR_PRIVKEY_1, fromBits = 8, toBits = 5, pad = true)
        GonkaBech32.encode("gonka", fiveBit) shouldBe "gonka1w508d6qejxtdg4y5r3zarvary0c5xw7k2gsyg6"
    }

    "encode with hrp bc and a witness-v0 prefix matches the official BIP-173 test address" {
        val fiveBit = GonkaBech32.convertBits(HASH160_FOR_PRIVKEY_1, fromBits = 8, toBits = 5, pad = true)
        val witnessData = byteArrayOf(0) + fiveBit
        GonkaBech32.encode("bc", witnessData) shouldBe "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
    }

    "convertBits round-trips 8-bit bytes through 5-bit groups and back" {
        val original = byteArrayOf(0x00, 0x11, 0x22.toByte(), 0xff.toByte(), 0x7f, 0x80.toByte())
        val fiveBit = GonkaBech32.convertBits(original, fromBits = 8, toBits = 5, pad = true)
        fiveBit.forEach { it.toInt() shouldBe (it.toInt() and 0x1f) }
        val back = GonkaBech32.convertBits(fiveBit, fromBits = 5, toBits = 8, pad = false)
        back shouldBe original
    }

    "convertBits round-trips a single byte" {
        val original = byteArrayOf(0x42)
        val fiveBit = GonkaBech32.convertBits(original, fromBits = 8, toBits = 5, pad = true)
        val back = GonkaBech32.convertBits(fiveBit, fromBits = 5, toBits = 8, pad = false)
        back shouldBe original
    }

    "convertBits round-trips an empty byte array" {
        GonkaBech32.convertBits(ByteArray(0), fromBits = 8, toBits = 5, pad = true) shouldBe ByteArray(0)
    }
})
