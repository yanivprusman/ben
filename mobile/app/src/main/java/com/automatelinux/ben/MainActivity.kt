package com.automatelinux.ben

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.automatelinux.ben.data.KeyValueStore
import com.automatelinux.ben.ui.theme.Fonts
import java.io.File

// Thin Android launcher — all UI lives in the shared commonMain App() composable.
class MainActivity : ComponentActivity() {

    /** True between onStart and onStop; the shared code only stays on the line while it is. */
    private var active by mutableStateOf(false)

    /** Instrument Sans and Newsreader (both OFL, see app/licenses), cut to the weights used. */
    private val fonts = Fonts(
        sans = FontFamily(
            Font(R.font.sans_regular, FontWeight.Normal),
            Font(R.font.sans_medium, FontWeight.Medium),
            Font(R.font.sans_semibold, FontWeight.SemiBold),
        ),
        serif = FontFamily(
            Font(R.font.serif_regular, FontWeight.Normal),
            Font(R.font.serif_medium, FontWeight.Medium),
            Font(R.font.serif_italic, FontWeight.Normal, FontStyle.Italic),
        ),
        display = FontFamily(Font(R.font.serif_display, FontWeight.Medium)),
    )

    /**
     * The saved copy of the conversation, in app-private storage. Written to a temp file and
     * renamed, so being killed mid-write leaves the previous copy, never half of a new one.
     */
    private val store by lazy {
        val dir = File(filesDir, "store").apply { mkdirs() }
        object : KeyValueStore {
            override fun get(key: String): String? = File(dir, "$key.json").takeIf { it.exists() }?.readText()

            override fun put(key: String, value: String) {
                val file = File(dir, "$key.json")
                val tmp = File(dir, "$key.json.tmp")
                tmp.writeText(value)
                if (!tmp.renameTo(file)) throw IllegalStateException("could not save ${file.name}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // enableEdgeToEdge guesses the system-bar icon colour once; follow the theme instead.
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            App(
                baseUrl = BuildConfig.API_BASE_URL,
                token = BuildConfig.API_TOKEN,
                store = store,
                fonts = fonts,
                active = active,
                platformConfirmsCopy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        active = true
    }

    override fun onStop() {
        active = false
        super.onStop()
    }
}
