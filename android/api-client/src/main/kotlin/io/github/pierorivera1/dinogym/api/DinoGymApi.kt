package io.github.pierorivera1.dinogym.api

import io.github.pierorivera1.dinogym.api.apis.AuthApi
import io.github.pierorivera1.dinogym.api.apis.DevicesApi
import io.github.pierorivera1.dinogym.api.apis.HealthApi
import io.github.pierorivera1.dinogym.api.apis.SharesApi
import io.github.pierorivera1.dinogym.api.apis.SyncApi
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The one place the generated API classes are built (decision 60).
 *
 * The generated `ApiClient` installs content negotiation with no format, so every
 * class made here gets kotlinx.serialization JSON. Unknown fields are ignored, so an
 * older app keeps working when the server adds a field to a response.
 */
class DinoGymApi(
    baseUrl: String,
    engine: HttpClientEngine,
) {
    private val config: (HttpClientConfig<*>) -> Unit = {
        it.install(ContentNegotiation) { json(JSON) }
        it.expectSuccess = false
    }

    val health = HealthApi(baseUrl, engine, config)
    val auth = AuthApi(baseUrl, engine, config)
    val sync = SyncApi(baseUrl, engine, config)
    val shares = SharesApi(baseUrl, engine, config)
    val devices = DevicesApi(baseUrl, engine, config)

    /** Sends `Authorization: Bearer <token>` on the calls that need it. */
    fun setAccessToken(token: String) {
        sync.setBearerToken(token)
    }

    companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            // Optional fields that are null are left out, which the contract allows.
            explicitNulls = false
        }
    }
}
