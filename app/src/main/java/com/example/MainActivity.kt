package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.banglakeys.app.BanglaKeysScreen
import com.banglakeys.app.EmoAppOpenScreen
import com.example.ui.theme.BanglaKeysTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      BanglaKeysTheme {
        var showOpenScreen by rememberSaveable { mutableStateOf(true) }
        Crossfade(
          targetState = showOpenScreen,
          animationSpec = tween(durationMillis = 320),
          label = "main_open_transition",
        ) { opening ->
          if (opening) {
            EmoAppOpenScreen(onFinished = { showOpenScreen = false })
          } else {
            BanglaKeysScreen()
          }
        }
      }
    }
  }
}
