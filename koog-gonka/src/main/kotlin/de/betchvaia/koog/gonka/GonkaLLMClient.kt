package de.betchvaia.koog.gonka

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import ai.koog.utils.time.KoogClock
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmOverloads

/**
 * Koog [ai.koog.prompt.executor.clients.LLMClient] implementation talking to a Gonka Broker
 * instance. The Broker exposes an OpenAI-compatible API (chat completions, streaming, tool
 * calls), so this class extends Koog's [AbstractOpenAILLMClient] to reuse its message
 * conversion, tool-call parsing, and SSE streaming loop — but it is Gonka's own class with its
 * own auth handling ([GonkaAuth]), configuration, and model listing, not a configuration profile
 * bolted onto Koog's built-in `OpenAILLMClient`.
 *
 * Only API-key/Broker auth ([GonkaAuth.ApiKey]) is supported in this wave. Wallet-based
 * (secp256k1) auth is planned for a later wave.
 *
 * Moderation and multiple-choice completions are not supported by Gonka Broker; [moderate] throws
 * and `executeMultipleChoices` is left at its inherited [LLMCapability.MultipleChoices] gate
 * (deliberately omitted from [DEFAULT_CAPABILITIES]).
 */
public open class GonkaLLMClient @JvmOverloads public constructor(
    auth: GonkaAuth.ApiKey,
    httpClient: HttpClient = HttpClient(CIO),
    clock: KoogClock = KoogClock.System,
    settings: GonkaClientSettings = GonkaClientSettings(baseUrl = auth.baseUrl),
) : AbstractOpenAILLMClient<GonkaChatCompletionResponse, GonkaChatCompletionStreamResponse>(
    apiKey = auth.apiKey,
    settings = settings,
    httpClientFactory = KtorKoogHttpClient.Factory(baseClient = httpClient),
    clientName = CLIENT_NAME,
    clock = clock,
    logger = logger,
    toolsConverter = OpenAICompatibleToolDescriptorSchemaGenerator(),
) {

    private val modelsPath: String = settings.modelsPath

    /**
     * The raw ktor [HttpClient] passed in (or default-constructed) above. [KtorKoogHttpClient.Factory]
     * derives a child client from it via `baseClient.config { ... }`, which shares the same underlying
     * [io.ktor.client.engine.HttpClientEngine] and bumps its ref-count. The base class's inherited
     * `close()` only closes that derived child, decrementing the engine ref-count by one but never to
     * zero for this base client's own contribution — so the engine (and its CIO thread pool/selector)
     * is never actually released unless this reference is closed too. Retained here so [close] can
     * close it explicitly.
     */
    private val ownedHttpClient: HttpClient = httpClient

    public companion object {
        internal const val CLIENT_NAME: String = "GonkaLLMClient"
        internal val logger = KotlinLogging.logger {}

        /**
         * Capabilities assumed for models discovered via [models]. Gonka Broker's support for
         * multiple-choice completions is unconfirmed, so [LLMCapability.MultipleChoices] is
         * deliberately omitted here.
         */
        public val DEFAULT_CAPABILITIES: List<LLMCapability> = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
        )
    }

    override fun llmProvider(): LLMProvider = GonkaLLMProvider

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException("Moderation is not supported by Gonka Broker")

    override suspend fun models(): List<LLModel> {
        logger.debug { "Fetching available models from Gonka Broker" }
        val response = try {
            httpClient.get(path = modelsPath, responseType = GonkaModelsResponse::class)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(clientName, e.message, e)
        }
        return response.data.map { entry ->
            LLModel(provider = llmProvider(), id = entry.id, capabilities = DEFAULT_CAPABILITIES)
        }
    }

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean,
    ): String = json.encodeToString(
        GonkaChatCompletionRequest.serializer(),
        GonkaChatCompletionRequest(
            messages = messages,
            model = model.id,
            temperature = params.temperature,
            maxTokens = params.maxTokens,
            tools = tools,
            toolChoice = toolChoice,
            stream = stream,
        ),
    )

    override fun processProviderChatResponse(response: GonkaChatCompletionResponse): List<Message.Assistant> {
        require(response.choices.isNotEmpty()) { "Empty choices in response from Gonka Broker" }
        val metaInfo = createMetaInfo(response.usage)
        return response.choices.map { choice -> choice.message.toMessageResponse(choice.finishReason, metaInfo) }
    }

    override fun decodeResponse(data: String): GonkaChatCompletionResponse =
        json.decodeFromString(GonkaChatCompletionResponse.serializer(), data)

    override fun decodeStreamingResponse(data: String): GonkaChatCompletionStreamResponse =
        json.decodeFromString(GonkaChatCompletionStreamResponse.serializer(), data)

    override fun processStreamingResponse(
        response: Flow<GonkaChatCompletionStreamResponse>
    ): Flow<StreamFrame> = buildStreamFrameFlow {
        var finishReason: String? = null
        var metaInfo: ResponseMetaInfo? = null
        response.collect { chunk ->
            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta.content?.let { emitTextDelta(it, choice.index) }
                choice.delta.toolCalls?.forEach { toolCall ->
                    emitToolCallDelta(
                        id = toolCall.id,
                        name = toolCall.function?.name,
                        args = toolCall.function?.arguments,
                        index = toolCall.index,
                    )
                }
                choice.finishReason?.let { finishReason = it }
            }
            chunk.usage?.let { metaInfo = createMetaInfo(it) }
        }
        emitEnd(finishReason, metaInfo)
    }

    /**
     * Closes both the derived Koog HTTP client (via [AbstractOpenAILLMClient.close]) and the
     * underlying [ownedHttpClient] passed into (or default-constructed by) this client's
     * constructor. Without closing [ownedHttpClient] explicitly, its ktor engine (and, for the
     * default CIO-backed client, its thread pool/selector) is leaked: the derived client created
     * internally by [KtorKoogHttpClient.Factory] shares the same engine instance and only
     * decrements its ref-count on close, never reaching zero for [ownedHttpClient]'s own share.
     */
    override fun close() {
        super.close()
        ownedHttpClient.close()
    }
}
