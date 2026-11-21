package com.banglakeys.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class ImeStatus {
  ACTIVE,
  ENABLED_ONLY,
  DISABLED,
}

private fun checkImeStatus(context: Context): ImeStatus {
  val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  val expectedComponent = ComponentName(context, BanglaIME::class.java)
  val isEnabled =
    imm.enabledInputMethodList.any { info ->
      info.component == expectedComponent || info.packageName == context.packageName
    }
  val defaultIme =
    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
  val isDefault =
    defaultIme == expectedComponent.flattenToShortString() ||
      defaultIme == expectedComponent.flattenToString()

  return when {
    isEnabled && isDefault -> ImeStatus.ACTIVE
    isEnabled -> ImeStatus.ENABLED_ONLY
    else -> ImeStatus.DISABLED
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BanglaKeysScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val prefs = remember { context.getSharedPreferences("bk", Context.MODE_PRIVATE) }

  var imeStatus by remember { mutableStateOf(checkImeStatus(context)) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    imeStatus = checkImeStatus(context)
  }

  var haptic by remember { mutableStateOf(prefs.getBoolean("haptic", true)) }
  var sound by remember { mutableStateOf(prefs.getBoolean("sound", false)) }
  var numRow by remember { mutableStateOf(prefs.getBoolean("numrow", false)) }
  var bnDigits by remember { mutableStateOf(prefs.getBoolean("bndigits", true)) }
  var heightPct by remember { mutableIntStateOf(prefs.getInt("height", 100).coerceIn(85, 125)) }

  var testInput by rememberSaveable { mutableStateOf("") }
  var phoneticSampleInput by rememberSaveable { mutableStateOf("ami tOmake bhalobashi") }
  val convertedBangla = remember(phoneticSampleInput) { Phonetic.convert(phoneticSampleInput) }
  val copiedMsg = stringResource(R.string.copied_snackbar)

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        HeroHeaderCard(imeStatus = imeStatus)

        SetupCard(
          onEnableClick = {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
          },
          onSelectClick = {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
          },
        )

        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            SectionTitle(
              icon = Icons.Filled.EditNote,
              title = stringResource(R.string.btn_open_notepad),
            )
            Text(
              text = stringResource(R.string.notepad_card_desc),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = { context.startActivity(Intent(context, NotepadActivity::class.java)) },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("open_notepad_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.EditNote,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.btn_open_notepad))
            }
          }
        }

        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            SectionTitle(
              icon = Icons.Outlined.TouchApp,
              title = stringResource(R.string.section_test),
            )
            OutlinedTextField(
              value = testInput,
              onValueChange = { testInput = it },
              label = { Text(stringResource(R.string.test_field_label)) },
              placeholder = { Text(stringResource(R.string.test_field_placeholder)) },
              minLines = 3,
              modifier = Modifier.fillMaxWidth().testTag("ime_test_input"),
              shape = RoundedCornerShape(14.dp),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(
              text = stringResource(R.string.phonetic_preview_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
              value = phoneticSampleInput,
              onValueChange = { phoneticSampleInput = it },
              placeholder = { Text(stringResource(R.string.phonetic_preview_hint)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("phonetic_preview_input"),
              shape = RoundedCornerShape(14.dp),
            )
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = convertedBangla.ifEmpty { "—" },
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.weight(1f).testTag("phonetic_converted_output"),
                )
                IconButton(
                  onClick = {
                    if (convertedBangla.isNotEmpty()) {
                      clipboardManager.setText(AnnotatedString(convertedBangla))
                      scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                    }
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_bangla_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_icon),
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              listOf("ami", "bangla", "sOnar", "kkhomota", "tOmake", "bhalobashi").forEach { sample ->
                AssistChip(
                  onClick = { phoneticSampleInput = sample },
                  label = { Text(sample) },
                  modifier = Modifier.testTag("sample_chip_$sample"),
                )
              }
            }
          }
        }

        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SectionTitle(
              icon = Icons.Filled.Tune,
              title = stringResource(R.string.section_settings),
            )

            PreferenceToggleRow(
              title = stringResource(R.string.pref_haptic_title),
              subtitle = stringResource(R.string.pref_haptic_desc),
              checked = haptic,
              testTag = "toggle_haptic",
              onCheckedChange = {
                haptic = it
                prefs.edit().putBoolean("haptic", it).apply()
              },
            )
            PreferenceToggleRow(
              title = stringResource(R.string.pref_sound_title),
              subtitle = stringResource(R.string.pref_sound_desc),
              checked = sound,
              testTag = "toggle_sound",
              onCheckedChange = {
                sound = it
                prefs.edit().putBoolean("sound", it).apply()
              },
            )
            PreferenceToggleRow(
              title = stringResource(R.string.pref_numrow_title),
              subtitle = stringResource(R.string.pref_numrow_desc),
              checked = numRow,
              testTag = "toggle_numrow",
              onCheckedChange = {
                numRow = it
                prefs.edit().putBoolean("numrow", it).apply()
              },
            )
            PreferenceToggleRow(
              title = stringResource(R.string.pref_bndigits_title),
              subtitle = stringResource(R.string.pref_bndigits_desc),
              checked = bnDigits,
              testTag = "toggle_bndigits",
              onCheckedChange = {
                bnDigits = it
                prefs.edit().putBoolean("bndigits", it).apply()
              },
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = stringResource(R.string.pref_height_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
              )
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = "$heightPct%",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
            Slider(
              value = heightPct.toFloat(),
              onValueChange = { value ->
                val snapped = value.roundToInt().coerceIn(85, 125)
                heightPct = snapped
                prefs.edit().putInt("height", snapped).apply()
              },
              valueRange = 85f..125f,
              modifier = Modifier.fillMaxWidth().testTag("height_slider"),
            )
          }
        }

        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            SectionTitle(
              icon = Icons.Outlined.Info,
              title = stringResource(R.string.section_tips),
            )
            Text(
              text = stringResource(R.string.tips_summary),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              listOf(
                "O → ও / ো",
                "o → অ / ভাঙা",
                "ng → ং",
                "Ng → ঙ",
                "NG → ঞ",
                "^ → ঁ",
                "t`` → ৎ",
                "kkh → ক্ষ",
                "rri → ঋ / ৃ",
              ).forEach { rule ->
                Surface(
                  color = MaterialTheme.colorScheme.surfaceContainer,
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Text(
                    text = rule,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }
}

@Composable
private fun HeroHeaderCard(imeStatus: ImeStatus) {
  val gradient =
    Brush.linearGradient(
      colors = listOf(Color(0xFF015E57), Color(0xFF00796B), Color(0xFF00897B))
    )
  val statusText =
    when (imeStatus) {
      ImeStatus.ACTIVE -> stringResource(R.string.status_active)
      ImeStatus.ENABLED_ONLY -> stringResource(R.string.status_enabled)
      ImeStatus.DISABLED -> stringResource(R.string.status_disabled)
    }

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(gradient)
        .padding(20.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.banglakeys_icon),
        contentDescription = stringResource(R.string.cd_app_icon),
        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
      )
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = Color.White,
        )
        Text(
          text = stringResource(R.string.hero_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.88f),
        )
        Surface(
          color = Color.White.copy(alpha = 0.18f),
          shape = CircleShape,
          modifier = Modifier.testTag("ime_status_badge"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector =
                if (imeStatus == ImeStatus.ACTIVE) Icons.Filled.CheckCircle else Icons.Filled.Keyboard,
              contentDescription = stringResource(R.string.cd_status_icon),
              tint = Color.White,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = statusText,
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SetupCard(
  onEnableClick: () -> Unit,
  onSelectClick: () -> Unit,
) {
  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = RoundedCornerShape(20.dp),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SectionTitle(
        icon = Icons.Filled.Settings,
        title = stringResource(R.string.setup_title),
      )
      Text(
        text = stringResource(R.string.setup_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(
        onClick = onEnableClick,
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("enable_keyboard_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
      ) {
        Icon(
          imageVector = Icons.Filled.Settings,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.btn_enable_keyboard))
      }
      FilledTonalButton(
        onClick = onSelectClick,
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("select_keyboard_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Keyboard,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.btn_select_keyboard))
      }
    }
  }
}

@Composable
private fun SectionTitle(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun PreferenceToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  testTag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
    )
  }
}
