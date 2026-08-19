package de.betchvaia.koog.gonka

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.base.OpenAIBaseSettings

/**
 * Settings for talking to a Gonka Broker instance.
 *
 * Gonka Broker exposes an OpenAI-compatible API. [baseUrl] already ends in `/v1`
 * (see [GonkaAuth.DEFAULT_BROKER_URL]), so [chatCompletionsPath] and [modelsPath]
 * deliberately do *not* repeat the `v1` segment — doing so would silently produce
 * `.../v1/v1/chat/completions` and fail with a 404 at runtime instead of a compile error.
 *
 * @property modelsPath Path (relative to [baseUrl]) used for model discovery via `GET`.
 */
public class GonkaClientSettings(
    baseUrl: String = GonkaAuth.DEFAULT_BROKER_URL,
    public val modelsPath: String = "models",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
) : OpenAIBaseSettings(baseUrl = baseUrl, chatCompletionsPath = "chat/completions", timeoutConfig = timeoutConfig)
