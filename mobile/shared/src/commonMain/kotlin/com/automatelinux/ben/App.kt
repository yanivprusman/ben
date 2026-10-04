package com.automatelinux.ben

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.automatelinux.ben.data.Api
import com.automatelinux.ben.data.Conversation
import com.automatelinux.ben.data.KeyValueStore
import com.automatelinux.ben.ui.ConversationScreen
import com.automatelinux.ben.ui.theme.AppTheme
import com.automatelinux.ben.ui.theme.Fonts
import kotlinx.datetime.Clock

/**
 * Ben: the conversation held through the wake phrase, and nothing else.
 *
 * The app owns no data. The voiceControl server on the desktop writes every turn down as it
 * happens; this reads it back, keeps a copy for when the desktop cannot be reached, and stays
 * on the line while it is on screen so a new sentence appears as it is said.
 *
 * @param baseUrl the voiceControl server
 * @param token   its read-only token — this app can show the conversation, never send into it
 * @param active  true between onStart and onStop; nothing is asked of the server in the background
 * @param platformConfirmsCopy the system itself shows what was copied (Android 13+)
 */
@Composable
fun App(baseUrl: String, token: String, store: KeyValueStore, fonts: Fonts, active: Boolean, platformConfirmsCopy: Boolean) {
    val now = remember { { Clock.System.now().toEpochMilliseconds() } }
    val conversation = remember { Conversation(Api(baseUrl, token), store, now) }

    LaunchedEffect(active) {
        if (active) conversation.follow()
    }

    AppTheme(fonts) {
        ConversationScreen(conversation, now, platformConfirmsCopy)
    }
}
