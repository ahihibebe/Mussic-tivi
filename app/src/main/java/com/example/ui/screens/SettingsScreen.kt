package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.AppUiStyle
import com.example.model.EqualizerPreset
import com.example.ui.components.tvFocusable
import com.example.ui.theme.AppleTvAccent
import com.example.ui.theme.AppleTvDeepBg
import com.example.ui.theme.AppleTvGlowTop
import com.example.ui.theme.AudioPrimary
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LocalTvStyle
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiStyle: AppUiStyle,
    customBackgroundPath: String?,
    backgroundOpacity: Float,
    glassBlurRadius: Float,
    currentEqualizer: EqualizerPreset,
    sleepTimerSeconds: Int?,
    totalTracksCount: Int,
    totalFoldersCount: Int,
    isScanning: Boolean,
    onSetUiStyle: (AppUiStyle) -> Unit,
    onSetBackgroundOpacity: (Float) -> Unit,
    onSetGlassBlurRadius: (Float) -> Unit,
    onOpenQrBackground: () -> Unit,
    onPickBackgroundImage: () -> Unit,
    onClearBackground: () -> Unit,
    onSetEqualizer: (EqualizerPreset) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onRefreshScan: () -> Unit,
    onPickStorageFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Cài Đặt Giao Diện & Âm Thanh",
                    color = style.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Chuyển đổi phong cách Apple TV Glass, tùy chỉnh ảnh nền QR & tối ưu âm thanh",
                    color = style.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        // Section 1: UI Style (Classic Light vs Apple TV Frosted Glass)
        item {
            SettingsCard(
                title = "Phong cách giao diện (UI Style)",
                subtitle = "Chọn giữa Nền Trắng thanh lịch hoặc Apple TV với hiệu ứng kính mờ",
                icon = Icons.Default.AutoAwesome,
                iconColor = style.accentColor
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    StylePreviewCard(
                        title = "Nền Trắng Thanh Lịch",
                        subtitle = "Sáng rõ, tương phản cao như phòng thu",
                        isSelected = uiStyle == AppUiStyle.CLASSIC_LIGHT,
                        isAppleTvPreview = false,
                        tag = "style_option_classic_light",
                        onClick = { onSetUiStyle(AppUiStyle.CLASSIC_LIGHT) },
                        modifier = Modifier.weight(1f)
                    )

                    StylePreviewCard(
                        title = "Apple TV (Hiệu Ứng Kính)",
                        subtitle = "Kính mờ trong suốt tvOS, viền sáng tinh tế",
                        isSelected = uiStyle == AppUiStyle.APPLE_TV_GLASS,
                        isAppleTvPreview = true,
                        tag = "style_option_apple_tv_glass",
                        onClick = { onSetUiStyle(AppUiStyle.APPLE_TV_GLASS) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (uiStyle == AppUiStyle.APPLE_TV_GLASS) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(style.surfaceVariant)
                            .border(1.dp, style.borderColor, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Độ mờ nền hiệu ứng kính (Glass Blur)",
                                color = style.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${glassBlurRadius.roundToInt()} dp",
                                color = style.accentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = glassBlurRadius,
                            onValueChange = onSetGlassBlurRadius,
                            valueRange = 0f..24f,
                            colors = SliderDefaults.colors(
                                thumbColor = style.accentColor,
                                activeTrackColor = style.accentColor,
                                inactiveTrackColor = style.inactiveTrackColor
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                0f to "Không mờ (0dp)",
                                6f to "Nhẹ (6dp)",
                                14f to "Vừa (14dp)",
                                22f to "Mạnh (22dp)"
                            ).forEach { (blurVal, label) ->
                                val isCurrent = abs(glassBlurRadius - blurVal) < 2.5f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .tvFocusable(
                                            tag = "settings_blur_${blurVal.toInt()}",
                                            shape = RoundedCornerShape(8.dp),
                                            defaultBgColor = if (isCurrent) style.selectedPillSurface else style.cardSurface,
                                            defaultBorderColor = if (isCurrent) style.accentColor else style.borderColor,
                                            onClick = { onSetGlassBlurRadius(blurVal) }
                                        )
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isCurrent) style.accentColor else style.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Custom Background Image via Phone QR & Transparency
        item {
            SettingsCard(
                title = "Ảnh nền TV & Gửi qua mã QR điện thoại",
                subtitle = "Quét mã QR để gửi ảnh từ điện thoại hoặc chọn ảnh có sẵn, tùy chỉnh độ trong suốt",
                icon = Icons.Default.Wallpaper,
                iconColor = style.emeraldColor
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Background thumbnail preview
                    Box(
                        modifier = Modifier
                            .size(width = 148.dp, height = 92.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(style.surfaceVariant)
                            .border(1.dp, style.borderColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (customBackgroundPath != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(File(customBackgroundPath))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Xem trước ảnh nền",
                                contentScale = ContentScale.Crop,
                                alpha = backgroundOpacity,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Wallpaper,
                                    contentDescription = null,
                                    tint = style.textMuted,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Nền mặc định",
                                    color = style.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Action Buttons
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .tvFocusable(
                                        tag = "settings_btn_qr_bg",
                                        shape = RoundedCornerShape(10.dp),
                                        defaultBgColor = style.accentColor,
                                        focusedBgColor = style.accentColor.copy(alpha = 0.85f),
                                        defaultBorderColor = style.accentColor,
                                        onClick = onOpenQrBackground
                                    )
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = "Quét mã QR",
                                    tint = style.accentContentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Quét QR từ điện thoại",
                                    color = style.accentContentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .tvFocusable(
                                        tag = "settings_btn_pick_bg",
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = onPickBackgroundImage
                                    )
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Chọn ảnh trên máy",
                                    tint = style.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Chọn ảnh trên thiết bị",
                                    color = style.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (customBackgroundPath != null) {
                                Row(
                                    modifier = Modifier
                                        .tvFocusable(
                                            tag = "settings_btn_clear_bg",
                                            shape = RoundedCornerShape(10.dp),
                                            defaultBgColor = style.redBg,
                                            defaultBorderColor = style.redBorder,
                                            onClick = onClearBackground
                                        )
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Xóa ảnh nền",
                                        tint = style.redColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Xóa",
                                        color = style.redColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (customBackgroundPath != null) {
                                "Đang sử dụng ảnh nền tùy chỉnh — điều chỉnh độ hiển thị bên dưới"
                            } else {
                                "Hỗ trợ gửi ảnh nhanh từ iPhone & Android qua mã QR cùng mạng Wi-Fi"
                            },
                            color = style.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Background Transparency / Opacity Slider + Presets
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(style.surfaceVariant)
                        .border(1.dp, style.borderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Độ trong suốt / rõ nét của ảnh nền",
                            color = style.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${(backgroundOpacity * 100).roundToInt()}%",
                            color = style.accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = backgroundOpacity,
                        onValueChange = onSetBackgroundOpacity,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = style.accentColor,
                            activeTrackColor = style.accentColor,
                            inactiveTrackColor = style.inactiveTrackColor
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0.15f to "15%",
                            0.35f to "35%",
                            0.55f to "55%",
                            0.75f to "75%",
                            1.0f to "100%"
                        ).forEach { (valOp, label) ->
                            val isCurrent = abs(backgroundOpacity - valOp) < 0.05f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .tvFocusable(
                                        tag = "settings_opacity_$label",
                                        shape = RoundedCornerShape(8.dp),
                                        defaultBgColor = if (isCurrent) style.selectedPillSurface else style.cardSurface,
                                        defaultBorderColor = if (isCurrent) style.accentColor else style.borderColor,
                                        onClick = { onSetBackgroundOpacity(valOp) }
                                    )
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isCurrent) style.accentColor else style.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Audio Formats
        item {
            SettingsCard(
                title = "Định dạng âm thanh hỗ trợ",
                subtitle = "Giải mã trực tiếp phần cứng và phần mềm trên Android TV",
                icon = Icons.Default.Audiotrack,
                iconColor = style.accentColor
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
                                .background(style.surfaceVariant)
                                .border(1.dp, style.borderColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(style.accentLightBg)
                                    .border(1.dp, style.accentLightBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = fmt,
                                    color = style.accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = desc,
                                color = style.textPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Equalizer Preset
        item {
            SettingsCard(
                title = "Bộ cân bằng âm thanh (Equalizer Preset)",
                subtitle = "Hiệu chỉnh dải tần số âm thanh phù hợp với loa TV hoặc dàn loa ngoài",
                icon = Icons.Default.Tune,
                iconColor = style.accentColor
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
                                    defaultBgColor = if (isSelected) style.selectedPillSurface else style.surfaceVariant,
                                    defaultBorderColor = if (isSelected) style.accentColor else style.borderColor,
                                    onClick = { onSetEqualizer(preset) }
                                )
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.title,
                                color = if (isSelected) style.accentColor else style.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Section 5: Sleep Timer
        item {
            SettingsCard(
                title = "Hẹn giờ tắt nhạc (Sleep Timer)",
                subtitle = "Tự động dừng phát nhạc sau khoảng thời gian đã chọn",
                icon = Icons.Default.Timer,
                iconColor = style.amberColor
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
                                    defaultBgColor = if (isSelected) style.amberBg else style.surfaceVariant,
                                    defaultBorderColor = if (isSelected) style.amberBorder else style.borderColor,
                                    onClick = { onSetSleepTimer(minutes) }
                                )
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) style.amberColor else style.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Section 6: Storage & Rescan
        item {
            SettingsCard(
                title = "Bộ nhớ máy & Thư viện âm nhạc",
                subtitle = "Hiện có $totalTracksCount bài hát trong $totalFoldersCount thư mục",
                icon = Icons.Default.Folder,
                iconColor = style.amberColor
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
                                defaultBgColor = style.accentColor,
                                focusedBgColor = style.accentColor.copy(alpha = 0.85f),
                                defaultBorderColor = style.accentColor,
                                onClick = onRefreshScan
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = style.accentContentColor,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = style.accentContentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isScanning) "Đang quét thiết bị..." else "Quét lại toàn bộ bộ nhớ",
                            color = style.accentContentColor,
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
                                defaultBgColor = style.surfaceVariant,
                                onClick = onPickStorageFolder
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mở thư mục tùy chỉnh (USB/Thẻ nhớ)",
                            color = style.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Section 7: Mouse & TV Remote Controls Guide
        item {
            SettingsCard(
                title = "Hướng dẫn điều khiển bằng Chuột & Remote TV",
                subtitle = "Hỗ trợ đầy đủ chuột máy tính, chuột bay (Air Mouse) và điều khiển TV",
                icon = Icons.Default.Mouse,
                iconColor = style.emeraldColor
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
                                color = style.accentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = desc,
                                color = style.textSecondary,
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
private fun StylePreviewCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isAppleTvPreview: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current

    Row(
        modifier = modifier
            .tvFocusable(
                tag = tag,
                shape = RoundedCornerShape(14.dp),
                defaultBgColor = if (isSelected) style.selectedPillSurface else style.surfaceVariant,
                defaultBorderColor = if (isSelected) style.accentColor else style.borderColor,
                onClick = onClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mini UI preview swatch
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 50.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    if (isAppleTvPreview) Color.White.copy(alpha = 0.35f) else Color(0xFFCBD5E1),
                    RoundedCornerShape(10.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (isAppleTvPreview) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(AppleTvDeepBg, AppleTvGlowTop)
                        )
                    )
                    // Translucent glass panel preview
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(8f, 8f),
                        size = Size(size.width * 0.32f, size.height - 16f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = AppleTvAccent.copy(alpha = 0.75f),
                        topLeft = Offset(size.width * 0.46f, 10f),
                        size = Size(size.width * 0.46f, size.height * 0.42f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                } else {
                    drawRect(color = LightBackground)
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(8f, 8f),
                        size = Size(size.width * 0.32f, size.height - 16f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = AudioPrimary,
                        topLeft = Offset(size.width * 0.46f, 10f),
                        size = Size(size.width * 0.46f, size.height * 0.42f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isAppleTvPreview) Icons.Default.AutoAwesome else Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = if (isSelected) style.accentColor else style.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = style.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = style.textSecondary,
                fontSize = 11.sp
            )
        }

        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Đang chọn",
                tint = style.accentColor,
                modifier = Modifier.size(22.dp)
            )
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
    val style = LocalTvStyle.current
    val cardShape = RoundedCornerShape(16.dp)
    val cardBorder = if (style.isGlassStyle) {
        Modifier.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(style.glassSpecularTop, style.glassSpecularBottom)
            ),
            shape = cardShape
        )
    } else {
        Modifier.border(1.dp, style.borderColor, cardShape)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(style.cardSurface)
            .then(cardBorder)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(style.accentLightBg)
                    .border(1.dp, style.accentLightBorder, RoundedCornerShape(10.dp)),
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
                    color = style.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = style.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        content()
    }
}
