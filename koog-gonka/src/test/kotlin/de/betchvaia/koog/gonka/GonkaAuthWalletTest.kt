package de.betchvaia.koog.gonka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private const val TOY_PRIVATE_KEY_HEX = "0000000000000000000000000000000000000000000000000000000000000001"
private const val OTHER_PRIVATE_KEY_HEX = "0000000000000000000000000000000000000000000000000000000000000002"
private const val GOLDEN_ADDRESS = "gonka1w508d6qejxtdg4y5r3zarvary0c5xw7k2gsyg6"

class GonkaAuthWalletTest : StringSpec({

    "constructs successfully and derives the golden address" {
        val wallet = GonkaAuth.Wallet(privateKeyHex = TOY_PRIVATE_KEY_HEX, nodeUrl = "http://node.test:8000")
        wallet.address shouldBe GOLDEN_ADDRESS
    }

    "rejects an invalid-length private key" {
        shouldThrow<IllegalArgumentException> {
            GonkaAuth.Wallet(privateKeyHex = TOY_PRIVATE_KEY_HEX.drop(1), nodeUrl = "http://node.test:8000")
        }
    }

    "rejects a blank nodeUrl" {
        shouldThrow<IllegalArgumentException> {
            GonkaAuth.Wallet(privateKeyHex = TOY_PRIVATE_KEY_HEX, nodeUrl = "   ")
        }
    }

    "toString does not contain the private key hex but contains address and nodeUrl" {
        val wallet = GonkaAuth.Wallet(privateKeyHex = TOY_PRIVATE_KEY_HEX, nodeUrl = "http://node.test:8000")
        val text = wallet.toString()
        text.shouldNotContain(TOY_PRIVATE_KEY_HEX)
        text shouldContain GOLDEN_ADDRESS
        text shouldContain "http://node.test:8000"
    }

    "two wallets from the same key and nodeUrl are equal with matching hashCode" {
        val a = GonkaAuth.Wallet(privateKeyHex = TOY_PRIVATE_KEY_HEX, nodeUrl = "http://node.test:8000")
        val b = GonkaAuth.Wallet(privateKeyHex = TOY_PRIVATE_KEY_HEX, nodeUrl = "http://node.test:8000")
        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
    }

    "wallets with the same key but different nodeUrl are not equal" {
        val a = GonkaAuth.Wallet(privateKeyHex = TOY_PRIVATE_KEY_HEX, nodeUrl = "http://node-a.test:8000")
        val b = GonkaAuth.Wallet(privateKeyHex = TOY_PRIVATE_KEY_HEX, nodeUrl = "http://node-b.test:8000")
        (a == b) shouldBe false
    }

    "wallets with different keys are not equal even with the same nodeUrl" {
        val a = GonkaAuth.Wallet(privateKeyHex = TOY_PRIVATE_KEY_HEX, nodeUrl = "http://node.test:8000")
        val b = GonkaAuth.Wallet(privateKeyHex = OTHER_PRIVATE_KEY_HEX, nodeUrl = "http://node.test:8000")
        (a == b) shouldBe false
    }

    "close does not throw and is idempotent when called twice" {
        val wallet = GonkaAuth.Wallet(privateKeyHex = TOY_PRIVATE_KEY_HEX, nodeUrl = "http://node.test:8000")
        wallet.close()
        wallet.close()
    }

    "GonkaAuth.ApiKey still compiles and behaves unchanged" {
        val apiKey = GonkaAuth.ApiKey(apiKey = "test-key")
        apiKey.apiKey shouldBe "test-key"
        apiKey.baseUrl shouldBe GonkaAuth.DEFAULT_BROKER_URL
    }
})
