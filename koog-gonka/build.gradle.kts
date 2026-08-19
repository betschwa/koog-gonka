plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

dependencies {
    // Koog LLMClient SPI (Maven Central, JetBrains AI Agent Framework)
    api(libs.koog.agents.jvm)

    // Gonka Broker exposes an OpenAI-compatible API. GonkaLLMClient extends Koog's
    // AbstractOpenAILLMClient (from -openai-client-base) to reuse message conversion,
    // tool-call parsing and SSE streaming — it is its own class with its own auth
    // (GonkaAuth), config and model listing, not a config profile on Koog's concrete
    // OpenAILLMClient. AbstractOpenAILLMClient is a public supertype of GonkaLLMClient,
    // so it must be on consumers' compile classpath too -> api, not implementation.
    api(libs.koog.prompt.executor.openai.client.base)

    // KtorKoogHttpClient/Factory are used only inside GonkaLLMClient's constructor body,
    // never in a public signature -> implementation.
    implementation(libs.koog.http.client.ktor)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // HttpClient (ktor-client-core) is a parameter *type* on GonkaLLMClient's public
    // constructor, so it must be visible to consumers of the published artifact -> api.
    api(libs.ktor.client.core)
    // Only used as the default-parameter *value* HttpClient(CIO), not a signature type.
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlin.logging)
    testRuntimeOnly(libs.logback.classic)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        // Live tests hit the real Gonka Broker endpoint — opt-in only.
        if (System.getProperty("koogGonka.test.live") != "true") {
            excludeTags("live")
        }
    }
}
