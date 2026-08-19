package de.betchvaia.koog.gonka

import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.message.MessagePart
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

class GonkaLLMClientTest : StringSpec({

    "execute() returns the assistant message on a happy-path response" {
        val client = mockGonkaClient { respond(CHAT_COMPLETION_RESPONSE_JSON, headers = jsonHeaders()) }
        val result = client.execute(testPrompt(), testModel())
        result.textContent() shouldBe "Hello from Gonka Broker"
        result.finishReason shouldBe "stop"
        result.metaInfo.totalTokensCount shouldBe 15
        client.close()
    }

    "execute() sends the Bearer auth header from GonkaAuth.ApiKey" {
        var capturedAuth: String? = null
        val client = mockGonkaClient(apiKey = "my-secret-key") { request ->
            capturedAuth = request.headers["Authorization"]
            respond(CHAT_COMPLETION_RESPONSE_JSON, headers = jsonHeaders())
        }
        client.execute(testPrompt(), testModel())
        capturedAuth shouldBe "Bearer my-secret-key"
        client.close()
    }

    "execute() posts to {baseUrl}/chat/completions without doubling the v1 segment" {
        var capturedPath: String? = null
        val client = mockGonkaClient(baseUrl = "https://broker.test/v1") { request ->
            capturedPath = request.url.encodedPath
            respond(CHAT_COMPLETION_RESPONSE_JSON, headers = jsonHeaders())
        }
        client.execute(testPrompt(), testModel())
        capturedPath shouldBe "/v1/chat/completions"
        client.close()
    }

    "execute() serializes model id, message content and stream=false in the request body" {
        var capturedBody: String? = null
        val client = mockGonkaClient { request ->
            capturedBody = request.bodyText()
            respond(CHAT_COMPLETION_RESPONSE_JSON, headers = jsonHeaders())
        }
        client.execute(testPrompt("What is the weather?"), testModel(id = "gonka-test-model"))
        val body = capturedBody!!
        body shouldContain "\"model\":\"gonka-test-model\""
        body shouldContain "\"stream\":false"
        body shouldContain "What is the weather?"
        client.close()
    }

    "execute() with a tool-call response yields a MessagePart.Tool.Call" {
        val client = mockGonkaClient {
            respond(toolCallResponseJson(toolCallId = "call_42", toolName = "get_weather"), headers = jsonHeaders())
        }
        val result = client.execute(testPrompt(), testModel(), tools = listOf(testToolDescriptor("get_weather")))
        val toolCall = result.parts.filterIsInstance<MessagePart.Tool.Call>().single()
        toolCall.id shouldBe "call_42"
        toolCall.tool shouldBe "get_weather"
        result.finishReason shouldBe "tool_calls"
        client.close()
    }

    "execute() with tools includes the tool definition in the request body" {
        var capturedBody: String? = null
        val client = mockGonkaClient { request ->
            capturedBody = request.bodyText()
            respond(CHAT_COMPLETION_RESPONSE_JSON, headers = jsonHeaders())
        }
        client.execute(testPrompt(), testModel(), tools = listOf(testToolDescriptor("get_weather")))
        capturedBody!! shouldContain "\"get_weather\""
        client.close()
    }

    "execute() wraps a 401 response as LLMClientException" {
        val client = mockGonkaClient { respondError(HttpStatusCode.Unauthorized, content = "unauthorized") }
        shouldThrow<LLMClientException> {
            client.execute(testPrompt(), testModel())
        }
        client.close()
    }

    "execute() wraps a 500 response as LLMClientException" {
        val client = mockGonkaClient { respondError(HttpStatusCode.InternalServerError, content = "boom") }
        shouldThrow<LLMClientException> {
            client.execute(testPrompt(), testModel())
        }
        client.close()
    }

    "execute() wraps a malformed JSON 200 response as LLMClientException" {
        val client = mockGonkaClient { respond("not json at all", headers = jsonHeaders()) }
        shouldThrow<LLMClientException> {
            client.execute(testPrompt(), testModel())
        }
        client.close()
    }

    "execute() propagates CancellationException un-wrapped on mid-flight cancellation" {
        val client = mockGonkaClient {
            delay(5_000)
            respond(CHAT_COMPLETION_RESPONSE_JSON, headers = jsonHeaders())
        }
        var caught: Throwable? = null
        val job: Job = kotlinx.coroutines.CoroutineScope(coroutineContext).launch {
            try {
                client.execute(testPrompt(), testModel())
            } catch (e: CancellationException) {
                caught = e
                throw e
            }
        }
        // Give the request a moment to actually start before cancelling it.
        withTimeout(2_000) {
            delay(50)
            job.cancelAndJoin()
        }
        caught.shouldBeInstanceOfCancellationException()
        client.close()
    }

    "llmProvider() returns GonkaLLMProvider" {
        val client = mockGonkaClient { respond(CHAT_COMPLETION_RESPONSE_JSON, headers = jsonHeaders()) }
        client.llmProvider() shouldBe GonkaLLMProvider
        client.close()
    }

    "close() closes the underlying client without throwing" {
        val client = mockGonkaClient { respond(CHAT_COMPLETION_RESPONSE_JSON, headers = jsonHeaders()) }
        client.close()
    }
})

private suspend fun Job.cancelAndJoin() {
    cancel()
    join()
}

private fun Throwable?.shouldBeInstanceOfCancellationException() {
    require(this is CancellationException) { "Expected CancellationException, got $this" }
}
