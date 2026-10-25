package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EqualizerPreset
import com.example.ui.components.tvFocusable
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.AudioPrimary
import com.example.ui.theme.AudioPrimaryLight
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceCard
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    currentEqualizer: EqualizerPreset,
    sleepTimerSeconds: Int?,
    totalTracksCount: Int,
    totalFoldersCount: Int,
    isScanning: Boolean,
    onSetEqualizer: (EqualizerPreset) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onRefreshScan: () -> Unit,
    onPickStorageFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            // Header
            Column {
                Text(
                    text = "Cài Đặt & Định Dạng Âm Thanh",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tối ưu hóa âm thanh cho Android TV và quản lý bộ nhớ thiết bị",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        // Section 1: Audio Formats
        item {
            SettingsCard(
                title = "Định dạng âm thanh hỗ trợ",
                subtitle = "Giải mã trực tiếp phần cứng và phần mềm trên Android TV",
                icon = Icons.Default.Audiotrack,
                iconColor = AudioPrimary
            ) {
                val formatBadges = listOf(
                    Pair("FLAC", "Hi-Res Audio Lossless 24-bit/96kHz"),
                    Pair("WAV", "Uncompressed Studio PCM Master"),
                    Pair("MP3", "Standard MPEG Audio lên đến 320kbps"),
                    Pair("AAC / M4A", "Advanced Audio Coding đa kênh"),
                    Pair("OGG", "OGG Vorbis chất lượng cao"),
                    Pair("OPUS", "Low latency audiophile format")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    formatBadges.forEach { (fmt, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(LightSurfaceVariant)
                                .border(1.dp, LightBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AudioPrimaryLight)
                                    .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = fmt,
                                    color = AudioPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = desc,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Equalizer Preset
        item {
            SettingsCard(
                title = "Bộ cân bằng âm thanh (Equalizer Preset)",
                subtitle = "Hiệu chỉnh dải tần số âm thanh phù hợp với loa TV hoặc dàn loa ngoài",
                icon = Icons.Default.Tune,
                iconColor = AudioPrimary
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        EqualizerPreset.NORMAL,
                        EqualizerPreset.BASS_BOOST,
                        EqualizerPreset.VOCAL,
                        EqualizerPreset.ROCK,
                        EqualizerPreset.POP,
                        EqualizerPreset.CINEMA
                    )

                    presets.forEach { preset ->
                        val isSelected = currentEqualizer == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .tvFocusable(
                                    tag = "settings_eq_${preset.name.lowercase()}",
                                    shape = RoundedCornerShape(10.dp),
                                    defaultBgColor = if (isSelected) AudioPrimaryLight else LightSurfaceVariant,
                                    defaultBorderColor = if (isSelected) AudioPrimary else LightBorder,
                                    onClick = { onSetEqualizer(preset) }
                                )
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.title,
                                color = if (isSelected) AudioPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Sleep Timer
        item {
            SettingsCard(
                title = "Hẹn giờ tắt nhạc (Sleep Timer)",
                subtitle = "Tự động dừng phát nhạc sau khoảng thời gian đã chọn",
                icon = Icons.Default.Timer,
                iconColor = AudioAmber
            ) {
                val timerOptions = listOf(
                    Pair("Tắt", null),
                    Pair("15 phút", 15),
                    Pair("30 phút", 30),
                    Pair("45 phút", 45),
                    Pair("60 phút", 60),
                    Pair("90 phút", 90)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timerOptions.forEach { (label, minutes) ->
                        val isSelected = if (minutes == null) {
                            sleepTimerSeconds == null
                        } else {
                            val activeMin = sleepTimerSeconds?.let { (it + 59) / 60 }
                            activeMin == minutes
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .tvFocusable(
                                    tag = "settings_timer_${minutes ?: 0}",
                                    shape = RoundedCornerShape(10.dp),
                                    defaultBgColor = if (isSelected) Color(0xFFFEF3C7) else LightSurfaceVariant,
                                    defaultBorderColor = if (isSelected) Color(0xFFFDE68A) else LightBorder,
                                    onClick = { onSetSleepTimer(minutes) }
                                )
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) AudioAmber else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Storage & Rescan
        item {
            SettingsCard(
                title = "Bộ nhớ máy & Thư viện âm nhạc",
                subtitle = "Hiện có $totalTracksCount bài hát trong $totalFoldersCount thư mục",
                icon = Icons.Default.Folder,
                iconColor = AudioAmber
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .tvFocusable(
                                tag = "settings_btn_rescan",
                                shape = RoundedCornerShape(10.dp),
                                defaultBgColor = AudioPrimary,
                                focusedBgColor = Color(0xFF1E40AF),
                                onClick = onRefreshScan
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isScanning) "Đang quét thiết bị..." else "Quét lại toàn bộ bộ nhớ",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(
                        modifier = Modifier
                            .tvFocusable(
                                tag = "settings_btn_pick_dir",
                                shape = RoundedCornerShape(10.dp),
                                defaultBgColor = LightSurfaceVariant,
                                onClick = onPickStorageFolder
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mở thư mục tùy chỉnh (USB/Thẻ nhớ)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Section 5: Mouse & TV Remote Controls Guide
        item {
            SettingsCard(
                title = "Hướng dẫn điều khiển bằng Chuột & Remote TV",
                subtitle = "Hỗ trợ đầy đủ chuột máy tính, chuột bay (Air Mouse) và điều khiển TV",
                icon = Icons.Default.Mouse,
                iconColor = AudioEmerald
            ) {
                val inputTips = listOf(
                    Pair("Di chuyển chuột (Hover)", "Đưa chuột qua các nút hoặc bài hát để thấy hiệu ứng viền nổi bật và con trỏ hình bàn tay"),
                    Pair("Nhấp chuột trái (Click)", "Phát nhạc, mở thư mục, chọn bài hát hoặc chỉnh thanh tiến trình"),
                    Pair("Cuộn chuột (Scroll Wheel)", "Lướt danh sách thư mục và bài hát mượt mà"),
                    Pair("Phím D-Pad Remote (Lên/Xuống/Trái/Phải)", "Di chuyển tiêu điểm khi dùng remote TV"),
                    Pair("Phím OK / Center Remote", "Kích hoạt nút hoặc mở bài đang chọn"),
                    Pair("Phím Media Play / Pause / Next / Prev", "Điều khiển phát nhạc trực tiếp từ remote")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    inputTips.forEach { (key, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• $key: ",
                                color = AudioPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = desc,
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LightSurfaceCard)
            .border(1.dp, LightBorder, RoundedCornerShape(14.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEFF6FF))
                    .border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        content()
    }
}
