package com.banglakeys.app.robot

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.theme.BanglaKeysTheme
import kotlinx.coroutines.flow.StateFlow

class EmoLearningViewModel(application: Application) : AndroidViewModel(application) {
    val state: StateFlow<EmoLearningSnapshot> = EmoLearningStore.state

    init {
        refresh()
    }

    fun refresh() {
        EmoLearningStore.snapshot(getApplication())
    }

    fun setLearningEnabled(enabled: Boolean) {
        EmoLearningStore.setEnabled(getApplication(), enabled)
        val snap = EmoLearningStore.snapshot(getApplication())
        EmoBrainHub.express(
            face = if (enabled) snap.stage.face else FaceId.CALM,
            speech = if (enabled) "Learning Mode চালু হলো!" else "Learning Mode বিরতিতে",
            forceSpeech = true,
        )
    }

    fun teachFromSandbox(text: String) {
        EmoLearningStore.recordSandboxInput(getApplication(), text)
    }

    fun resetAll() {
        EmoLearningStore.reset(getApplication(), clearVocabularyAndRecents = true)
        EmoBrainHub.express(
            face = FaceId.CURIOUS,
            speech = "নতুন করে শুরু করছি!",
            forceSpeech = true,
        )
    }
}

private object LearningPal {
    val Bg = Color(0xFF080C12)
    val Card = Color(0xFF0F1622)
    val CardAlt = Color(0xFF152030)
    val Border = Color(0xFF1F3448)
    val Cyan = Color(0xFF36D1FF)
    val CyanSoft = Color(0xFF0D2A3C)
    val Mint = Color(0xFF22E58A)
    val MintSoft = Color(0xFF0D2E20)
    val Coral = Color(0xFFFF6B9C)
    val CoralSoft = Color(0xFF351422)
    val TextPrimary = Color(0xFFF1F7FF)
    val TextSub = Color(0xFF8CA8C2)
}

class EmoLearningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BanglaKeysTheme(darkTheme = true, dynamicColor = false) {
                val vm: EmoLearningViewModel = viewModel()
                EmoLearningScreen(
                    viewModel = vm,
                    onBack = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmoLearningScreen(
    viewModel: EmoLearningViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snap by viewModel.state.collectAsStateWithLifecycle()
    var sandboxText by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = LearningPal.Bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LearningPal.Bg,
                    titleContentColor = LearningPal.TextPrimary,
                    navigationIconContentColor = LearningPal.TextPrimary,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_learning_back"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_icon),
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.emo_learning_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.emo_learning_subtitle),
                            fontSize = 11.5.sp,
                            color = LearningPal.TextSub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    Surface(
                        color = if (snap.enabled) LearningPal.MintSoft else LearningPal.CardAlt,
                        shape = RoundedCornerShape(99.dp),
                        border = BorderStroke(
                            1.dp,
                            if (snap.enabled) LearningPal.Mint.copy(alpha = 0.55f) else LearningPal.Border,
                        ),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text(
                            text = if (snap.enabled) "Active" else "Paused",
                            color = if (snap.enabled) LearningPal.Mint else LearningPal.TextSub,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            LearningPal.Bg,
                            Color(0xFF0A111B),
                            LearningPal.Bg,
                        )
                    )
                ),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 1. Hero EMO Brain & Overall Understanding Card
                Surface(
                    color = LearningPal.Card,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, LearningPal.Cyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 116.dp, height = 80.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF05080D))
                                    .border(1.dp, LearningPal.Cyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                    .testTag("learning_face_view"),
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        LearningVisorFaceView(ctx).apply {
                                            currentFace = snap.stage.face
                                        }
                                    },
                                    update = { view ->
                                        view.currentFace = snap.stage.face
                                        view.invalidate()
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Surface(
                                    color = LearningPal.CyanSoft,
                                    shape = RoundedCornerShape(99.dp),
                                ) {
                                    Text(
                                        text = "${snap.stage.titleBn} · ${snap.stage.titleEn}",
                                        color = LearningPal.Cyan,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                Text(
                                    text = "${snap.overallPercent}% বোঝার ক্ষমতা",
                                    color = LearningPal.TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.testTag("learning_overall_percent_text"),
                                )
                                Text(
                                    text = snap.stage.subtitleBn,
                                    color = LearningPal.TextSub,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }

                        EmoGradientProgressBar(
                            progress = snap.overallProgress,
                            trackColor = LearningPal.CardAlt,
                            gradientColors = listOf(LearningPal.Cyan, LearningPal.Mint),
                            heightDp = 14,
                            testTag = "learning_overall_progress_bar",
                        )

                        // Toggle Switch Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(LearningPal.CardAlt)
                                .clickable { viewModel.setLearningEnabled(!snap.enabled) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = if (snap.enabled) LearningPal.Cyan else LearningPal.TextSub,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "অটো-লার্নিং মোড (Auto Learning)",
                                    color = LearningPal.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "আপনার শব্দ, পরের শব্দের ধারা ও ইমোজি অভ্যাস মনে রাখবে",
                                    color = LearningPal.TextSub,
                                    fontSize = 11.5.sp,
                                )
                            }
                            Switch(
                                checked = snap.enabled,
                                onCheckedChange = { viewModel.setLearningEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF031721),
                                    checkedTrackColor = LearningPal.Cyan,
                                    uncheckedThumbColor = LearningPal.TextSub,
                                    uncheckedTrackColor = LearningPal.Card,
                                ),
                                modifier = Modifier.testTag("switch_learning_mode"),
                            )
                        }
                    }
                }

                // 2. Typing Pattern Understanding Card
                LearningBreakdownCard(
                    icon = Icons.Filled.Keyboard,
                    accent = LearningPal.Cyan,
                    accentSoft = LearningPal.CyanSoft,
                    title = "টাইপিং প্যাটার্ন (Typing Patterns)",
                    subtitle = snap.languageStyleLabel,
                    percent = snap.typingPatternPercent,
                    progress = snap.typingPatternProgress,
                    gradientColors = listOf(Color(0xFF1BA9E6), LearningPal.Cyan),
                    progressTag = "learning_typing_progress_bar",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatMiniChip(
                            label = "শেখা শব্দ",
                            value = "${snap.totalLearnedWords}",
                            sub = "বাংলা ${snap.learnedBnWords} · En ${snap.learnedEnWords}",
                            accent = LearningPal.Cyan,
                            modifier = Modifier.weight(1f),
                        )
                        StatMiniChip(
                            label = "পরের শব্দ (Bigram)",
                            value = "${snap.bigramCount}",
                            sub = "Suggestion গ্রহণ ${snap.suggestionAccepts}",
                            accent = LearningPal.Mint,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (snap.totalLearnedWords == 0 && snap.totalWordCommits == 0) {
                        EmptyHintBox(
                            text = "কিবোর্ডে বাংলা বা ইংরেজি টাইপ করলে এখানে আপনার শব্দভাণ্ডার ও পরের শব্দের প্রগ্রেস দেখা যাবে।",
                        )
                    }
                }

                // 3. Emoji Usage Preferences Card
                LearningBreakdownCard(
                    icon = Icons.Filled.EmojiEmotions,
                    accent = LearningPal.Coral,
                    accentSoft = LearningPal.CoralSoft,
                    title = "ইমোজি পছন্দ (Emoji Preferences)",
                    subtitle = "মুড স্টাইল: ${snap.emojiVibeLabel}",
                    percent = snap.emojiPreferencePercent,
                    progress = snap.emojiPreferenceProgress,
                    gradientColors = listOf(LearningPal.Coral, Color(0xFFFFA36C)),
                    progressTag = "learning_emoji_progress_bar",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatMiniChip(
                            label = "মোট ইমোজি ব্যবহার",
                            value = "${snap.totalEmojiUses}",
                            sub = "ভিন্ন ইমোজি ${snap.uniqueEmojisCount}টি",
                            accent = LearningPal.Coral,
                            modifier = Modifier.weight(1f),
                        )
                        StatMiniChip(
                            label = "মুড ও স্কিন-টোন",
                            value = "${snap.moodKeywordMatches}",
                            sub = "টোন পছন্দ ${snap.skinTonesRemembered}টি",
                            accent = Color(0xFFFFC857),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (snap.topEmojis.isNotEmpty()) {
                        Text(
                            text = "আপনার সবচেয়ে প্রিয় ইমোজিগুলো",
                            color = LearningPal.TextSub,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.testTag("learning_top_emojis_row"),
                        ) {
                            for (item in snap.topEmojis) {
                                Surface(
                                    color = LearningPal.CardAlt,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, LearningPal.Coral.copy(alpha = 0.38f)),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    ) {
                                        Text(text = item.emoji, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "×${item.count}",
                                            color = LearningPal.Coral,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        EmptyHintBox(
                            text = "চ্যাট বা প্র্যাকটিস কিবোর্ডে ইমোজি ব্যবহার করলে EMO আপনার প্রিয় ইমোজিগুলো এখানে গুছিয়ে রাখবে।",
                        )
                    }
                }

                // 4. Live Practice Sandbox Card (Real input updates real stats)
                Surface(
                    color = LearningPal.Card,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, LearningPal.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = LearningPal.Mint,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "লাইভ শেখানো (Teach EMO Now)",
                                color = LearningPal.TextPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "আপনার প্রিয় বাংলা/English শব্দ বা ইমোজি এখানে লিখে পাঠালে EMO তা সাথে সাথে শিখে প্রগ্রেস আপডেট করবে।",
                            color = LearningPal.TextSub,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                        OutlinedTextField(
                            value = sandboxText,
                            onValueChange = { sandboxText = it },
                            placeholder = {
                                Text(
                                    text = "যেমন: আমি বাংলায় লিখি 😍",
                                    color = LearningPal.TextSub.copy(alpha = 0.7f),
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LearningPal.TextPrimary,
                                unfocusedTextColor = LearningPal.TextPrimary,
                                focusedBorderColor = LearningPal.Cyan,
                                unfocusedBorderColor = LearningPal.Border,
                                focusedContainerColor = LearningPal.CardAlt,
                                unfocusedContainerColor = LearningPal.CardAlt,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("learning_sandbox_input"),
                        )
                        Button(
                            onClick = {
                                val trimmed = sandboxText.trim()
                                if (trimmed.isEmpty()) {
                                    Toast.makeText(context, "আগে কোনো শব্দ বা ইমোজি লিখুন", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.teachFromSandbox(trimmed)
                                    sandboxText = ""
                                    Toast.makeText(context, "EMO আপনার প্যাটার্নটি শিখে নিয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LearningPal.Cyan,
                                contentColor = Color(0xFF031721),
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("btn_teach_emo"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EMO-কে শেখান",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // 5. Reset Learning Data
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    border = BorderStroke(1.dp, Color(0xFFFF5A6E).copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5A6E)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("btn_reset_learning"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "লার্নিং ডেটা রিসেট করুন",
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    AnimatedVisibility(visible = showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = LearningPal.Card,
            titleContentColor = LearningPal.TextPrimary,
            textContentColor = LearningPal.TextSub,
            title = { Text("লার্নিং মেমোরি মুছবেন?") },
            text = {
                Text("EMO-র শেখা শব্দ, পরের শব্দের প্যাটার্ন এবং প্রিয় ইমোজির হিসাব শূন্য থেকে শুরু হবে।")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAll()
                        showResetDialog = false
                        Toast.makeText(context, "EMO লার্নিং ডেটা রিসেট হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("btn_confirm_reset_learning"),
                ) {
                    Text("মুছে ফেলুন", color = Color(0xFFFF5A6E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("বাতিল", color = LearningPal.TextSub)
                }
            },
        )
    }
}

@Composable
private fun LearningBreakdownCard(
    icon: ImageVector,
    accent: Color,
    accentSoft: Color,
    title: String,
    subtitle: String,
    percent: Int,
    progress: Float,
    gradientColors: List<Color>,
    progressTag: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = LearningPal.Card,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, LearningPal.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accentSoft),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = LearningPal.TextPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        color = LearningPal.TextSub,
                        fontSize = 12.sp,
                    )
                }
                Surface(
                    color = accentSoft,
                    shape = RoundedCornerShape(99.dp),
                ) {
                    Text(
                        text = "$percent%",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            EmoGradientProgressBar(
                progress = progress,
                trackColor = LearningPal.CardAlt,
                gradientColors = gradientColors,
                heightDp = 10,
                testTag = progressTag,
            )

            content()
        }
    }
}

@Composable
fun EmoGradientProgressBar(
    progress: Float,
    trackColor: Color,
    gradientColors: List<Color>,
    heightDp: Int,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "emo_learning_progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(CircleShape)
            .background(trackColor)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = clamped,
                    range = 0f..1f,
                )
            }
            .testTag(testTag),
    ) {
        if (animated > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(gradientColors)),
            )
        }
    }
}

@Composable
private fun StatMiniChip(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = LearningPal.CardAlt,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LearningPal.Border),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = LearningPal.TextSub,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = sub,
                color = LearningPal.TextSub,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyHintBox(text: String) {
    Surface(
        color = LearningPal.CardAlt,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LearningPal.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            color = LearningPal.TextSub,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private class LearningVisorFaceView(
    context: android.content.Context,
) : android.view.View(context) {
    var currentFace: FaceId = FaceId.CURIOUS
    private val platePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF060A10.toInt()
    }
    private val outlinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x5536D1FF
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = (1.5f * context.resources.displayMetrics.density).coerceAtLeast(1.5f)
    }
    private val eyePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF36D1FF.toInt()
    }

    init {
        isClickable = true
        contentDescription = "EMO Learning Visor"
        setOnClickListener {
            EmoBrainHub.handleEvent(RobotEvent.Tap)
            invalidate()
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        FaceRenderer.draw(
            canvas = canvas,
            face = currentFace,
            cx = w / 2f,
            cy = h / 2f,
            plateW = w * 0.94f,
            plateH = h * 0.90f,
            eyeGap = w * 0.40f,
            eyeSize = h * 0.34f,
            blink = 0f,
            eyePaint = eyePaint,
            platePaint = platePaint,
            plateOutline = outlinePaint,
            glow = true,
        )
    }
}
