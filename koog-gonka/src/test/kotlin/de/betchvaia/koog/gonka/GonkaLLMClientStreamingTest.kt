package de.betchvaia.koog.gonka

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamDelta
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamFunction
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamToolCall
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import ai.koog.prompt.streaming.StreamFrame
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList

/**
 * A real network round-trip through [GonkaLLMClient.executeStreaming] would go through
 * `KoogHttpClient.sse()`, which requires the underlying ktor engine to advertise the SSE
 * [io.ktor.client.engine.HttpClientEngineCapability]. `ktor-client-mock`'s [MockEngine] does not
 * advertise that capability, so driving `executeStreaming()` end-to-end against it fails with
 * "Engine doesn't support SSECapability" regardless of what the mock handler returns — this was
 * confirmed with a spike before writing this whole file (see the plan's gotcha about this
 * combination being unproven).
 *
 * These tests instead exercise [GonkaLLMClient.processStreamingResponse] directly with a
 * synthetic [Flow] of already-decoded [GonkaChatCompletionStreamResponse] chunks. That is the
 * part of the streaming path that is actually Gonka's own logic (turning OpenAI-compatible
 * stream chunks into Koog [StreamFrame]s); the chunk decoding, HTTP transport, and SSE framing
 * above it are inherited from `AbstractOpenAILLMClient`/`KtorKoogHttpClient` and are Koog's own,
 * already-tested code.
 */
private class TestableGonkaLLMClient : GonkaLLMClient(
    auth = GonkaAuth.ApiKey(apiKey = TEST_API_KEY, baseUrl = TEST_BASE_URL),
    httpClient = HttpClient(MockEngine { respondError(HttpStatusCode.NotImplemented) }),
) {
    fun processStreaming(response: Flow<GonkaChatCompletionStreamResponse>): Flow<StreamFrame> =
        processStreamingResponse(response)
}

private fun streamChunk(
    content: String? = null,
    toolCalls: List<OpenAIStreamToolCall>? = null,
    finishReason: String? = null,
    index: Int = 0,
    usage: OpenAIUsage? = null,
): GonkaChatCompletionStreamResponse = GonkaChatCompletionStreamResponse(
    choices = listOf(
        OpenAIStreamChoice(
            delta = OpenAIStreamDelta(content = content, toolCalls = toolCalls),
            finishReason = finishReason,
            index = index,
        ),
    ),
    created = 1_710_000_000L,
    id = "chunk-test",
    model = "gonka-test-model",
    usage = usage,
)

class GonkaLLMClientStreamingTest : StringSpec({

    "processStreamingResponse() emits text deltas followed by an End frame" {
        val client = TestableGonkaLLMClient()
        val chunks = flowOf(
            streamChunk(content = "Hello"),
            streamChunk(content = " world", finishReason = "stop"),
        )

        val frames = client.processStreaming(chunks).toList()

        val textDeltas = frames.filterIsInstance<StreamFrame.TextDelta>().map { it.text }
        textDeltas shouldBe listOf("Hello", " world")

        val end = frames.last()
        end.shouldBeInstanceOf<StreamFrame.End>()
        end.finishReason shouldBe "stop"

        client.close()
    }

    "processStreamingResponse() carries usage into the End frame's metaInfo" {
        val client = TestableGonkaLLMClient()
        val usage = OpenAIUsage(promptTokens = 3, completionTokens = 7, totalTokens = 10)
        val chunks = flowOf(
            streamChunk(content = "Hi", finishReason = "stop", usage = usage),
        )

        val frames = client.processStreaming(chunks).toList()

        val end = frames.last()
        end.shouldBeInstanceOf<StreamFrame.End>()
        end.metaInfo.totalTokensCount shouldBe 10

        client.close()
    }

    "processStreamingResponse() emits tool-call deltas across chunks and completes them" {
        val client = TestableGonkaLLMClient()
        val chunks = flowOf(
            streamChunk(
                toolCalls = listOf(
                    OpenAIStreamToolCall(
                        index = 0,
                        id = "call_1",
                        function = OpenAIStreamFunction(name = "get_weather", arguments = "{\"loc"),
                    ),
                ),
            ),
            streamChunk(
                toolCalls = listOf(
                    OpenAIStreamToolCall(
                        index = 0,
                        id = null,
                        function = OpenAIStreamFunction(name = null, arguments = "ation\":\"Berlin\"}"),
                    ),
                ),
                finishReason = "tool_calls",
            ),
        )

        val frames = client.processStreaming(chunks).toList()

        val deltas = frames.filterIsInstance<StreamFrame.ToolCallDelta>()
        deltas.size shouldBe 2
        deltas.first().name shouldBe "get_weather"

        val complete = frames.filterIsInstance<StreamFrame.ToolCallComplete>().single()
        complete.name shouldBe "get_weather"
        complete.content shouldBe "{\"location\":\"Berlin\"}"

        frames.last().shouldBeInstanceOf<StreamFrame.End>()

        client.close()
    }

    "processStreamingResponse() on an empty chunk flow still emits a single End frame" {
        val client = TestableGonkaLLMClient()
        val frames = client.processStreaming(flowOf<GonkaChatCompletionStreamResponse>()).toList()
        frames.size shouldBe 1
        val end = frames.single()
        end.shouldBeInstanceOf<StreamFrame.End>()
        end.finishReason shouldBe null
        client.close()
    }
})
