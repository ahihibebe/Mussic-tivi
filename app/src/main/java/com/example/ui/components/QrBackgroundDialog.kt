package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.qr.QrCodeGenerator
import com.example.ui.theme.LocalTvStyle
import java.io.File
import kotlin.math.roundToInt

@Composable
fun QrBackgroundDialog(
    serverUrl: String?,
    uploadStatus: String,
    customBackgroundPath: String?,
    backgroundOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    onPickLocalImage: () -> Unit,
    onClearBackground: () -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalTvStyle.current
    val context = LocalContext.current
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(serverUrl) {
        qrBitmap = if (!serverUrl.isNullOrBlank()) {
            QrCodeGenerator.generateQrBitmap(serverUrl, 512)
        } else {
            null
        }
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogBg = if (style.isGlassStyle) {
            Color(0xE60F172A)
        } else {
            Color.White
        }

        val borderModifier = if (style.isGlassStyle) {
            Modifier.border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(style.glassSpecularTop, style.glassSpecularBottom)
                ),
                shape = RoundedCornerShape(22.dp)
            )
        } else {
            Modifier.border(1.dp, style.borderColor, RoundedCornerShape(22.dp))
        }

        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 840.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(dialogBg)
                .then(borderModifier)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Dialog Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(style.accentLightBg)
                            .border(1.dp, style.accentLightBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "Mã QR",
                            tint = style.accentColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Đổi Ảnh Nền Qua Mã QR Điện Thoại",
                            color = style.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gửi ảnh trực tiếp từ điện thoại lên TV qua cùng mạng Wi-Fi",
                            color = style.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .tvFocusable(
                            tag = "qr_dialog_close",
                            shape = CircleShape,
                            defaultBgColor = style.surfaceVariant,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = style.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Content Split
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left: Scannable QR Code Card
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(style.surfaceVariant)
                        .border(1.dp, style.borderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Always keep QR background pure white for camera readability
                    Box(
                        modifier = Modifier
                            .size(204.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val currentQr = qrBitmap
                        if (currentQr != null) {
                            Image(
                                bitmap = currentQr,
                                contentDescription = "Mã QR tải ảnh nền",
                                modifier = Modifier.size(184.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                color = style.accentColor,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(style.accentLightBg)
                            .border(1.dp, style.accentLightBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = style.accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = serverUrl ?: "Đang khởi tạo địa chỉ...",
                            color = style.accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = uploadStatus,
                        color = if (uploadStatus.contains("thành công") || uploadStatus.contains("mới")) {
                            style.emeraldColor
                        } else {
                            style.textSecondary
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Right: Instructions, Preview & Transparency Controls
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Steps
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(style.surfaceVariant)
                            .border(1.dp, style.borderColor, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Hướng dẫn nhanh:",
                            color = style.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1. Đảm bảo điện thoại và Tivi kết nối cùng 1 mạng Wi-Fi.",
                            color = style.textSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "2. Mở Camera hoặc Zalo/Trình duyệt trên điện thoại để quét mã QR (hoặc nhập đường dẫn bên trái).",
                            color = style.textSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "3. Chọn ảnh từ điện thoại và nhấn \"Gửi lên Tivi ngay\" — ảnh sẽ cập nhật tức thì.",
                            color = style.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // Opacity Control & Current Background Preview
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(style.surfaceVariant)
                            .border(1.dp, style.borderColor, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Độ trong suốt / hiển thị ảnh nền",
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
                            onValueChange = onOpacityChange,
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
                            listOf(0.2f to "20%", 0.45f to "45%", 0.65f to "65%", 0.9f to "90%").forEach { (valOp, label) ->
                                val isCurrent = kotlin.math.abs(backgroundOpacity - valOp) < 0.06f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .tvFocusable(
                                            tag = "qr_opacity_$label",
                                            shape = RoundedCornerShape(8.dp),
                                            defaultBgColor = if (isCurrent) style.selectedPillSurface else style.cardSurface,
                                            defaultBorderColor = if (isCurrent) style.accentColor else style.borderColor,
                                            onClick = { onOpacityChange(valOp) }
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

                        if (customBackgroundPath != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(File(customBackgroundPath))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Ảnh nền hiện tại",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp, 36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, style.borderColor, RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Ảnh nền đang được áp dụng trực tiếp",
                                    color = style.emeraldColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Bottom Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .tvFocusable(
                                    tag = "qr_pick_local_image",
                                    shape = RoundedCornerShape(12.dp),
                                    defaultBgColor = style.accentColor,
                                    focusedBgColor = style.accentColor.copy(alpha = 0.85f),
                                    defaultBorderColor = style.accentColor,
                                    onClick = onPickLocalImage
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = style.accentContentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Chọn ảnh trên máy",
                                color = style.accentContentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (customBackgroundPath != null) {
                            Row(
                                modifier = Modifier
                                    .tvFocusable(
                                        tag = "qr_clear_background",
                                        shape = RoundedCornerShape(12.dp),
                                        defaultBgColor = style.redBg,
                                        defaultBorderColor = style.redBorder,
                                        onClick = onClearBackground
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                    text = "Xóa ảnh",
                                    color = style.redColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
