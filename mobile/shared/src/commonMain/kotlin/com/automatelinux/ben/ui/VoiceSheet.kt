package com.automatelinux.ben.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.automatelinux.ben.data.ApiFailure
import com.automatelinux.ben.data.VoiceSetting
import com.automatelinux.ben.ui.theme.LocalPalette
import com.automatelinux.ben.ui.theme.LocalType
import kotlinx.coroutines.launch

/** The round key in the top bar that opens [VoiceSheet]. */
@Composable
fun VoiceKey(onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .testTag("open-voice-setting")
            .size(38.dp)
            .clip(CircleShape)
            .border(1.dp, p.hairline, CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Ben's voice", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = "Ben's voice", tint = p.ink, modifier = Modifier.size(19.dp))
    }
}

/** Which voice the phone speaks Ben's words in. The choice is the server's; this shows and changes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSheet(setting: VoiceSetting, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.raised,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 22.dp)) {
            Text("Ben's voice", style = t.title, color = p.ink)
            Spacer(Modifier.height(6.dp))
            Text("What the phone sounds like when it answers you.", style = t.label, color = p.soft)
            Spacer(Modifier.height(20.dp))

            VoiceOption(
                tag = "voice-desktop",
                title = "Caption voice",
                body = "The same voice as the desktop captions. Each answer is made on the desktop and sent to the phone.",
                selected = setting.current == VoiceSetting.DESKTOP,
                saving = setting.saving == VoiceSetting.DESKTOP,
                onClick = { scope.launch { setting.choose(VoiceSetting.DESKTOP) } },
            )
            Spacer(Modifier.height(10.dp))
            VoiceOption(
                tag = "voice-phone",
                title = "Phone voice",
                body = "This phone's own text-to-speech.",
                selected = setting.current == VoiceSetting.PHONE,
                saving = setting.saving == VoiceSetting.PHONE,
                onClick = { scope.launch { setting.choose(VoiceSetting.PHONE) } },
            )

            setting.failure?.let { f ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = when (f) {
                        is ApiFailure.Unreachable -> "Can't reach the desktop, so the voice can't be read or changed right now."
                        is ApiFailure.Refused -> "The desktop refused this app's key."
                        is ApiFailure.Broken -> "The desktop answered with an error (${f.status})."
                    },
                    style = t.label,
                    color = p.voice,
                )
            }
        }
    }
}

@Composable
private fun VoiceOption(tag: String, title: String, body: String, selected: Boolean, saving: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .testTag(tag)
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) p.bubble else p.raised)
            .border(1.dp, if (selected) p.ink else p.hairline, shape)
            .clickable(role = Role.RadioButton, enabled = !selected && !saving, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = t.body, color = p.ink)
            Spacer(Modifier.height(3.dp))
            Text(if (saving) "Switching…" else body, style = t.label, color = p.soft)
        }
        Spacer(Modifier.width(14.dp))
        // A plain radio mark: a ring, filled with Ben's colour when chosen.
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, if (selected) p.voice else p.faint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(p.voice))
        }
    }
}
