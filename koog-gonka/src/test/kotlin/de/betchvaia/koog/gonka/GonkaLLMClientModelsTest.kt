package de.betchvaia.koog.gonka

import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMCapability
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

class GonkaLLMClientModelsTest : StringSpec({

    "models() returns LLModels for every entry in the Broker response" {
        val client = mockGonkaClient { respond(MODELS_RESPONSE_JSON, headers = jsonHeaders()) }
        val models = client.models()
        models.map { it.id } shouldContainExactly listOf("gonka-model-a", "gonka-model-b")
        models.forEach { model ->
            model.provider shouldBe GonkaLLMProvider
            model.supports(LLMCapability.Completion) shouldBe true
        }
        client.close()
    }

    "models() sends GET {baseUrl}/models with the Bearer auth header" {
        var capturedMethod: HttpMethod? = null
        var capturedPath: String? = null
        var capturedAuth: String? = null
        val client = mockGonkaClient(apiKey = "my-secret-key") { request ->
            capturedMethod = request.method
            capturedPath = request.url.encodedPath
            capturedAuth = request.headers["Authorization"]
            respond(MODELS_RESPONSE_JSON, headers = jsonHeaders())
        }
        client.models()
        capturedMethod shouldBe HttpMethod.Get
        capturedPath shouldBe "/v1/models"
        capturedAuth shouldBe "Bearer my-secret-key"
        client.close()
    }

    "models() wraps a non-2xx response as LLMClientException" {
        val client = mockGonkaClient { respondError(HttpStatusCode.ServiceUnavailable, content = "down") }
        shouldThrow<LLMClientException> {
            client.models()
        }
        client.close()
    }
})
