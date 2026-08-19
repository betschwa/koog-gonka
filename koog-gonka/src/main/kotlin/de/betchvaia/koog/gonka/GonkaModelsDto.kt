package de.betchvaia.koog.gonka

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape for `GET {baseUrl}/models` against a Gonka Broker instance.
 *
 * Modeled on the OpenAI `GET /v1/models` convention (`{"object":"list","data":[{"id":...}]}`)
 * since Gonka Broker is documented as OpenAI-compatible. **Not verified against a live Broker
 * response** — if the real shape differs, only this file needs to change.
 */
@Serializable
internal class GonkaModelsResponse(val data: List<GonkaModelEntry>)

@Serializable
internal class GonkaModelEntry(
    val id: String,
    @SerialName("object") val objectType: String? = null,
    val created: Long? = null,
    val ownedBy: String? = null,
)
