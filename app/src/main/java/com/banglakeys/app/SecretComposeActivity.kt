package com.banglakeys.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.R
import com.example.ui.theme.BanglaKeysTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Secret Message ও AES-256-GCM ভল্ট স্ক্রিন:
 *  - Per-contact passphrase (AES-256-GCM দিয়ে local storage-এ সুরক্ষিত)
 *  - BKEY1: invite তৈরি/শেয়ার/ইমপোর্ট + fingerprint যাচাই
 *  - ৩টি Glyph style (Runic / Dots / Shapes)
 *  - Manual Encrypt + Decrypt (sender contact নামসহ)
 *  - AES-256-GCM এনক্রিপ্টেড লোকাল Secret Cache (গোপন দেখুন/লুকান সুইচসহ)
 */
class SecretComposeActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      BanglaKeysTheme {
        SecretComposeScreen(onNavigateBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SecretComposeScreen(onNavigateBack: () -> Unit) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var contacts by remember { mutableStateOf(SecretConfig.getContacts(context)) }
  var activeContact by remember { mutableStateOf(SecretConfig.getActiveContact(context)) }
  var selectedStyle by remember { mutableStateOf(SecretConfig.getStyle(context)) }
  var autoDetect by remember { mutableStateOf(SecretConfig.autoDetectEnabled(context)) }
  var accessibilityOn by remember { mutableStateOf(AssistantSetup.isAccessibilityOn(context)) }
  var aesSecured by remember { mutableStateOf(SecretConfig.isStorageAesSecured(context)) }
  var cacheEntries by remember { mutableStateOf(SecretConfig.getSecretCacheEntries(context)) }

  var contactName by rememberSaveable { mutableStateOf("") }
  var contactPass by rememberSaveable { mutableStateOf("") }
  var showPass by rememberSaveable { mutableStateOf(false) }
  var importCode by rememberSaveable { mutableStateOf("") }

  var plainInput by rememberSaveable { mutableStateOf("") }
  var cipherOutput by rememberSaveable { mutableStateOf("") }
  var cipherInput by rememberSaveable { mutableStateOf("") }
  var decodedSender by rememberSaveable { mutableStateOf<String?>(null) }
  var decodedOutput by rememberSaveable { mutableStateOf("") }
  var revealCacheText by rememberSaveable { mutableStateOf(false) }
  var isBusy by remember { mutableStateOf(false) }

  fun reloadState() {
    contacts = SecretConfig.getContacts(context)
    val storedActive = SecretConfig.getActiveContact(context)
    activeContact =
      if (storedActive != null && contacts.containsKey(storedActive)) storedActive
      else contacts.keys.firstOrNull()
    selectedStyle = SecretConfig.getStyle(context)
    autoDetect = SecretConfig.autoDetectEnabled(context)
    accessibilityOn = AssistantSetup.isAccessibilityOn(context)
    aesSecured = SecretConfig.isStorageAesSecured(context)
    cacheEntries = SecretConfig.getSecretCacheEntries(context)
  }

  fun readClipboardText(): String {
    return try {
      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    } catch (_: Exception) {
      ""
    }
  }

  fun copyToClipboard(label: String, text: String, ignoreMemory: Boolean = true) {
    if (text.isBlank()) return
    if (ignoreMemory) ClipboardMemory.ignoreNext(text)
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    scope.launch { snackbarHostState.showSnackbar("Copy হয়েছে") }
  }

  fun shareText(title: String, text: String) {
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
      context.startActivity(Intent.createChooser(send, title))
    } catch (_: Exception) {
      scope.launch { snackbarHostState.showSnackbar("Share করা গেল না") }
    }
  }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    reloadState()
    val clip = readClipboardText()
    if (importCode.isBlank() && SecretInvite.parse(clip) != null) {
      importCode = clip
      scope.launch {
        snackbarHostState.showSnackbar("Clipboard-এ secret invite code পাওয়া গেছে — Import চাপুন")
      }
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.secret_title)) },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("secret_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back_icon),
            )
          }
        },
      )
    },
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // 1. AES-256-GCM Security & Auto-detect status
        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            SecretSectionHeader(
              icon = Icons.Filled.Shield,
              title = "AES-256-GCM নিরাপত্তা ও Auto-Detect",
            )
            Text(
              text = "একই passphrase ছাড়া কেউ (মানুষ বা AI) secret লেখা পড়তে পারবে না। সব passphrase, draft ও local cache ফোনে AES-256-GCM ভল্টে এনক্রিপ্ট হয়ে থাকে।",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
              color = if (aesSecured) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
              else MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("aes_vault_status_card"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = if (aesSecured) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                  contentDescription = null,
                  tint = if (aesSecured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(20.dp),
                )
                Text(
                  text = if (aesSecured) "AES-256-GCM ভল্ট সক্রিয় (plaintext সংরক্ষণ বন্ধ)"
                  else "লিগ্যাসি ডেটা আপগ্রেড অপেক্ষমাণ",
                  style = MaterialTheme.typography.labelLarge,
                  color = if (aesSecured) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onErrorContainer,
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                  text = "Screen-এ Auto-Detect Bubble",
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.Medium,
                )
                Text(
                  text = "চ্যাট স্ক্রিনে secret লেখা দেখলে ভাসমান বাবলে আসল লেখা দেখাবে",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = autoDetect,
                onCheckedChange = { checked ->
                  autoDetect = checked
                  SecretConfig.setAutoDetect(context, checked)
                  if (checked && !accessibilityOn) {
                    scope.launch {
                      snackbarHostState.showSnackbar("Auto-detect-এর জন্য BanglaKeys Assistant Accessibility চালু করুন")
                    }
                  }
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("secret_autodetect_switch"),
              )
            }

            if (!accessibilityOn) {
              OutlinedButton(
                onClick = {
                  context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("open_accessibility_settings"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(imageVector = Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Accessibility Settings খুলুন (Auto-Detect-এর জন্য)")
              }
            }
          }
        }

        // 2. Contacts & Passphrases
        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            SecretSectionHeader(
              icon = Icons.Filled.PersonAdd,
              title = "Contact ও Passphrase",
            )

            if (contacts.isEmpty()) {
              Text(
                text = "এখনো কোনো contact সেভ করা হয়নি। নিচে নতুন contact যোগ করুন বা বন্ধুর BKEY1 code import করুন।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                contacts.entries.forEachIndexed { index, (name, pass) ->
                  val isActive = name == activeContact
                  val fp = remember(pass) { SecretInvite.fingerprint(pass) }
                  Surface(
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.surfaceContainer,
                    border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = RoundedCornerShape(14.dp),
                    modifier =
                      Modifier.fillMaxWidth()
                        .clickable {
                          activeContact = name
                          SecretConfig.setActiveContact(context, name)
                          scope.launch { snackbarHostState.showSnackbar("এখন $name-এর জন্য encrypt হবে") }
                        }
                        .testTag("contact_row_$index"),
                  ) {
                    Row(
                      modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                          if (isActive) {
                            Icon(
                              imageVector = Icons.Filled.CheckCircle,
                              contentDescription = "Active contact",
                              tint = MaterialTheme.colorScheme.primary,
                              modifier = Modifier.size(16.dp),
                            )
                          }
                          Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                          )
                        }
                        Text(
                          text = "Fingerprint: $fp" + if (isActive) " · সক্রিয়" else " · ট্যাপ করে বেছে নিন",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      IconButton(
                        onClick = { shareText("$name-কে Secret Code পাঠান", SecretInvite.shareText(pass)) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("share_contact_$index"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Share,
                          contentDescription = "Share invite code",
                          tint = MaterialTheme.colorScheme.primary,
                        )
                      }
                      IconButton(
                        onClick = {
                          SecretConfig.removeContact(context, name)
                          reloadState()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_contact_$index"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.DeleteOutline,
                          contentDescription = stringResource(R.string.cd_delete_icon),
                          tint = MaterialTheme.colorScheme.error,
                        )
                      }
                    }
                  }
                }
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            OutlinedTextField(
              value = contactName,
              onValueChange = { contactName = it },
              label = { Text("Contact নাম (যেমন: বন্ধু, পরিবার)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("secret_contact_name_input"),
              shape = RoundedCornerShape(14.dp),
            )

            OutlinedTextField(
              value = contactPass,
              onValueChange = { contactPass = it },
              label = { Text("Secret Passphrase") },
              singleLine = true,
              visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(
                  onClick = { showPass = !showPass },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("toggle_pass_visibility"),
                ) {
                  Icon(
                    imageVector = if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (showPass) "Hide passphrase" else "Show passphrase",
                  )
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("secret_contact_pass_input"),
              shape = RoundedCornerShape(14.dp),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = {
                  contactPass = SecretInvite.generatePassphrase()
                  showPass = true
                  scope.launch {
                    snackbarHostState.showSnackbar("নতুন random code তৈরি হয়েছে — নাম দিয়ে Save করুন")
                  }
                },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("generate_secret_code_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(imageVector = Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Random Code")
              }
              Button(
                onClick = {
                  val n = contactName.trim()
                  val p = contactPass
                  if (n.isBlank() || p.isBlank()) {
                    scope.launch { snackbarHostState.showSnackbar("নাম ও passphrase দুটোই লিখুন") }
                    return@Button
                  }
                  SecretConfig.setContact(context, n, p)
                  SecretConfig.setActiveContact(context, n)
                  contactName = ""
                  contactPass = ""
                  reloadState()
                  scope.launch {
                    snackbarHostState.showSnackbar("AES ভল্টে সেভ হয়েছে (${SecretInvite.fingerprint(p)})")
                  }
                },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("save_contact_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(imageVector = Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save করুন")
              }
            }

            Text(
              text = "সতর্কতা: Passphrase বা BKEY1 code একই chat-এ না পাঠিয়ে আলাদা মাধ্যম বা সামনাসামনি দিন।",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(
              text = "বন্ধুর BKEY1 Invite Import করুন",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
              value = importCode,
              onValueChange = { importCode = it },
              placeholder = { Text("BKEY1:... পেস্ট করুন") },
              trailingIcon = {
                IconButton(
                  onClick = {
                    val clip = readClipboardText()
                    if (clip.isNotBlank()) importCode = clip
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("paste_import_code_button"),
                ) {
                  Icon(imageVector = Icons.Filled.ContentPaste, contentDescription = "Paste invite code")
                }
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("import_code_input"),
              shape = RoundedCornerShape(14.dp),
            )
            FilledTonalButton(
              onClick = {
                val pass = SecretInvite.parse(importCode)
                if (pass == null) {
                  scope.launch { snackbarHostState.showSnackbar("সঠিক BKEY1 code পাওয়া যায়নি") }
                  return@FilledTonalButton
                }
                val resolvedName = contactName.trim().ifBlank { "Contact ${contacts.size + 1}" }
                SecretConfig.setContact(context, resolvedName, pass)
                SecretConfig.setActiveContact(context, resolvedName)
                contactName = ""
                importCode = ""
                reloadState()
                scope.launch {
                  snackbarHostState.showSnackbar("$resolvedName সেভ হয়েছে · Fingerprint: ${SecretInvite.fingerprint(pass)}")
                }
              },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("import_code_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text("Invite Code Import করুন")
            }
          }
        }

        // 3. Glyph Style
        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            SecretSectionHeader(
              icon = Icons.Filled.EnhancedEncryption,
              title = "পাঠানোর Glyph Style",
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              GlyphStyle.entries.forEach { style ->
                FilterChip(
                  selected = style == selectedStyle,
                  onClick = {
                    selectedStyle = style
                    SecretConfig.setStyle(context, style)
                  },
                  label = { Text(style.label) },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("style_chip_${style.name}"),
                )
              }
            }
          }
        }

        // 4. Manual Encrypt & Decrypt
        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            SecretSectionHeader(
              icon = Icons.Filled.Lock,
              title = "Encrypt ও Decrypt করুন",
            )
            Text(
              text = if (activeContact != null) "সক্রিয় প্রাপক: $activeContact"
              else "উপরে থেকে একটি contact বেছে নিন বা যোগ করুন",
              style = MaterialTheme.typography.labelLarge,
              color = if (activeContact != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )

            OutlinedTextField(
              value = plainInput,
              onValueChange = { plainInput = it },
              label = { Text("আসল গোপন বার্তা লিখুন") },
              minLines = 2,
              modifier = Modifier.fillMaxWidth().testTag("manual_plain_input"),
              shape = RoundedCornerShape(14.dp),
            )

            Button(
              enabled = !isBusy,
              onClick = {
                val targetName = activeContact
                val pass = targetName?.let { contacts[it] }
                if (targetName == null || pass.isNullOrBlank()) {
                  scope.launch { snackbarHostState.showSnackbar("আগে একটি contact সেভ/সিলেক্ট করুন") }
                  return@Button
                }
                val text = plainInput.trim()
                if (text.isBlank()) return@Button
                isBusy = true
                scope.launch {
                  val glyph = withContext(Dispatchers.Default) {
                    GlyphAlphabet.encode(SecretCipher.encrypt(text, pass), selectedStyle)
                  }
                  SecretConfig.saveSecretCacheEntry(context, targetName, text, glyph, selectedStyle, isOutgoing = true)
                  cipherOutput = glyph
                  reloadState()
                  isBusy = false
                }
              },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("manual_encrypt_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text("🔒 Encrypt করুন ও Cache-এ রাখুন")
            }

            if (cipherOutput.isNotEmpty()) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(
                    text = cipherOutput,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("manual_cipher_output"),
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                      onClick = { copyToClipboard("secret", cipherOutput) },
                      modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("copy_cipher_button"),
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Copy")
                    }
                    OutlinedButton(
                      onClick = { shareText("Secret Message পাঠান", cipherOutput) },
                      modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("share_cipher_button"),
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Share")
                    }
                  }
                }
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            OutlinedTextField(
              value = cipherInput,
              onValueChange = { cipherInput = it },
              label = { Text("পাওয়া Secret Ciphertext পেস্ট করুন") },
              trailingIcon = {
                IconButton(
                  onClick = {
                    val clip = readClipboardText()
                    if (clip.isNotBlank()) cipherInput = clip
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("paste_cipher_button"),
                ) {
                  Icon(imageVector = Icons.Filled.ContentPaste, contentDescription = "Paste cipher text")
                }
              },
              minLines = 2,
              modifier = Modifier.fillMaxWidth().testTag("manual_cipher_input"),
              shape = RoundedCornerShape(14.dp),
            )

            FilledTonalButton(
              enabled = !isBusy,
              onClick = {
                if (contacts.isEmpty()) {
                  scope.launch { snackbarHostState.showSnackbar("আগে অন্তত একটি contact/passphrase সেভ করুন") }
                  return@FilledTonalButton
                }
                val rawCipher = cipherInput.trim()
                if (rawCipher.isBlank()) return@FilledTonalButton
                isBusy = true
                scope.launch {
                  val match = withContext(Dispatchers.Default) {
                    val bytes = GlyphAlphabet.decodeAny(rawCipher)
                    bytes?.let { SecretCipher.decryptWithMatchingContact(it, contacts) }
                  }
                  if (match != null) {
                    val style = GlyphAlphabet.detectStyle(rawCipher) ?: GlyphStyle.RUNIC
                    SecretConfig.saveSecretCacheEntry(context, match.first, match.second, rawCipher, style, isOutgoing = false)
                    decodedSender = match.first
                    decodedOutput = match.second
                    reloadState()
                  } else {
                    decodedSender = null
                    decodedOutput = "পড়া গেল না — কোনো সেভ করা passphrase মিলছে না, বা লেখা অসম্পূর্ণ।"
                  }
                  isBusy = false
                }
              },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("manual_decrypt_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text("🔓 Decrypt করুন")
            }

            if (decodedOutput.isNotEmpty()) {
              Surface(
                color = if (decodedSender != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("manual_decoded_box"),
              ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  if (decodedSender != null) {
                    Text(
                      text = "প্রেরক: $decodedSender",
                      style = MaterialTheme.typography.labelLarge,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                  Text(
                    text = decodedOutput,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (decodedSender != null) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.testTag("manual_decoded_output"),
                  )
                }
              }
            }
          }
        }

        // 5. AES-256-GCM Encrypted Local Message Cache
        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Filled.Shield,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
                Text(
                  text = "AES Encrypted Local Cache",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              IconButton(
                onClick = { revealCacheText = !revealCacheText },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("toggle_cache_reveal"),
              ) {
                Icon(
                  imageVector = if (revealCacheText) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription = if (revealCacheText) "Hide decrypted cache" else "Reveal decrypted cache",
                )
              }
              if (cacheEntries.isNotEmpty()) {
                TextButton(
                  onClick = {
                    SecretConfig.clearSecretCache(context)
                    reloadState()
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_secret_cache_button"),
                ) {
                  Text("সব মুছুন")
                }
              }
            }

            Text(
              text = "সাম্প্রতিক পাঠানো ও ডিক্রিপ্ট করা মেসেজ লোকাল cache-এ AES-256-GCM দিয়ে এনক্রিপ্ট করে রাখা হয়।",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (cacheEntries.isEmpty()) {
              Text(
                text = "এখনো কোনো encrypted cache এন্ট্রি নেই।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
              )
            } else {
              val fmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cacheEntries.forEachIndexed { idx, entry ->
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("secret_cache_row_$idx"),
                  ) {
                    Row(
                      modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                          text = (if (entry.isOutgoing) "প্রেরিত → " else "প্রাপ্ত ← ") +
                            "${entry.contactName} · ${fmt.format(Date(entry.timestampMs))}",
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                          text = if (revealCacheText) entry.plainText else "•••••••• (চোখের আইকনে ট্যাপ করে দেখুন)",
                          style = MaterialTheme.typography.bodyMedium,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text = "${entry.styleName}: ${entry.glyphText.take(28)}…",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                      IconButton(
                        onClick = { copyToClipboard("secret", entry.glyphText) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("cache_copy_$idx"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.ContentCopy,
                          contentDescription = stringResource(R.string.cd_copy_icon),
                          tint = MaterialTheme.colorScheme.primary,
                        )
                      }
                      IconButton(
                        onClick = {
                          SecretConfig.removeSecretCacheEntry(context, entry.id)
                          reloadState()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("cache_delete_$idx"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.DeleteOutline,
                          contentDescription = stringResource(R.string.cd_delete_icon),
                          tint = MaterialTheme.colorScheme.error,
                        )
                      }
                    }
                  }
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
private fun SecretSectionHeader(
  icon: ImageVector,
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
