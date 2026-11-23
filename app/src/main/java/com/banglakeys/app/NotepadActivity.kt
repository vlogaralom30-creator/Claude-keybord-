package com.banglakeys.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.R
import com.example.ui.theme.BanglaKeysTheme
import com.example.ui.theme.ProvideSafeTextToolbar
import kotlinx.coroutines.launch

class NotepadActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      BanglaKeysTheme {
        NotepadScreen(onNavigateBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadScreen(onNavigateBack: () -> Unit) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var notesRepo by remember { mutableStateOf(Notes(context)) }
  var clipboardRepo by remember { mutableStateOf(ClipboardMemory(context)) }
  var savedNotes by remember { mutableStateOf(notesRepo.all()) }
  var recentClips by remember { mutableStateOf(clipboardRepo.clips()) }
  var newNoteText by rememberSaveable { mutableStateOf("") }
  var editingKind by rememberSaveable { mutableStateOf<String?>(null) }
  var editingIndex by rememberSaveable { mutableStateOf(-1) }
  var editingOldText by rememberSaveable { mutableStateOf("") }
  var editingDraft by rememberSaveable { mutableStateOf("") }

  val copiedMsg = stringResource(R.string.copied_snackbar)

  fun reloadAll() {
    notesRepo = Notes(context)
    clipboardRepo = ClipboardMemory(context)
    savedNotes = notesRepo.all()
    recentClips = clipboardRepo.clips()
  }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    reloadAll()
  }

  fun copyText(text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("BanglaKeys", text))
    clipboardRepo.add(text)
    recentClips = clipboardRepo.clips()
    scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.notepad_title)) },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("notepad_back_button"),
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
        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.notepad_notes_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }
            Text(
              text = stringResource(R.string.notepad_notes_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = newNoteText,
                onValueChange = { newNoteText = it },
                placeholder = { Text(stringResource(R.string.notepad_input_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("notepad_note_input"),
                shape = RoundedCornerShape(14.dp),
              )
              Button(
                onClick = {
                  if (newNoteText.isNotBlank()) {
                    notesRepo.add(newNoteText)
                    savedNotes = notesRepo.all()
                    newNoteText = ""
                  }
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("notepad_add_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.notepad_add_btn))
              }
            }

            if (savedNotes.isEmpty()) {
              Text(
                text = stringResource(R.string.notepad_empty_notes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
              )
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                savedNotes.forEachIndexed { index, note ->
                  SnippetRow(
                    text = note,
                    testTagPrefix = "note_$index",
                    onEdit = {
                      editingKind = "note"
                      editingIndex = index
                      editingOldText = note
                      editingDraft = note
                    },
                    onCopy = { copyText(note) },
                    onDelete = {
                      notesRepo.remove(index)
                      savedNotes = notesRepo.all()
                    },
                  )
                }
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
                  imageVector = Icons.Filled.ContentPaste,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = stringResource(R.string.notepad_clips_title),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              if (recentClips.isNotEmpty()) {
                TextButton(
                  onClick = {
                    clipboardRepo.clear()
                    recentClips = clipboardRepo.clips()
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_clips_button"),
                ) {
                  Text(stringResource(R.string.notepad_clear_all))
                }
              }
            }
            Text(
              text = stringResource(R.string.notepad_clips_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (recentClips.isEmpty()) {
              Text(
                text = stringResource(R.string.notepad_empty_clips),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
              )
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentClips.forEachIndexed { index, clip ->
                  SnippetRow(
                    text = clip,
                    testTagPrefix = "clip_$index",
                    onPin = {
                      notesRepo.add(clip)
                      clipboardRepo.remove(clip)
                      savedNotes = notesRepo.all()
                      recentClips = clipboardRepo.clips()
                      scope.launch { snackbarHostState.showSnackbar("📌 Pinned হয়েছে") }
                    },
                    onEdit = {
                      editingKind = "clip"
                      editingIndex = -1
                      editingOldText = clip
                      editingDraft = clip
                    },
                    onCopy = { copyText(clip) },
                    onDelete = {
                      clipboardRepo.remove(clip)
                      recentClips = clipboardRepo.clips()
                    },
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
      }
    }

    if (editingKind != null) {
      AlertDialog(
        onDismissRequest = { editingKind = null },
        title = { Text(stringResource(R.string.notepad_edit_title)) },
        text = {
          ProvideSafeTextToolbar {
            OutlinedTextField(
              value = editingDraft,
              onValueChange = { editingDraft = it },
              minLines = 3,
              maxLines = 6,
              modifier = Modifier.fillMaxWidth().testTag("notepad_edit_input"),
              shape = RoundedCornerShape(14.dp),
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val trimmed = editingDraft.trim()
              when (editingKind) {
                "note" -> {
                  if (trimmed.isNotEmpty()) notesRepo.update(editingIndex, trimmed)
                  else notesRepo.remove(editingIndex)
                  savedNotes = notesRepo.all()
                }
                "clip" -> {
                  if (trimmed.isNotEmpty()) clipboardRepo.update(editingOldText, trimmed)
                  else clipboardRepo.remove(editingOldText)
                  recentClips = clipboardRepo.clips()
                }
              }
              editingKind = null
            },
            modifier = Modifier.testTag("notepad_edit_save"),
          ) {
            Text(stringResource(R.string.btn_save))
          }
        },
        dismissButton = {
          TextButton(
            onClick = { editingKind = null },
            modifier = Modifier.testTag("notepad_edit_cancel"),
          ) {
            Text(stringResource(R.string.btn_cancel))
          }
        },
      )
    }
  }
}

@Composable
private fun SnippetRow(
  text: String,
  testTagPrefix: String,
  onPin: (() -> Unit)? = null,
  onEdit: () -> Unit,
  onCopy: () -> Unit,
  onDelete: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (onPin != null) {
          IconButton(
            onClick = onPin,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("${testTagPrefix}_pin"),
          ) {
            Icon(
              imageVector = Icons.Outlined.PushPin,
              contentDescription = stringResource(R.string.cd_pin_icon),
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
        IconButton(
          onClick = onEdit,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("${testTagPrefix}_edit"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = stringResource(R.string.cd_edit_icon),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onCopy,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("${testTagPrefix}_copy"),
        ) {
          Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = stringResource(R.string.cd_copy_icon),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("${testTagPrefix}_delete"),
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
