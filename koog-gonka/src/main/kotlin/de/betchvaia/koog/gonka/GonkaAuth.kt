package de.betchvaia.koog.gonka

import de.betchvaia.koog.gonka.wallet.GonkaSigner

/**
 * Authentication mode for the Gonka network.
 *
 * [ApiKey] talks to a Gonka Broker instance (OpenAI-compatible, USD card
 * billing, no wallet needed). [Wallet] holds a secp256k1 key and derives its
 * `gonka1...` address, ready for chain-native signing — see [Wallet]'s KDoc
 * for exactly what this wave does and does not implement.
 */
public sealed interface GonkaAuth {
    public data class ApiKey(val apiKey: String, val baseUrl: String = DEFAULT_BROKER_URL) : GonkaAuth

    /**
     * Wallet-based auth: signs requests directly against the Gonka chain with a secp256k1
     * key instead of going through a Broker. See
     * [de.betchvaia.koog.gonka.wallet.GonkaWalletLLMClient] for what this wave actually
     * implements (identity + signing primitives) versus what it deliberately does not
     * (dispatch) and why.
     *
     * Deliberately NOT a `data class` (unlike [ApiKey]): a generated `toString()`/`copy()`
     * would expose [privateKeyHex]-derived state in ways that are easy to leak by accident
     * (e.g. `logger.debug { "$auth" }`). [toString] is hand-written to print only [address]
     * and [nodeUrl].
     *
     * [AutoCloseable]: [close] best-effort zeroes the retained key material (see
     * [GonkaSigner.close] for the exact guarantee and its limits). A caller holding a
     * [Wallet] directly should call [close] once the key is no longer needed.
     * [de.betchvaia.koog.gonka.wallet.GonkaWalletLLMClient] takes ownership of the [Wallet]
     * passed to its constructor and forwards its own `close()` to this one — same pattern as
     * [de.betchvaia.koog.gonka.GonkaLLMClient] owning and closing the ktor `HttpClient`
     * passed (or default-constructed) into it.
     *
     * SECURITY: [privateKeyHex] is consumed once by the constructor to derive this object's
     * key material; the string itself is not retained by this class. Key material derived
     * from it IS retained internally until [close] is called (needed to sign future
     * requests) — never logged, never surfaced in [toString] or in any exception message
     * this class or [GonkaSigner] throws. The JVM offers no hard guarantee of clearing it
     * from memory (immutable `String`s may be interned; JIT/GC-retained copies and the
     * JNI-backed secp256k1 library's own internal copies are outside this class's control)
     * — callers should treat [privateKeyHex] itself as sensitive for its own lifecycle
     * (don't log it before passing it in, don't keep extra references around), and should
     * call [close] rather than relying solely on GC.
     *
     * @param privateKeyHex 32-byte secp256k1 private key, lowercase or uppercase hex, with
     *   or without a `0x` prefix, exactly 64 hex digits. Matches the format
     *   `inferenced keys export <name> --unarmored-hex --unsafe` produces.
     * @param nodeUrl Base URL of a Gonka chain node (e.g. `http://node2.gonka.ai:8000`, per
     *   Gonka's own `docs/consumer_setup.md` example). Unlike [ApiKey.baseUrl], there is no
     *   default: Gonka has no single canonical node the way `api.gonka.ai` is for the
     *   Broker — this is a community-run public network. Not yet used by anything in this
     *   wave (kept for a future dispatch implementation) but validated non-blank.
     * @throws IllegalArgumentException if [privateKeyHex] is not exactly 64 hex digits, is
     *   not valid hex, or is not a valid secp256k1 private key (zero, or >= curve order) —
     *   see [GonkaSigner.fromPrivateKeyHex]. Also thrown if [nodeUrl] is blank. Neither the
     *   invalid hex value nor any prefix/suffix of it appears in the exception message.
     */
    public class Wallet(privateKeyHex: String, public val nodeUrl: String) : GonkaAuth, AutoCloseable {

        init {
            require(nodeUrl.isNotBlank()) { "GonkaAuth.Wallet: nodeUrl must not be blank" }
        }

        private val signer: GonkaSigner = GonkaSigner.fromPrivateKeyHex(privateKeyHex)

        /** The `gonka1...` bech32 address derived from this wallet's public key. */
        public val address: String get() = signer.address

        /** The 33-byte compressed secp256k1 public key, hex-encoded. */
        public val compressedPublicKeyHex: String get() = signer.compressedPublicKeyHex

        /** Best-effort zeroes the retained key material. See this class's KDoc for the guarantee's limits. */
        override fun close() {
            signer.close()
        }

        override fun toString(): String = "GonkaAuth.Wallet(address=$address, nodeUrl=$nodeUrl)"

        override fun equals(other: Any?): Boolean =
            other is Wallet && address == other.address && nodeUrl == other.nodeUrl

        override fun hashCode(): Int = 31 * address.hashCode() + nodeUrl.hashCode()
    }

    public companion object {
        public const val DEFAULT_BROKER_URL: String = "https://api.gonka.ai/v1"
    }
}
