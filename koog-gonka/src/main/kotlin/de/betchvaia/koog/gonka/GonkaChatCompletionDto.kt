package de.betchvaia.koog.gonka

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMStreamResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request envelope for `POST {baseUrl}/chat/completions` against a Gonka Broker instance.
 *
 * Not reused from Koog's concrete `ai.koog:prompt-executor-openai-client` module because the
 * equivalent type there (`OpenAIChatCompletionRequest`) is `internal` to that module. Everything
 * else on this request (messages, tools, tool choice) is reused verbatim from
 * `ai.koog:prompt-executor-openai-client-base`, which *is* public.
 */
@Serializable
internal class GonkaChatCompletionRequest(
    val messages: List<OpenAIMessage>,
    val model: String,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val tools: List<OpenAITool>? = null,
    val toolChoice: OpenAIToolChoice? = null,
    val stream: Boolean? = null,
)

/**
 * Response envelope for a non-streaming chat completion from a Gonka Broker instance.
 * Wire shape follows the OpenAI chat-completions convention (Gonka Broker is documented as
 * OpenAI-compatible).
 */
@Serializable
public class GonkaChatCompletionResponse(
    public val choices: List<OpenAIChoice>,
    override val created: Long,
    override val id: String,
    override val model: String,
    @SerialName("object") public val objectType: String? = null,
    public val usage: OpenAIUsage? = null,
) : OpenAIBaseLLMResponse

/**
 * Streaming response chunk (one SSE `data: {...}` event) from a Gonka Broker instance.
 */
@Serializable
public class GonkaChatCompletionStreamResponse(
    public val choices: List<OpenAIStreamChoice>,
    override val created: Long,
    override val id: String,
    override val model: String,
    @SerialName("object") public val objectType: String? = null,
    public val usage: OpenAIUsage? = null,
) : OpenAIBaseLLMStreamResponse
