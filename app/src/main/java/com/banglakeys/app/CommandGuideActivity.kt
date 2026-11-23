package com.banglakeys.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BanglaKeysTheme
import kotlinx.coroutines.launch

class CommandGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BanglaKeysTheme {
                CommandGuideScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommandGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryKey by rememberSaveable { mutableStateOf(CommandCatalog.Category.ALL.key) }
    val selectedCategory = remember(selectedCategoryKey) {
        CommandCatalog.Category.entries.firstOrNull { it.key == selectedCategoryKey }
            ?: CommandCatalog.Category.ALL
    }

    var customPhrase by rememberSaveable { mutableStateOf("") }
    var customPreviewSummary by rememberSaveable { mutableStateOf<String?>(null) }
    var customResolvedCommand by remember { mutableStateOf<Command?>(null) }
    var activeTryPhrase by rememberSaveable { mutableStateOf<String?>(null) }
    var activeTryTitle by rememberSaveable { mutableStateOf<String?>(null) }

    val filteredEntries = remember(selectedCategory, searchQuery) {
        CommandCatalog.filter(selectedCategory, searchQuery)
    }

    fun inspectPhrase(phrase: String): Pair<Command?, String> {
        val clean = phrase.trim()
        if (clean.isEmpty()) return null to "আগে একটি কমান্ড লিখুন বা সিলেক্ট করুন"
        val res = SmartCommandResolver.resolveWithMemory(
            candidates = listOf(clean),
            alreadyAwake = true,
            memory = AssistantMemory.shared,
            foregroundPackage = AssistantAccessibilityService.instance?.foregroundPackage().orEmpty()
        )
        val cmd = res.command
        val desc = if (cmd != null) {
            "সঠিক কমান্ড: ${res.summary ?: SmartCommandResolver.describe(cmd)}"
        } else {
            "সরাসরি চিনতে পারিনি — উদাহরণের ফরম্যাটে বলুন"
        }
        return cmd to desc
    }

    fun executeWithDelay(cmd: Command, delayMs: Long) {
        if (delayMs > 0L) {
            Toast.makeText(context, "৩ সেকেন্ডের মধ্যে টার্গেট অ্যাপে যান…", Toast.LENGTH_SHORT).show()
        }
        mainHandler.postDelayed({
            val exec = CommandExecutor(context.applicationContext)
            if (exec.isAsync(cmd)) {
                scope.launch {
                    val result = exec.executeAsync(cmd)
                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                }
            } else {
                val result = exec.execute(cmd)
                if (result.isNotBlank()) {
                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                }
            }
        }, delayMs)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("command_guide_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "কমান্ড গাইড (৯৯% নির্ভুল)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "প্রতিটি অ্যাপের কথা বলার সঠিক নিয়ম ও লাইভ টেস্ট",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "search_bar") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("কমান্ড খুঁজুন (যেমন chrome, comment, ektu, call)") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("command_guide_search")
                    )
                }

                item(key = "category_chips") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(CommandCatalog.Category.entries, key = { it.key }) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategoryKey = cat.key },
                                label = { Text(cat.labelBn) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("category_chip_${cat.key}")
                            )
                        }
                    }
                }

                item(key = "tester_card") {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "লাইভ কমান্ড টেস্টার",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = customPhrase,
                                onValueChange = {
                                    customPhrase = it
                                    val (cmd, desc) = inspectPhrase(it)
                                    customResolvedCommand = cmd
                                    customPreviewSummary = if (it.isBlank()) null else desc
                                },
                                placeholder = { Text("যেমন: ektu niche বা chrome e prothomalo.com khulo") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("custom_phrase_input")
                            )
                            if (customPreviewSummary != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = if (customResolvedCommand != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = customPreviewSummary.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.testTag("custom_phrase_result")
                                        )
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val (cmd, desc) = inspectPhrase(customPhrase)
                                        customResolvedCommand = cmd
                                        customPreviewSummary = desc
                                    },
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("test_phrase_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Parse দেখুন")
                                }
                                Button(
                                    onClick = {
                                        val (cmd, desc) = inspectPhrase(customPhrase)
                                        customResolvedCommand = cmd
                                        customPreviewSummary = desc
                                        if (cmd != null) {
                                            executeWithDelay(cmd, delayMs = 3000L)
                                        }
                                    },
                                    enabled = customPhrase.isNotBlank(),
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("run_phrase_delayed_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("৩সে. পর চালান")
                                }
                            }
                        }
                    }
                }

                if (filteredEntries.isEmpty()) {
                    item(key = "empty_state") {
                        ElevatedCard(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "কোনো কমান্ড পাওয়া যায়নি",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "অন্য কোনো শব্দ বা ক্যাটাগরি দিয়ে খুঁজে দেখুন।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredEntries, key = { it.id }) { entry ->
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().testTag("guide_card_${entry.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(99.dp)
                                    ) {
                                        Text(
                                            text = entry.category.labelBn,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = entry.titleBn,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = entry.descriptionBn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "যেভাবে বলবেন (ট্যাপ করলে কপি হবে):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    entry.examples.forEach { example ->
                                        AssistChip(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(example))
                                                customPhrase = example
                                                val (cmd, desc) = inspectPhrase(example)
                                                customResolvedCommand = cmd
                                                customPreviewSummary = desc
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("কপি ও টেস্টারে লোড হয়েছে: $example")
                                                }
                                            },
                                            label = { Text("\"$example\"") }
                                        )
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lightbulb,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = entry.tipBn,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(entry.testPhrase))
                                            scope.launch {
                                                snackbarHostState.showSnackbar("কপি হয়েছে: ${entry.testPhrase}")
                                            }
                                        },
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("copy_button_${entry.id}"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copy")
                                    }
                                    Button(
                                        onClick = {
                                            activeTryTitle = entry.titleBn
                                            activeTryPhrase = entry.testPhrase
                                        },
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("try_button_${entry.id}"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Try")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val tryPhrase = activeTryPhrase
    if (tryPhrase != null) {
        val (resolvedCmd, resolvedSummary) = remember(tryPhrase) { inspectPhrase(tryPhrase) }
        AlertDialog(
            onDismissRequest = { activeTryPhrase = null },
            title = { Text(activeTryTitle ?: "Try Command", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ভয়েস কমান্ড: \"$tryPhrase\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = resolvedSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Text(
                        text = "আপনি এখনই অথবা ৩ সেকেন্ড পর (অন্য অ্যাপে গিয়ে) কমান্ডটি চালাতে পারেন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeTryPhrase = null
                        if (resolvedCmd != null) executeWithDelay(resolvedCmd, delayMs = 3000L)
                    },
                    enabled = resolvedCmd != null,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("try_dialog_run_delayed")
                ) {
                    Text("৩সে. পর চালান")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { activeTryPhrase = null },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("বন্ধ")
                    }
                    if (resolvedCmd != null) {
                        TextButton(
                            onClick = {
                                activeTryPhrase = null
                                executeWithDelay(resolvedCmd, delayMs = 0L)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("try_dialog_run_now")
                        ) {
                            Text("এখনই")
                        }
                    }
                }
            }
        )
    }
}
