package com.automatelinux.ben.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One line of the record the voiceControl server keeps (its `server/lib/conversation.mjs`).
 * A turn is two of them sharing a [turn] id: what was heard, then how it ended.
 */
@Serializable
data class Event(
    /** Position in the server's file. Contiguous, starting at 1. */
    val seq: Int,
    /** When it happened, by the server's clock (epoch milliseconds). */
    val ts: Long,
    val turn: String,
    val kind: String,
    val text: String = "",
    /** What Ben had the phone do alongside a reply — `{"type":"open_app","package_hint":"waze"}`. */
    val actions: List<JsonObject> = emptyList(),
    /** Why a turn failed: `timeout`, `error` or `interrupted`. */
    val reason: String? = null,
)

object Kind {
    const val HEARD = "heard"
    const val REPLY = "reply"
    const val FAILED = "failed"
}

/** The server's answer to `GET /api/conversation`. */
@Serializable
data class Page(
    /** Names the file the events came from. A different epoch is a different conversation record. */
    val epoch: String,
    val events: List<Event>,
    /** More events remain in the direction that was asked for. */
    val more: Boolean,
    /** The newest seq the server holds. */
    val last: Int,
    /** The server's clock when it answered. */
    val now: Long,
)
