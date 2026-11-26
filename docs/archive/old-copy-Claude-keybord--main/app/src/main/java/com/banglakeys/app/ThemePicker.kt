package com.banglakeys.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Settings screen-এর "Keyboard Theme" card।
 * Theme বদলালে [onChanged] ডাকা হয় — তখন নিচের live keyboard preview সঙ্গে সঙ্গে নতুন theme নেয়।
 * আসল keyboard পরের বার খুললেই নতুন theme নেয়।
 */
@Composable
fun ThemePickerCard(onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(ThemeStore.selectedId(ctx)) }
    var hasImage by remember { mutableStateOf(ThemeStore.hasCustomImage(ctx)) }
    var keyAlpha by remember { mutableIntStateOf(ThemeStore.imageAlpha(ctx)) }
    var dim by remember { mutableIntStateOf(ThemeStore.imageDim(ctx)) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (ThemeStore.saveCustomImage(ctx, uri)) {
                hasImage = true
                ThemeStore.setSelectedId(ctx, Themes.CUSTOM_ID)
                selected = Themes.CUSTOM_ID
                onChanged()
            } else {
                Toast.makeText(ctx, "ছবিটি লোড করা যায়নি", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("theme_picker_card"),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "🎨 Keyboard Theme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
                    ThemeChip(
                        theme = t,
                        selected = selected == t.id,
                        onClick = {
                            if (t.usesImage && !hasImage) {
                                picker.launch("image/*")
                            } else {
                                ThemeStore.setSelectedId(ctx, t.id)
                                selected = t.id
                                onChanged()
                            }
                        },
                    )
                }
            }

            if (selected == Themes.CUSTOM_ID) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { picker.launch("image/*") }) { Text("ছবি বদলান") }
                    OutlinedButton(
                        onClick = {
                            ThemeStore.clearCustomImage(ctx)
                            hasImage = false
                            ThemeStore.setSelectedId(ctx, Themes.AUTO_ID)
                            selected = Themes.AUTO_ID
                            onChanged()
                        },
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
    val bgImg = remember(t.id) { t.bgAsset?.let { ThemeStore.assetBitmap(it)?.asImageBitmap() } }
    val tileImgs = remember(t.id) { t.keyTiles.mapNotNull { ThemeStore.assetBitmap(it)?.asImageBitmap() } }
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
        } else if (t.bgTop != 0 && t.bgBottom != 0) {
            drawRect(Brush.verticalGradient(listOf(Color(t.bgTop), Color(t.bgBottom))))
        } else {
            drawRect(Color(t.bg))
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
