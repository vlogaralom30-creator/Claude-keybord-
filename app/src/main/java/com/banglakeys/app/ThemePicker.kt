package com.banglakeys.app

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.banglakeys.app.robot.EmoBrainHub
import com.banglakeys.app.robot.EmoVisualMode
import com.banglakeys.app.robot.HdEyeStyle
import com.banglakeys.app.robot.RobotBodyMove
import com.banglakeys.app.robot.RobotBodySkin
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Settings screen-এর "Keyboard Theme" card।
 * Theme বদলালে [onChanged] ডাকা হয় — তখন নিচের live keyboard preview সঙ্গে সঙ্গে নতুন theme নেয়।
 * Automated theme switcher সময় অথবা ব্যবহার অনুযায়ী যোগ করা থিমগুলোতে ঘোরে।
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePickerCard(refreshKey: Int = 0, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(ThemeStore.selectedId(ctx)) }
    var hasImage by remember { mutableStateOf(ThemeStore.hasCustomImage(ctx)) }
    var keyAlpha by remember { mutableIntStateOf(ThemeStore.imageAlpha(ctx)) }
    var dim by remember { mutableIntStateOf(ThemeStore.imageDim(ctx)) }
    var emoAlpha by remember { mutableIntStateOf(ThemeStore.emoKeyAlpha(ctx)) }
    var emoSkin by remember { mutableStateOf(AssistantPrefs.emoBodySkin(ctx)) }
    var emoVisualMode by remember { mutableStateOf(AssistantPrefs.emoVisualMode(ctx)) }

    var autoMode by remember { mutableStateOf(ThemeAutoSwitcher.getMode(ctx)) }
    var cadence by remember { mutableStateOf(ThemeAutoSwitcher.getCadence(ctx)) }
    var poolIds by remember { mutableStateOf(ThemeAutoSwitcher.getPoolIds(ctx)) }
    var usageProgress by remember { mutableIntStateOf(ThemeAutoSwitcher.usageProgress(ctx)) }
    var statusText by remember { mutableStateOf(ThemeAutoSwitcher.statusSummary(ctx)) }
    var showPoolPicker by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        selected = ThemeStore.selectedId(ctx)
        hasImage = ThemeStore.hasCustomImage(ctx)
        emoAlpha = ThemeStore.emoKeyAlpha(ctx)
        emoSkin = AssistantPrefs.emoBodySkin(ctx)
        emoVisualMode = AssistantPrefs.emoVisualMode(ctx)
        autoMode = ThemeAutoSwitcher.getMode(ctx)
        cadence = ThemeAutoSwitcher.getCadence(ctx)
        poolIds = ThemeAutoSwitcher.getPoolIds(ctx)
        usageProgress = ThemeAutoSwitcher.usageProgress(ctx)
        statusText = ThemeAutoSwitcher.statusSummary(ctx)
    }

    val refreshAutoStatus = {
        autoMode = ThemeAutoSwitcher.getMode(ctx)
        cadence = ThemeAutoSwitcher.getCadence(ctx)
        poolIds = ThemeAutoSwitcher.getPoolIds(ctx)
        usageProgress = ThemeAutoSwitcher.usageProgress(ctx)
        statusText = ThemeAutoSwitcher.statusSummary(ctx)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (ThemeStore.saveCustomImage(ctx, uri)) {
                hasImage = true
                ThemeStore.setSelectedId(ctx, Themes.CUSTOM_ID)
                selected = Themes.CUSTOM_ID
                if (autoMode != ThemeAutoSwitcher.Mode.OFF) {
                    ThemeAutoSwitcher.setMode(ctx, ThemeAutoSwitcher.Mode.OFF)
                }
                refreshAutoStatus()
                onChanged()
            } else {
                Toast.makeText(ctx, "ছবিটি লোড করা যায়নি", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val launchPhotoPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val resolvedNow = remember(refreshKey, selected, autoMode, statusText) { ThemeStore.resolve(ctx) }
    val eligibleThemes = remember(hasImage) { ThemeAutoSwitcher.eligibleThemes(ctx) }
    val mostUsed = remember(refreshKey, statusText) { ThemeAutoSwitcher.mostUsedTheme(ctx) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("theme_picker_card"),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🎨 Keyboard Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (autoMode != ThemeAutoSwitcher.Mode.OFF) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("auto_theme_active_badge"),
                    ) {
                        Text(
                            text = "${resolvedNow.emoji} ${resolvedNow.name}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Text(
                text = "একটি theme বেছে নিন — নিচের keyboard preview-তে সঙ্গে সঙ্গে দেখা যাবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Themes.all.forEach { t ->
                    val isSelected = if (autoMode != ThemeAutoSwitcher.Mode.OFF) {
                        resolvedNow.id == t.id
                    } else {
                        selected == t.id
                    }
                    ThemeChip(
                        theme = t,
                        selected = isSelected,
                        onClick = {
                            if (t.usesImage && !hasImage) {
                                launchPhotoPicker()
                            } else {
                                if (autoMode != ThemeAutoSwitcher.Mode.OFF) {
                                    ThemeAutoSwitcher.setMode(ctx, ThemeAutoSwitcher.Mode.OFF)
                                    Toast.makeText(
                                        ctx,
                                        "অটো থিম পরিবর্তন বন্ধ — ম্যানুয়াল থিম চালু",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                ThemeStore.setSelectedId(ctx, t.id)
                                selected = t.id
                                refreshAutoStatus()
                                onChanged()
                            }
                        },
                    )
                }
            }

            if (selected == Themes.CUSTOM_ID && autoMode == ThemeAutoSwitcher.Mode.OFF) {
                if (!hasImage) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("theme_custom_photo_guide"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "গ্যালারি থেকে পছন্দের ছবি বাছলে কিবোর্ড ব্যাকগ্রাউন্ড হিসেবে সেট হবে।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            FilledTonalButton(
                                onClick = { launchPhotoPicker() },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) { Text("ছবি বাছুন") }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { launchPhotoPicker() },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) { Text("ছবি বদলান") }
                        OutlinedButton(
                            onClick = {
                                ThemeStore.clearCustomImage(ctx)
                                hasImage = false
                                ThemeStore.setSelectedId(ctx, Themes.AUTO_ID)
                                selected = Themes.AUTO_ID
                                refreshAutoStatus()
                                onChanged()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) { Text("মুছুন") }
                    }
                    Text(
                        text = "Key স্বচ্ছতা: $keyAlpha%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Slider(
                        value = keyAlpha.toFloat(),
                        onValueChange = {
                            keyAlpha = it.roundToInt().coerceIn(ThemeStore.MIN_ALPHA, ThemeStore.MAX_ALPHA)
                            ThemeStore.setImageAlpha(ctx, keyAlpha)
                            onChanged()
                        },
                        valueRange = ThemeStore.MIN_ALPHA.toFloat()..ThemeStore.MAX_ALPHA.toFloat(),
                        modifier = Modifier.fillMaxWidth().testTag("theme_alpha_slider"),
                    )
                    Text(
                        text = "ছবির উপর অন্ধকার: $dim%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Slider(
                        value = dim.toFloat(),
                        onValueChange = {
                            dim = it.roundToInt().coerceIn(0, 70)
                            ThemeStore.setImageDim(ctx, dim)
                            onChanged()
                        },
                        valueRange = 0f..70f,
                        modifier = Modifier.fillMaxWidth().testTag("theme_dim_slider"),
                    )
                }
            }

            if (resolvedNow.id == Themes.EMO_ID) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("emo_theme_controls"),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "🤖 EMO ফুল-বডি ও গ্লাস কী কন্ট্রোল (লাইভ কিবোর্ড সিঙ্ক)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RobotBodySkin.entries.forEach { skin ->
                                val tag = if (skin == RobotBodySkin.LAVENDER_3D) {
                                    "emo_theme_skin_lavender"
                                } else {
                                    "emo_theme_skin_cyan"
                                }
                                FilterChip(
                                    selected = emoSkin == skin,
                                    onClick = {
                                        emoSkin = skin
                                        AssistantPrefs.setEmoBodySkin(ctx, skin)
                                        EmoBrainHub.triggerTapHop()
                                        onChanged()
                                    },
                                    label = { Text("${skin.badge} ${skin.titleEn}") },
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag(tag),
                                )
                            }
                        }

                        Text(
                            text = "🎭 পরিস্থিতি অনুযায়ী অভিনয় ও চোখ:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            EmoVisualMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = emoVisualMode == mode,
                                    onClick = {
                                        emoVisualMode = mode
                                        AssistantPrefs.setEmoVisualMode(ctx, mode)
                                        EmoBrainHub.clearPlayOverrides()
                                        EmoBrainHub.triggerTapHop()
                                        onChanged()
                                    },
                                    label = { Text("${mode.badge} ${mode.titleBn}") },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("emo_visual_mode_${mode.name.lowercase()}"),
                                )
                            }
                        }

                        Text(
                            text = "🕺 কিবোর্ডে বডি মুভ ট্রাই করুন:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    EmoBrainHub.clearPlayOverrides()
                                    EmoBrainHub.triggerTapHop()
                                },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("emo_theme_move_auto"),
                            ) { Text("🤖 Auto") }
                            listOf(
                                RobotBodyMove.JUMP_WAVE,
                                RobotBodyMove.HEAD_TILT,
                                RobotBodyMove.BELLY_FLOP,
                                RobotBodyMove.WALK_SHUFFLE,
                                RobotBodyMove.DIZZY_WOBBLE,
                            ).forEach { move ->
                                OutlinedButton(
                                    onClick = { EmoBrainHub.playBodyMove(move) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("emo_theme_move_${move.name.lowercase()}"),
                                ) {
                                    Text("${move.badge} ${move.titleEn}")
                                }
                            }
                        }

                        Text(
                            text = "👀 ৮টি HD চোখ কিবোর্ডে দেখুন:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            HdEyeStyle.entries.forEach { style ->
                                OutlinedButton(
                                    onClick = { EmoBrainHub.playHdEye(style) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("emo_theme_eye_${style.name.lowercase()}"),
                                ) {
                                    Text("${style.badge} ${style.titleEn}")
                                }
                            }
                        }

                        Text(
                            text = "🤖 EMO গ্লাস কী স্বচ্ছতা: $emoAlpha%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Slider(
                            value = emoAlpha.toFloat(),
                            onValueChange = {
                                emoAlpha = it.roundToInt().coerceIn(ThemeStore.MIN_EMO_ALPHA, ThemeStore.MAX_EMO_ALPHA)
                                ThemeStore.setEmoKeyAlpha(ctx, emoAlpha)
                                onChanged()
                            },
                            valueRange = ThemeStore.MIN_EMO_ALPHA.toFloat()..ThemeStore.MAX_EMO_ALPHA.toFloat(),
                            modifier = Modifier.fillMaxWidth().testTag("emo_alpha_slider"),
                        )
                        OutlinedButton(
                            onClick = {
                                ctx.startActivity(
                                    Intent(ctx, AssistantActivity::class.java)
                                        .putExtra(AssistantActivity.EXTRA_SCROLL_TO_FACES, true)
                                )
                            },
                            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("emo_open_all_faces_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("সব ৩২টি EMO ফেস দেখুন")
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "অটো থিম সুইচার (Auto Rotate)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("auto_theme_status"),
                        )
                    }
                }
                Switch(
                    checked = autoMode != ThemeAutoSwitcher.Mode.OFF,
                    onCheckedChange = { enabled ->
                        val nextMode = if (enabled) ThemeAutoSwitcher.Mode.TIME_OF_DAY else ThemeAutoSwitcher.Mode.OFF
                        ThemeAutoSwitcher.setMode(ctx, nextMode)
                        refreshAutoStatus()
                        onChanged()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("auto_theme_toggle"),
                )
            }

            AnimatedVisibility(visible = autoMode != ThemeAutoSwitcher.Mode.OFF) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = autoMode == ThemeAutoSwitcher.Mode.TIME_OF_DAY,
                            onClick = {
                                ThemeAutoSwitcher.setMode(ctx, ThemeAutoSwitcher.Mode.TIME_OF_DAY)
                                refreshAutoStatus()
                                onChanged()
                            },
                            label = { Text("সময় অনুযায়ী (Time)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("auto_mode_time"),
                        )
                        FilterChip(
                            selected = autoMode == ThemeAutoSwitcher.Mode.USAGE_FREQUENCY,
                            onClick = {
                                ThemeAutoSwitcher.setMode(ctx, ThemeAutoSwitcher.Mode.USAGE_FREQUENCY)
                                refreshAutoStatus()
                                onChanged()
                            },
                            label = { Text("ব্যবহার অনুযায়ী (Usage)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Insights,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("auto_mode_usage"),
                        )
                    }

                    if (poolIds.size < 2) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("auto_theme_pool_warning"),
                        ) {
                            Text(
                                text = "অটো রোটেশন কাজ করতে নিচের 'রোটেশন পুল' থেকে কমপক্ষে ২টি থিম সিলেক্ট করুন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }

                    if (autoMode == ThemeAutoSwitcher.Mode.TIME_OF_DAY) {
                        val activeSlot = remember(statusText) {
                            ThemeAutoSwitcher.TimeSlot.forHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "দিনের সময়ভেদে থিম পরিবর্তন (সকাল ও দুপুরে উজ্জ্বল, সন্ধ্যা ও রাতে ডার্ক):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    ThemeAutoSwitcher.TimeSlot.entries.forEach { slot ->
                                        val current = slot == activeSlot
                                        Surface(
                                            color = if (current) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text(
                                                text = "${slot.labelBn.substringBefore(" ")} (${slot.rangeLabel})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                                color = if (current) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (autoMode == ThemeAutoSwitcher.Mode.USAGE_FREQUENCY) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ThemeAutoSwitcher.UsageCadence.entries.forEach { item ->
                                FilterChip(
                                    selected = cadence == item,
                                    onClick = {
                                        ThemeAutoSwitcher.setCadence(ctx, item)
                                        refreshAutoStatus()
                                        onChanged()
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("auto_cadence_${item.id}"),
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val unitLabel = if (cadence.metric == ThemeAutoSwitcher.UsageMetric.SESSIONS) "বার খোলা" else "কী"
                                val pct = (usageProgress.toFloat() / cadence.threshold.coerceAtLeast(1)).coerceIn(0f, 1f)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "পরবর্তী থিমে অগ্রগতি",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "$usageProgress / ${cadence.threshold} $unitLabel",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (mostUsed != null) {
                                "সবচেয়ে বেশি ব্যবহৃত: ${mostUsed.first.emoji} ${mostUsed.first.name} (${mostUsed.second})"
                            } else {
                                "পুলে থিম: ${poolIds.size}টি"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        FilledTonalButton(
                            onClick = {
                                ThemeAutoSwitcher.rotateNow(ctx)
                                refreshAutoStatus()
                                onChanged()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("auto_theme_rotate_now"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পরের থিম")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { showPoolPicker = !showPoolPicker },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("auto_pool_toggle"),
                        ) {
                            Text("রোটেশন পুল (${poolIds.size}/${eligibleThemes.size}টি থিম)")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (showPoolPicker) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (poolIds.size < eligibleThemes.size) {
                            TextButton(
                                onClick = {
                                    ThemeAutoSwitcher.setPoolIds(ctx, eligibleThemes.map { it.id })
                                    refreshAutoStatus()
                                    onChanged()
                                },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("auto_pool_select_all"),
                            ) {
                                Text("সব নির্বাচন")
                            }
                        }
                    }

                    AnimatedVisibility(visible = showPoolPicker) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            eligibleThemes.forEach { t ->
                                val included = t.id in poolIds
                                FilterChip(
                                    selected = included,
                                    onClick = {
                                        ThemeAutoSwitcher.toggleThemeInPool(ctx, t.id)
                                        refreshAutoStatus()
                                        onChanged()
                                    },
                                    label = { Text("${t.emoji} ${t.name}", maxLines = 1) },
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("auto_pool_chip_${t.id}"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeChip(theme: KeyboardTheme, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(if (selected) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThemePreview(theme)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${theme.emoji} ${theme.name}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Theme-এর ছোট mini keyboard — ৩ সারি key, শেষ key-টা accent */
@Composable
private fun ThemePreview(t: KeyboardTheme) {
    val ctx = LocalContext.current
    val bgImg = remember(t.id) { t.bgAsset?.let { ThemeStore.assetBitmap(it)?.asImageBitmap() } }
    val tileImgs = remember(t.id) { t.keyTiles.mapNotNull { ThemeStore.assetBitmap(it)?.asImageBitmap() } }
    val emoSkin = if (t.id == Themes.EMO_ID) AssistantPrefs.emoBodySkin(ctx) else RobotBodySkin.CLASSIC_CYAN
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        // background
        if (bgImg != null) {
            drawImage(bgImg, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        } else if (t.usesImage) {
            drawRect(Brush.linearGradient(listOf(Color(0xFF5B86E5), Color(0xFF36D1DC), Color(0xFFF7797D))))
        } else if (t.id == Themes.EMO_ID && emoSkin == RobotBodySkin.LAVENDER_3D) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF171029), Color(0xFF07050D))))
        } else if (t.bgTop != 0 && t.bgBottom != 0) {
            drawRect(Brush.verticalGradient(listOf(Color(t.bgTop), Color(t.bgBottom))))
        } else {
            drawRect(Color(t.bg))
        }

        if (t.id == Themes.EMO_ID) {
            val isLavender = emoSkin == RobotBodySkin.LAVENDER_3D
            val glow = if (isLavender) Color(0xFFC4A1FF) else Color(t.accent)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.36f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.width * 0.48f,
                ),
                radius = size.width * 0.48f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
            // Mini chubby full-body silhouette behind mini keys
            val bw = size.width * 0.44f
            val bh = size.height * 0.64f
            val bx = (size.width - bw) / 2f
            val by = size.height * 0.24f
            drawCircle(
                color = Color(emoSkin.bodyCore),
                radius = 4.dp.toPx(),
                center = Offset(bx + bw * 0.28f, by - 4.dp.toPx()),
            )
            drawRoundRect(
                color = Color(emoSkin.bodyCore),
                topLeft = Offset(bx, by),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
            )
            val vw = bw * 0.72f
            val vh = bh * 0.50f
            val vx = (size.width - vw) / 2f
            val vy = by + bh * 0.22f
            drawRoundRect(
                color = Color(emoSkin.visorTop),
                topLeft = Offset(vx, vy),
                size = Size(vw, vh),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            )
            val eyeColor = Color(emoSkin.eyeAccent)
            drawCircle(eyeColor, radius = 2.6.dp.toPx(), center = Offset(vx + vw * 0.32f, vy + vh * 0.50f))
            drawCircle(eyeColor, radius = 2.6.dp.toPx(), center = Offset(vx + vw * 0.68f, vy + vh * 0.50f))
        }

        val rowsCount = intArrayOf(5, 5, 4)
        val pad = 5.dp.toPx()
        val gap = 3.dp.toPx()
        val rowH = (size.height - pad * 2 - gap * 2) / 3f
        val radius = (t.radiusDp.coerceAtMost(6f)).dp.toPx()

        for (r in rowsCount.indices) {
            val n = rowsCount[r]
            val kw = (size.width - pad * 2 - gap * (n - 1)) / n
            for (i in 0 until n) {
                val x = pad + i * (kw + gap)
                val y = pad + r * (rowH + gap)
                val isAccent = r == 2 && i == n - 1
                val isFn = r == 2 && i == 0
                val fillInt = when {
                    isAccent -> t.accent
                    isFn -> t.fn
                    else -> t.key
                }
                val topLeft = Offset(x, y)
                val sz = Size(kw, rowH)
                val cr = CornerRadius(radius, radius)

                if (t.shadowDp > 0f && t.style != KeyStyle.GLASS && t.style != KeyStyle.NEON) {
                    drawRoundRect(Color(t.shadow), Offset(x, y + 1.2.dp.toPx()), sz, cr)
                }
                drawRoundRect(Color(fillInt), topLeft, sz, cr)

                if (t.style == KeyStyle.TILE && !isAccent && !isFn && tileImgs.isNotEmpty()) {
                    val path = Path().apply { addRoundRect(RoundRect(x, y, x + kw, y + rowH, cr)) }
                    clipPath(path) {
                        drawImage(
                            tileImgs[(r * 5 + i) % tileImgs.size],
                            dstOffset = IntOffset(x.toInt(), y.toInt()),
                            dstSize = IntSize(kw.toInt().coerceAtLeast(1), rowH.toInt().coerceAtLeast(1)),
                        )
                    }
                    drawRect(Color(t.tileScrim), topLeft, sz)
                }
                if (t.style == KeyStyle.BLOCK && !isAccent && !isFn) {
                    drawRect(Color(t.keyTop), topLeft, Size(kw, rowH * 0.3f))
                }
                if (t.style == KeyStyle.PIXEL) {
                    drawRoundRect(Color(t.border), topLeft, sz, cr, style = Stroke(1.6.dp.toPx()))
                } else if (t.style == KeyStyle.NEON) {
                    drawRoundRect(Color(if (isAccent) t.accent else t.border), topLeft, sz, cr, style = Stroke(1.2.dp.toPx()))
                } else if (t.borderDp > 0f && t.border != 0) {
                    drawRoundRect(Color(t.border), topLeft, sz, cr, style = Stroke(1.dp.toPx()))
                }
            }
        }
    }
}
