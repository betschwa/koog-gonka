package de.betchvaia.koog.gonka.wallet

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import de.betchvaia.koog.gonka.GonkaAuth
import de.betchvaia.koog.gonka.GonkaLLMProvider
import kotlinx.coroutines.flow.Flow

/**
 * Sibling to [de.betchvaia.koog.gonka.GonkaLLMClient] for wallet-based
 * ([GonkaAuth.Wallet]) auth — talking directly to the Gonka chain-native "devshard"
 * inference protocol instead of a centralized Broker.
 *
 * STATUS (this wave): identity-only. [address] and the underlying signer/address-derivation
 * primitives ([GonkaSigner], [GonkaBech32]) are fully implemented and tested. Actually
 * dispatching a chat completion is deliberately NOT implemented: [execute],
 * [executeStreaming], and [moderate] all throw [UnsupportedOperationException].
 *
 * WHY: Gonka's own `docs/consumer_setup.md` (`github.com/gonka-ai/gonka`) states that a
 * funded, on-chain-registered account that signs its own requests still gets rejected end
 * to end without going through a Broker or an allow-listed self-hosted gateway — there is no
 * self-serve path today. The actual protocol
 * (`github.com/gonka-ai/gonka/tree/main/devshard`) is a stateful, multi-host,
 * chain-bridged session protocol (`devshard/user/httpsession.go` needs a chain-queried
 * escrow, a redundant multi-host "group", local session persistence, and nonce/timeout
 * bookkeeping) — not a single signable REST call — and Gonka's own chain-bridge interface
 * (`devshard/bridge/interface.go`) is marked `// Phase 1: interface only, no
 * implementation.` in Gonka's own repo (verified against `main` on 2026-08-19). Implementing
 * dispatch now would mean guessing at an actively-evolving, partially-unimplemented-upstream
 * protocol; a wrong guess here doesn't just fail loudly, it risks misusing key material or
 * paying for unsettled/unverified inference. A future wave should revisit once
 * `MainnetBridge` has a real implementation upstream (re-verify the above citations against
 * current `main` first — the devshard protocol is versioned and moves quickly, e.g. tag
 * `release/v0.2.15-devshard-v4.0.1` from 2026-07-31), and should expect a session-oriented
 * API shape, not a drop-in replacement for
 * [ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient].
 */
public class GonkaWalletLLMClient(private val auth: GonkaAuth.Wallet) : LLMClient() {

    /** The `gonka1...` bech32 address derived from [auth]'s wallet key. */
    public val address: String = auth.address

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant =
        notImplemented()

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
        notImplemented()

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = notImplemented()

    override fun llmProvider(): LLMProvider = GonkaLLMProvider

    /**
     * Closes [auth], best-effort zeroing its retained private-key material (see
     * [GonkaAuth.Wallet]'s KDoc for the guarantee's limits). No HTTP client or session
     * exists yet to close in this wave — the key material is the only thing this class owns.
     */
    override fun close() {
        auth.close()
    }

    private fun notImplemented(): Nothing = throw UnsupportedOperationException(
        "GonkaWalletLLMClient does not yet dispatch inference requests over Gonka's devshard " +
            "protocol - it is not self-serve today and is a stateful, multi-host, chain-bridged " +
            "session protocol, not a signable REST call. See this class's KDoc for citations.",
    )
}
