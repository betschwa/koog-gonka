package de.betchvaia.koog.gonka.wallet

/**
 * Minimal implementation of classic Bech32 (BIP-173) — explicitly NOT Bech32m (BIP-350).
 * Gonka addresses use classic Bech32 with HRP `"gonka"` (`devshard/signing/secp256k1.go`
 * in `gonka-ai/gonka` imports `github.com/cosmos/btcutil/bech32` and calls `bech32.Encode`,
 * the classic variant) — the same scheme Cosmos SDK uses for `"cosmos1..."` addresses.
 *
 * This is NOT elliptic-curve cryptography — it is a public, deterministic checksum/encoding
 * format with no secret-dependent computation (BIP-173, public-domain reference
 * pseudocode). Implemented directly here rather than pulling in a dependency (e.g. ACINQ's
 * `bitcoin-kmp`, which bundles this but also a full Bitcoin protocol stack this library does
 * not need) to keep the dependency footprint minimal; validated against BIP-173's published
 * test vectors plus this library's own golden `gonka1...` vector (see [GonkaSigner]'s test suite).
 */
internal object GonkaBech32 {

    private const val CHARSET: String = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATOR: IntArray = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    /**
     * Encodes [data] (a byte array of 5-bit groups, each byte holding a value in `0..31` —
     * typically produced by [convertBits]) as classic Bech32 with human-readable part [hrp].
     */
    internal fun encode(hrp: String, data: ByteArray): String {
        require(hrp.isNotEmpty()) { "Bech32 hrp must not be empty" }
        require(data.all { it in 0..31 }) { "Bech32 data must contain only 5-bit values (0-31)" }
        val values = data.map { it.toInt() }
        val checksum = createChecksum(hrp, values)
        val combined = values + checksum
        return buildString {
            append(hrp)
            append('1')
            combined.forEach { append(CHARSET[it]) }
        }
    }

    /**
     * Converts a byte array of `fromBits`-wide groups into a byte array of `toBits`-wide
     * groups (each output byte holding a value in `0 until (1 shl toBits)`). Used to convert
     * 8-bit bytes (a RIPEMD160 hash) into the 5-bit groups Bech32 encodes.
     *
     * @param pad whether to zero-pad an incomplete trailing group instead of failing.
     * @throws IllegalArgumentException if [pad] is `false` and the input bits don't evenly
     *   divide into `toBits`-wide groups, or if padding bits are non-zero.
     */
    internal fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        require(fromBits in 1..8 && toBits in 1..8) { "fromBits/toBits must be in 1..8" }
        var acc = 0
        var bits = 0
        val maxV = (1 shl toBits) - 1
        val maxAcc = (1 shl (fromBits + toBits - 1)) - 1
        val result = mutableListOf<Int>()
        for (byte in data) {
            val value = byte.toInt() and 0xFF
            require((value shr fromBits) == 0) { "Input byte exceeds fromBits width" }
            acc = ((acc shl fromBits) or value) and maxAcc
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add((acc shr bits) and maxV)
            }
        }
        if (pad) {
            if (bits > 0) {
                result.add((acc shl (toBits - bits)) and maxV)
            }
        } else {
            require(bits < fromBits) { "Illegal zero padding" }
            require(((acc shl (toBits - bits)) and maxV) == 0) { "Non-zero padding" }
        }
        return ByteArray(result.size) { result[it].toByte() }
    }

    private fun polymod(values: List<Int>): Int {
        var chk = 1
        for (v in values) {
            val b = chk ushr 25
            chk = (chk and 0x1ffffff) shl 5 xor v
            for (i in 0 until 5) {
                if ((b shr i) and 1 == 1) {
                    chk = chk xor GENERATOR[i]
                }
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): List<Int> {
        val high = hrp.map { it.code shr 5 }
        val low = hrp.map { it.code and 31 }
        return high + listOf(0) + low
    }

    private fun createChecksum(hrp: String, data: List<Int>): List<Int> {
        val values = hrpExpand(hrp) + data + List(6) { 0 }
        val polymod = polymod(values) xor 1
        return (0 until 6).map { (polymod shr (5 * (5 - it))) and 31 }
    }
}
