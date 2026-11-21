package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.banglakeys.app.BanglaKeysScreen
import com.example.ui.theme.BanglaKeysTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      BanglaKeysTheme {
        BanglaKeysScreen()
      }
    }
  }
}
