package de.betchvaia.koog.gonka

/**
 * Authentication mode for the Gonka network.
 *
 * [ApiKey] talks to a Gonka Broker instance (OpenAI-compatible, USD card
 * billing, no wallet needed). [Wallet] signs requests directly against the
 * chain with a secp256k1 key — planned for a later wave, not yet implemented.
 */
public sealed interface GonkaAuth {
    public data class ApiKey(val apiKey: String, val baseUrl: String = DEFAULT_BROKER_URL) : GonkaAuth

    public companion object {
        public const val DEFAULT_BROKER_URL: String = "https://api.gonka.ai/v1"
    }
}
