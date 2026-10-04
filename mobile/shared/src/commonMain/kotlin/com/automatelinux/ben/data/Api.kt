package com.automatelinux.ben.data

import com.automatelinux.ben.data.model.Page
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Small strings that survive a restart — here, the saved copy of the conversation. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** The ways asking for the conversation can fail. Each one is shown differently. */
sealed class ApiFailure(message: String) : Exception(message) {
    /** No answer at all: the desktop is off, WireGuard is down, or the phone has no network. */
    class Unreachable : ApiFailure("the server did not answer")

    /** The server answered and refused the token baked into this build. */
    class Refused : ApiFailure("the server refused this build's token")

    /** The server answered with something that is not a page of the conversation. */
    class Broken(val status: Int) : ApiFailure("the server answered $status")
}

/** Where the conversation is read from. An interface so the sync logic can be tested without a server. */
interface ConversationSource {
    /**
     * - nothing set: the latest page
     * - [after]: what is newer; with [waitSeconds] the server holds the request until something is
     * - [before]: one page further back
     */
    suspend fun page(after: Int? = null, before: Int? = null, limit: Int? = null, waitSeconds: Int? = null): Page
}

expect fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient

/**
 * The voiceControl server's `GET /api/conversation`. Ben has no backend of its own to ask: the
 * conversation is kept by the server that relays it, and read from there with a token that can
 * read and nothing else.
 */
class Api(baseUrl: String, private val token: String) : ConversationSource {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val client = createHttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            // The server holds a waiting request for up to 25 seconds before answering "nothing new".
            requestTimeoutMillis = 40_000
            socketTimeoutMillis = 40_000
            connectTimeoutMillis = 8_000
        }
    }

    override suspend fun page(after: Int?, before: Int?, limit: Int?, waitSeconds: Int?): Page {
        val response = try {
            client.get("$base/api/conversation") {
                header(HttpHeaders.Authorization, "Bearer $token")
                after?.let { parameter("after", it) }
                before?.let { parameter("before", it) }
                limit?.let { parameter("limit", it) }
                waitSeconds?.let { parameter("wait", it) }
            }
        } catch (e: CancellationException) {
            // The app went to the background mid-request. That is not a network failure.
            throw e
        } catch (e: Exception) {
            throw ApiFailure.Unreachable()
        }

        if (response.status == HttpStatusCode.Unauthorized) throw ApiFailure.Refused()
        if (!response.status.isSuccess()) throw ApiFailure.Broken(response.status.value)

        return try {
            json.decodeFromString(Page.serializer(), response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A 200 that is not a page: something else is listening on that port.
            throw ApiFailure.Broken(response.status.value)
        }
    }
}
