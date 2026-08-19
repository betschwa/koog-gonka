plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
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

    // secp256k1 for GonkaAuth.Wallet: JNI bindings to Bitcoin Core's libsecp256k1, the same
    // reference implementation Gonka's own Go signer wraps (devshard/signing/secp256k1.go,
    // github.com/gonka-ai/gonka, via go-ethereum's crypto package). Only used inside the
    // wallet package, never in a public signature -> implementation. Two artifacts needed:
    // -jvm has the actual Kotlin classes (Secp256k1 interface + wrapper), -jni-jvm bundles
    // the per-OS native libsecp256k1 binaries it loads at runtime. Pinned to 0.16.0, not the
    // newest release: 0.17.0+ declares a minimum JVM target of 21 via Gradle module
    // metadata, incompatible with this module's jvmToolchain(17); 0.16.0 is the newest
    // release still built for JVM 17. Bump this alongside jvmToolchain if that ever changes.
    implementation(libs.acinq.secp256k1.jvm)
    implementation(libs.acinq.secp256k1.jni.jvm)

    // RIPEMD160 for gonka1... address derivation (sha256 -> ripemd160 -> bech32, matching
    // Gonka's own scheme). The JDK's default JCE providers don't ship RIPEMD160. Only used
    // inside GonkaSigner -> implementation.
    implementation(libs.bouncycastle.bcprov)

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
