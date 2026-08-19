package de.betchvaia.koog.gonka

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf

internal const val TEST_API_KEY: String = "test-api-key"
internal const val TEST_BASE_URL: String = "https://broker.test/v1"

internal fun testModel(
    id: String = "gonka-test-model",
    capabilities: List<LLMCapability> = GonkaLLMClient.DEFAULT_CAPABILITIES,
): LLModel = LLModel(provider = GonkaLLMProvider, id = id, capabilities = capabilities)

internal fun testPrompt(userMessage: String = "Hello"): Prompt =
    Prompt.build(id = "test-prompt") {
        user(userMessage)
    }

internal fun testToolDescriptor(name: String = "get_weather"): ToolDescriptor = ToolDescriptor(
    name = name,
    description = "Gets the current weather for a location",
)

/** Builds a [GonkaLLMClient] backed by a ktor [MockEngine] driven by [handler]. */
internal fun mockGonkaClient(
    apiKey: String = TEST_API_KEY,
    baseUrl: String = TEST_BASE_URL,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): GonkaLLMClient = GonkaLLMClient(
    auth = GonkaAuth.ApiKey(apiKey = apiKey, baseUrl = baseUrl),
    httpClient = HttpClient(MockEngine { request -> handler(request) }),
)

/** Reads the raw UTF-8 request body text captured by [MockEngine], for asserting on serialized JSON. */
internal fun HttpRequestData.bodyText(): String = when (val content = body) {
    is TextContent -> content.text
    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
    else -> ""
}

internal fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

internal fun sseHeaders() = headersOf(HttpHeaders.ContentType, "text/event-stream")

internal fun sseBody(vararg dataLines: String): ByteArray =
    dataLines.joinToString(separator = "") { "data: $it\n\n" }.toByteArray()

internal val CHAT_COMPLETION_RESPONSE_JSON: String = """
    {
      "id": "chatcmpl-test-1",
      "object": "chat.completion",
      "created": 1710000000,
      "model": "gonka-test-model",
      "choices": [
        {
          "index": 0,
          "finish_reason": "stop",
          "message": {
            "role": "assistant",
            "content": "Hello from Gonka Broker"
          }
        }
      ],
      "usage": {
        "prompt_tokens": 10,
        "completion_tokens": 5,
        "total_tokens": 15
      }
    }
""".trimIndent()

internal fun toolCallResponseJson(toolCallId: String = "call_1", toolName: String = "get_weather"): String = """
    {
      "id": "chatcmpl-test-2",
      "object": "chat.completion",
      "created": 1710000000,
      "model": "gonka-test-model",
      "choices": [
        {
          "index": 0,
          "finish_reason": "tool_calls",
          "message": {
            "role": "assistant",
            "content": null,
            "tool_calls": [
              {
                "id": "$toolCallId",
                "type": "function",
                "function": {
                  "name": "$toolName",
                  "arguments": "{\"location\":\"Berlin\"}"
                }
              }
            ]
          }
        }
      ]
    }
""".trimIndent()

internal val MODELS_RESPONSE_JSON: String = """
    {
      "object": "list",
      "data": [
        { "id": "gonka-model-a", "object": "model", "created": 1710000000, "owned_by": "gonka" },
        { "id": "gonka-model-b", "object": "model", "created": 1710000001, "owned_by": "gonka" }
      ]
    }
""".trimIndent()
