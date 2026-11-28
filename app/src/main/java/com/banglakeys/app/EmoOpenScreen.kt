package com.banglakeys.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banglakeys.app.robot.FaceId
import com.banglakeys.app.robot.HdEyeStyle
import com.banglakeys.app.robot.RobotBodyMotionEngine
import com.banglakeys.app.robot.RobotBodyMove
import com.banglakeys.app.robot.RobotBodyRenderer
import com.banglakeys.app.robot.RobotBodySkin
import com.example.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * অ্যাপ চালুর সময় ফুল-বডি কিউট EMO রোবট ওয়েলকাম/বুট স্ক্রিন।
 * ঘুমন্ত বেলি-ফ্লপ থেকে জেগে উঠে -> মাথা কাত করে তাকিয়ে -> খুশিতে লাফ দিয়ে হাত নেড়ে হাই বলে
 * হোম স্ক্রিনে নিয়ে যায়; ইউজার চাইলে Lavender 3D ও Classic Cyan স্কিন সুইচ বা "শুরু করুন" করতে পারেন।
 */
@Composable
fun EmoAppOpenScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var skin by remember { mutableStateOf(AssistantPrefs.emoBodySkin(context)) }
    var progress by remember { mutableFloatStateOf(0f) }
    var eyeStyle by remember { mutableStateOf(HdEyeStyle.CHILL) }
    var activeMove by remember { mutableStateOf(RobotBodyMove.BELLY_FLOP) }
    var blink by remember { mutableFloatStateOf(0.75f) }
    var gazeNormX by remember { mutableFloatStateOf(0f) }
    var gazeNormY by remember { mutableFloatStateOf(0f) }
    var animNowMs by remember { mutableLongStateOf(0L) }
    var tapHopAtMs by remember { mutableLongStateOf(-10_000L) }
    var statusCaption by remember { mutableStateOf("EMO ঘুম ভেঙে উঠছে…") }

    var blendedPose by remember { mutableStateOf(RobotBodyMotionEngine.samplePose(RobotBodyMove.BELLY_FLOP, 0L)) }

    val stageAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "open_robot_alpha",
    )

    LaunchedEffect(Unit) {
        val totalMs = 2050L
        val stepMs = 16L
        var elapsed = 0L
        while (elapsed < totalMs) {
            animNowMs = elapsed
            progress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
            when {
                elapsed < 460L -> {
                    activeMove = RobotBodyMove.BELLY_FLOP
                    eyeStyle = HdEyeStyle.CHILL
                    val wakeT = (elapsed / 460f).coerceIn(0f, 1f)
                    blink = (1f - wakeT) * 0.8f
                    gazeNormX = 0f
                    gazeNormY = 0.08f * (1f - wakeT)
                    statusCaption = "EMO ঘুম ভেঙে উঠছে…"
                }
                elapsed < 1160L -> {
                    activeMove = RobotBodyMove.HEAD_TILT
                    eyeStyle = HdEyeStyle.GLANCE
                    val phase = (elapsed - 460L) / 700f
                    gazeNormX = sin(phase * PI.toFloat() * 2f) * 0.7f
                    gazeNormY = sin(phase * PI.toFloat()) * -0.15f
                    blink = if (elapsed in 790L..870L) 0.85f else 0f
                    statusCaption = "ভয়েস ও কিবোর্ড প্রস্তুত হচ্ছে…"
                }
                elapsed < 1520L -> {
                    activeMove = RobotBodyMove.IDLE
                    eyeStyle = HdEyeStyle.SPARKLE
                    gazeNormX = 0f
                    gazeNormY = -0.06f
                    blink = 0f
                    statusCaption = "সেন্সর ও ব্রেইন যুক্ত হচ্ছে…"
                }
                else -> {
                    activeMove = RobotBodyMove.JUMP_WAVE
                    eyeStyle = HdEyeStyle.PEEK
                    gazeNormX = 0f
                    gazeNormY = 0f
                    blink = 0f
                    statusCaption = "হাই! EMO প্রস্তুত 🙌"
                }
            }
            val targetPose = RobotBodyMotionEngine.samplePose(
                move = activeMove,
                nowMs = elapsed + 1_200L,
                tapHopElapsedMs = if (tapHopAtMs > 0L) (elapsed - tapHopAtMs) else -1L,
            )
            blendedPose = RobotBodyMotionEngine.lerpPose(blendedPose, targetPose, 0.24f)
            delay(stepMs)
            elapsed += stepMs
        }
        progress = 1f
        onFinished()
    }

    val isLavender = skin == RobotBodySkin.LAVENDER_3D
    val accentColor = if (isLavender) Color(0xFFC4A1FF) else Color(0xFF36D1FF)
    val bgTop = if (isLavender) Color(0xFF1F1633) else Color(0xFF0E2538)
    val bgDeep = Color(0xFF080C12)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.radialGradient(colors = listOf(bgTop, bgDeep)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("emo_open_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // 1. Top Brand Pill + Quick Skin Switcher (always clear of hero)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(0xFF121826),
                shape = CircleShape,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.emo_keys_icon),
                        contentDescription = stringResource(R.string.cd_app_icon),
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Text(
                        text = "EMO BUDDY",
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }

            Surface(
                onClick = {
                    val next = skin.toggled()
                    skin = next
                    AssistantPrefs.setEmoBodySkin(context, next)
                    tapHopAtMs = animNowMs
                },
                color = Color(0xFF141B2B),
                shape = CircleShape,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("open_screen_skin_toggle"),
            ) {
                Text(
                    text = "${skin.badge} ${skin.titleEn}",
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        // 2. Center Adaptive Hero Stage
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 140.dp, max = 260.dp)
                    .testTag("open_screen_hd_eyes"),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { tapHopAtMs = animNowMs }
                        .testTag("open_screen_full_body_robot"),
                ) {
                    val face = when (activeMove) {
                        RobotBodyMove.BELLY_FLOP -> FaceId.REST
                        RobotBodyMove.HEAD_TILT -> FaceId.CURIOUS
                        RobotBodyMove.JUMP_WAVE -> FaceId.BEAMING
                        else -> FaceId.NORMAL
                    }
                    drawIntoCanvas { canvas ->
                        RobotBodyRenderer.draw(
                            canvas = canvas.nativeCanvas,
                            cx = size.width / 2f,
                            cy = size.height / 2f,
                            maxW = size.width * 0.92f,
                            maxH = size.height * 0.94f,
                            skin = skin,
                            pose = blendedPose,
                            hdEyesEnabled = true,
                            hdStyle = eyeStyle,
                            face = face,
                            blink = blink,
                            gazeNormX = gazeNormX,
                            gazeNormY = gazeNormY,
                            nowMs = animNowMs + 1_200L,
                            visorBounceX = stageAlpha.coerceAtLeast(0.8f),
                            visorBounceY = stageAlpha.coerceAtLeast(0.8f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    color = Color(0xFFB7C6E0),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(0.75f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = accentColor,
                    trackColor = Color(0xFF1A2334),
                )
                Text(
                    text = statusCaption,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor.copy(alpha = 0.92f),
                    modifier = Modifier.testTag("open_screen_status_text"),
                )
            }
        }

        // 3. Bottom Enter Button
        OutlinedButton(
            onClick = onFinished,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF131B2A),
                contentColor = accentColor,
            ),
            modifier = Modifier
                .padding(bottom = 8.dp)
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
