plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(25)
    explicitApi()
}

dependencies {
    // Koog LLMClient SPI (Maven Central, JetBrains AI Agent Framework)
    api(libs.koog.agents.jvm)

    // Gonka Broker exposes an OpenAI-compatible API — the first auth mode
    // (API key) is implemented as a thin delegation to Koog's OpenAI client.
    implementation(libs.koog.prompt.executor.openai.client)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlin.logging)
    testRuntimeOnly(libs.logback.classic)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        // Live tests hit the real Gonka Broker endpoint — opt-in only.
        if (System.getProperty("koogGonka.test.live") != "true") {
            excludeTags("live")
        }
    }
}
