package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AudioTrack
import com.example.ui.components.tvFocusable
import com.example.ui.theme.LocalTvStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BackgroundPlayScreen(
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    backgroundPlaybackEnabled: Boolean,
    keepCpuAwake: Boolean,
    antiHiss24BitEnabled: Boolean,
    sleepTimerSeconds: Int?,
    onToggleBackgroundPlayback: (Boolean) -> Unit,
    onToggleKeepCpuAwake: (Boolean) -> Unit,
    onToggleAntiHiss24Bit: (Boolean) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onMinimizeToBackground: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    var showDarkAmbientMode by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("background_play_screen")
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phát Nhạc Chạy Nền TV",
                        color = style.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tiếp tục phát nhạc khi thoát về màn hình chính TV, chuyển ứng dụng hoặc tắt sáng màn hình",
                        color = style.textSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (backgroundPlaybackEnabled) style.emeraldBg else style.surfaceVariant)
                        .border(
                            1.dp,
                            if (backgroundPlaybackEnabled) style.emeraldBorder else style.borderColor,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (backgroundPlaybackEnabled) style.emeraldColor else style.textMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (backgroundPlaybackEnabled) "Chạy nền: BẬT" else "Chạy nền: TẮT",
                        color = if (backgroundPlaybackEnabled) style.emeraldColor else style.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Quick Mini Controller + Launch Actions
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(style.cardSurface)
                    .border(1.dp, style.borderColor, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPlaying) "ĐANG PHÁT TRONG NỀN" else "TRÌNH PHÁT ĐÃ SẴN SÀNG",
                            color = style.accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentTrack?.title ?: "Chưa chọn bài hát",
                            color = style.textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (currentTrack != null) {
                                "${currentTrack.artist} • ${currentTrack.format} ${currentTrack.sampleRate}"
                            } else {
                                "Bấm Phát hoặc chọn bài trong Thư mục / Tất cả bài hát"
                            },
                            color = style.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .tvFocusable(
                                    tag = "bg_btn_prev",
                                    shape = CircleShape,
                                    onClick = onSkipPrevious
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Bài trước",
                                tint = style.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .tvFocusable(
                                    tag = "bg_btn_play_pause",
                                    shape = CircleShape,
                                    defaultBgColor = style.accentColor,
                                    focusedBgColor = style.accentColor.copy(alpha = 0.85f),
                                    defaultBorderColor = style.accentColor,
                                    onClick = onPlayPause
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                                tint = style.accentContentColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .tvFocusable(
                                    tag = "bg_btn_next",
                                    shape = CircleShape,
                                    onClick = onSkipNext
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Bài tiếp theo",
                                tint = style.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusable(
                                tag = "bg_btn_minimize_home",
                                shape = RoundedCornerShape(12.dp),
                                defaultBgColor = style.accentColor,
                                focusedBgColor = style.accentColor.copy(alpha = 0.85f),
                                defaultBorderColor = style.accentColor,
                                onClick = {
                                    if (!backgroundPlaybackEnabled) {
                                        onToggleBackgroundPlayback(true)
                                    }
                                    onMinimizeToBackground()
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Thu nhỏ về Home TV",
                            tint = style.accentContentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thu nhỏ app & Nghe chạy nền",
                            color = style.accentContentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusable(
                                tag = "bg_btn_dark_screen",
                                shape = RoundedCornerShape(12.dp),
                                defaultBgColor = style.surfaceVariant,
                                defaultBorderColor = style.borderColor,
                                onClick = { showDarkAmbientMode = true }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Tắt sáng màn hình TV",
                            tint = style.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Màn hình tối nghe nhạc (Chống chói)",
                            color = style.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Background Service Options
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(style.cardSurface)
                    .border(1.dp, style.borderColor, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cấu hình chạy nền & âm thanh sạch",
                    color = style.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                BackgroundToggleRow(
                    title = "Cho phép phát nhạc chạy nền (MediaSessionService)",
                    subtitle = "Nhạc tiếp tục phát khi nhấn Home trên remote TV hoặc chuyển sang ứng dụng khác",
                    checked = backgroundPlaybackEnabled,
                    tag = "bg_toggle_service",
                    onToggle = onToggleBackgroundPlayback
                )

                BackgroundToggleRow(
                    title = "Giữ CPU thức khi chạy nền (WakeLock)",
                    subtitle = "Tránh giật tiếng hoặc ngắt Wi-Fi khi màn hình TV giảm độ sáng",
                    checked = keepCpuAwake,
                    tag = "bg_toggle_wakelock",
                    onToggle = onToggleKeepCpuAwake
                )

                BackgroundToggleRow(
                    title = "Chống sôi nhạc 24-bit / 48kHz (PCM 16-bit sạch)",
                    subtitle = "Chuẩn hóa luồng PCM 24-bit/Float sang PCM 16-bit sạch, loại bỏ rè/sôi trên DAC TV",
                    checked = antiHiss24BitEnabled,
                    tag = "bg_toggle_anti_hiss",
                    onToggle = onToggleAntiHiss24Bit
                )
            }
        }

        // Sleep Timer Quick Row
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(style.cardSurface)
                    .border(1.dp, style.borderColor, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = style.amberColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hẹn giờ tắt khi nghe nền",
                            color = style.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (sleepTimerSeconds != null && sleepTimerSeconds > 0) {
                        Text(
                            text = "Còn %02d:%02d".format(sleepTimerSeconds / 60, sleepTimerSeconds % 60),
                            color = style.amberColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val presets = listOf(null to "Tắt hẹn", 15 to "15 phút", 30 to "30 phút", 45 to "45 phút", 60 to "60 phút")
                    presets.forEach { (minutes, label) ->
                        val isActive = if (minutes == null) {
                            sleepTimerSeconds == null
                        } else {
                            sleepTimerSeconds != null && ((sleepTimerSeconds + 59) / 60) in (minutes - 14)..minutes
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .tvFocusable(
                                    tag = "bg_timer_${minutes ?: 0}",
                                    shape = RoundedCornerShape(10.dp),
                                    defaultBgColor = if (isActive) style.selectedPillSurface else style.surfaceVariant,
                                    defaultBorderColor = if (isActive) style.accentColor else style.borderColor,
                                    onClick = { onSetSleepTimer(minutes) }
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isActive) style.accentColor else style.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDarkAmbientMode) {
        DarkScreenAmbientOverlay(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            onDismiss = { showDarkAmbientMode = false }
        )
    }
}

@Composable
private fun BackgroundToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onToggle: (Boolean) -> Unit
) {
    val style = LocalTvStyle.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(
                tag = tag,
                shape = RoundedCornerShape(12.dp),
                defaultBgColor = if (checked) style.selectedPillSurface.copy(alpha = 0.6f) else style.surfaceVariant,
                defaultBorderColor = if (checked) style.accentLightBorder else style.borderColor,
                onClick = { onToggle(!checked) }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.NightsStay,
                    contentDescription = null,
                    tint = if (checked) style.accentColor else style.textMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = style.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = style.textSecondary,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = style.accentColor
            )
        )
    }
}

@Composable
private fun DarkScreenAmbientOverlay(
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onDismiss: () -> Unit
) {
    var timeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            timeText = fmt.format(Date())
            delay(5_000L)
        }
    }

    val drift = rememberInfiniteTransition(label = "burn_in_drift")
    val offsetX by drift.animateFloat(
        initialValue = -28f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift_x"
    )
    val offsetY by drift.animateFloat(
        initialValue = -16f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift_y"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusable()
                .onKeyEvent {
                    onDismiss()
                    true
                }
                .tvFocusable(
                    tag = "bg_ambient_dismiss_area",
                    shape = RoundedCornerShape(0.dp),
                    scaleUpFactor = 1f,
                    defaultBgColor = Color.Black,
                    focusedBgColor = Color.Black,
                    defaultBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    elevation = 0.dp,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = timeText,
                    color = Color(0xFF475569),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Light
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8).copy(alpha = if (isPlaying) 0.7f else 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentTrack?.let { "${it.title} — ${it.artist}" } ?: "Đang phát nhạc nền",
                        color = Color(0xFF64748B),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Bấm phím bất kỳ hoặc chạm màn hình để bật sáng lại",
                    color = Color(0xFF334155),
                    fontSize = 12.sp
                )
            }
        }
    }
}
