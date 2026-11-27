package com.banglakeys.app

import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banglakeys.app.robot.FaceId
import com.banglakeys.app.robot.FaceRenderer
import com.example.R
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * অ্যাপ চালুর সময় EMO রোবট ওয়েলকাম/বুট স্ক্রিন।
 * ১.৪৫ সেকেন্ডের মধ্যে মসৃণভাবে হোম স্ক্রিনে নিয়ে যায়, অথবা ইউজার চাইলে
 * সাথে সাথে "শুরু করুন" বাটনে ট্যাপ করে ঢুকতে পারেন।
 */
@Composable
fun EmoAppOpenScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var currentFace by remember { mutableStateOf(FaceId.REST) }
    var blink by remember { mutableFloatStateOf(0f) }
    var gazeX by remember { mutableFloatStateOf(0f) }
    var statusCaption by remember { mutableStateOf("EMO জেগে উঠছে…") }

    val visorAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "open_visor_alpha",
    )

    LaunchedEffect(Unit) {
        val totalMs = 1450L
        val stepMs = 32L
        var elapsed = 0L
        while (elapsed < totalMs) {
            progress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
            when {
                elapsed < 350L -> {
                    currentFace = FaceId.REST
                    blink = 0f
                    gazeX = 0f
                    statusCaption = "EMO জেগে উঠছে…"
                }
                elapsed < 860L -> {
                    currentFace = FaceId.CURIOUS
                    val phase = (elapsed - 350L) / 510f
                    gazeX = sin(phase * Math.PI * 2f).toFloat() * 6f
                    blink = if (elapsed in 620L..700L) 0.85f else 0f
                    statusCaption = "ভয়েস ও কিবোর্ড প্রস্তুত হচ্ছে…"
                }
                else -> {
                    currentFace = FaceId.JOY
                    gazeX = 0f
                    blink = 0f
                    statusCaption = "স্বাগতম! EMO প্রস্তুত"
                }
            }
            delay(stepMs)
            elapsed += stepMs
        }
        progress = 1f
        onFinished()
    }

    val cyan = Color(0xFF36D1FF)
    val bgDeep = Color(0xFF080C12)
    val plateColor = Color(0xFF0D141F)
    val eyePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF36D1FF.toInt() } }
    val platePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plateColor.toArgb() } }
    val outlinePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1F384D.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0E2538), bgDeep),
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("emo_open_screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                color = Color(0xFF0E1A28),
                shape = CircleShape,
                border = BorderStroke(1.dp, cyan.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.emo_keys_icon),
                        contentDescription = stringResource(R.string.cd_app_icon),
                        modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Text(
                        text = "EMO ROBOT SYSTEM",
                        color = cyan,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                    )
                }
            }

            // Live HD EMO Visor Animation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pw = (size.width * 0.82f).coerceAtMost(620f)
                    val ph = (size.height * 0.82f).coerceAtMost(360f)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val pulse = 1f + sin(progress * Math.PI * 3f).toFloat() * 0.02f
                    eyePaint.alpha = (visorAlpha * 255).toInt().coerceIn(0, 255)
                    drawIntoCanvas { canvas ->
                        FaceRenderer.draw(
                            canvas = canvas.nativeCanvas,
                            face = currentFace,
                            cx = cx,
                            cy = cy,
                            plateW = pw,
                            plateH = ph,
                            eyeGap = pw * 0.38f,
                            eyeSize = ph * 0.24f,
                            blink = blink,
                            eyePaint = eyePaint,
                            platePaint = platePaint,
                            plateOutline = outlinePaint,
                            gazeX = gazeX,
                            gazeY = 0f,
                            scaleX = pulse,
                            scaleY = pulse,
                            glow = true,
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.open_screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0CAE0),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier.fillMaxWidth(0.75f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = cyan,
                    trackColor = Color(0xFF152536),
                )
                Text(
                    text = statusCaption,
                    style = MaterialTheme.typography.labelMedium,
                    color = cyan.copy(alpha = 0.88f),
                    modifier = Modifier.testTag("open_screen_status_text"),
                )
            }
        }

        OutlinedButton(
            onClick = onFinished,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, cyan.copy(alpha = 0.55f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF0E1D2B),
                contentColor = cyan,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .width(190.dp)
                .minimumInteractiveComponentSize()
                .testTag("open_screen_enter_button"),
        ) {
            Text(
                text = stringResource(R.string.open_screen_enter),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
