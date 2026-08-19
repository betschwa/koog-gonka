package de.betchvaia.koog.gonka.wallet

import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import de.betchvaia.koog.gonka.GonkaAuth
import de.betchvaia.koog.gonka.GonkaLLMProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking

private const val TOY_PRIVATE_KEY_HEX = "0000000000000000000000000000000000000000000000000000000000000001"
private const val GOLDEN_ADDRESS = "gonka1w508d6qejxtdg4y5r3zarvary0c5xw7k2gsyg6"

private fun testWalletAuth(): GonkaAuth.Wallet =
    GonkaAuth.Wallet(privateKeyHex = TOY_PRIVATE_KEY_HEX, nodeUrl = "http://node.test:8000")

private fun testWalletPrompt(): Prompt = Prompt.build(id = "wallet-test-prompt") { user("Hello") }

private fun testWalletModel(): LLModel = LLModel(
    provider = GonkaLLMProvider,
    id = "gonka-wallet-test-model",
    capabilities = listOf(LLMCapability.Completion),
)

class GonkaWalletLLMClientTest : StringSpec({

    "address matches the golden vector derived from the wallet's key" {
        val client = GonkaWalletLLMClient(testWalletAuth())
        client.address shouldBe GOLDEN_ADDRESS
        client.close()
    }

    "execute throws UnsupportedOperationException mentioning devshard" {
        val client = GonkaWalletLLMClient(testWalletAuth())
        val ex = shouldThrow<UnsupportedOperationException> {
            runBlocking { client.execute(testWalletPrompt(), testWalletModel()) }
        }
        ex.message shouldContain "devshard"
        client.close()
    }

    "executeStreaming throws UnsupportedOperationException mentioning devshard" {
        val client = GonkaWalletLLMClient(testWalletAuth())
        val ex = shouldThrow<UnsupportedOperationException> {
            client.executeStreaming(testWalletPrompt(), testWalletModel())
        }
        ex.message shouldContain "devshard"
        client.close()
    }

    "moderate throws UnsupportedOperationException mentioning devshard" {
        val client = GonkaWalletLLMClient(testWalletAuth())
        val ex = shouldThrow<UnsupportedOperationException> {
            runBlocking { client.moderate(testWalletPrompt(), testWalletModel()) }
        }
        ex.message shouldContain "devshard"
        client.close()
    }

    "models (inherited default) still throws UnsupportedOperationException" {
        val client = GonkaWalletLLMClient(testWalletAuth())
        shouldThrow<UnsupportedOperationException> {
            runBlocking { client.models() }
        }
        client.close()
    }

    "llmProvider returns GonkaLLMProvider" {
        val client = GonkaWalletLLMClient(testWalletAuth())
        client.llmProvider() shouldBe GonkaLLMProvider
        client.close()
    }

    "close does not throw and is idempotent when called twice" {
        val client = GonkaWalletLLMClient(testWalletAuth())
        client.close()
        client.close()
    }
})
