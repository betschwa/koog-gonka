package de.betchvaia.koog.gonka

import ai.koog.prompt.llm.LLMProvider
import kotlinx.serialization.Serializable

/**
 * The [LLMProvider] identifying the Gonka network. [LLMProvider] is open/extensible by design —
 * this follows the same pattern Koog itself uses for its built-in providers.
 */
@Serializable
public object GonkaLLMProvider : LLMProvider("gonka", "Gonka")
