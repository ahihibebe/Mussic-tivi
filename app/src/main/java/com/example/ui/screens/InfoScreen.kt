package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.tvFocusable
import com.example.ui.theme.LocalTvStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoScreen(
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val cardShape = RoundedCornerShape(18.dp)
    val cardBorderModifier = if (style.isGlassStyle) {
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
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        Column {
            Text(
                text = "Thông Tin & Ủng Hộ",
                color = style.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Kết nối tác giả & ủng hộ phát triển TV Music",
                color = style.textSecondary,
                fontSize = 13.sp
            )
        }

        // Responsive layout: 2 columns when wide, stacked vertically when narrow
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWideLayout = maxWidth >= 600.dp

            val facebookCard: @Composable (Modifier) -> Unit = { cardMod ->
                Column(
                    modifier = cardMod
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(style.cardSurface)
                        .then(cardBorderModifier)
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(style.accentLightBg)
                                .border(1.dp, style.accentLightBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Facebook",
                                tint = style.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Liên hệ / Facebook",
                                color = style.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Facebook.com/truongalone",
                                color = style.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .tvFocusable(
                                    tag = "info_btn_open_facebook",
                                    shape = RoundedCornerShape(10.dp),
                                    defaultBgColor = style.accentColor,
                                    focusedBgColor = style.accentColor.copy(alpha = 0.85f),
                                    defaultBorderColor = style.accentColor,
                                    onClick = {
                                        openUrlSafely(context, "https://www.facebook.com/truongalone")
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Mở Facebook",
                                tint = style.accentContentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mở Facebook",
                                color = style.accentContentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .tvFocusable(
                                    tag = "info_btn_copy_facebook",
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = {
                                        copyToClipboard(
                                            context = context,
                                            label = "Facebook",
                                            value = "https://www.facebook.com/truongalone",
                                            toastMessage = "Đã sao chép link Facebook!"
                                        )
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Sao chép link",
                                tint = style.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sao chép link",
                                color = style.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            val donateCard: @Composable (Modifier) -> Unit = { cardMod ->
                Column(
                    modifier = cardMod
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(style.cardSurface)
                        .then(cardBorderModifier)
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(style.amberBg)
                                .border(1.dp, style.amberBorder, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            BimBimSnackIcon(size = 34.dp)
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Donate gói bim bim",
                                color = style.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Cảm ơn bạn đã ủng hộ tác giả duy trì và nâng cấp ứng dụng!",
                                color = style.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bank Account Box (sharp corners as requested)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(style.surfaceVariant, RectangleShape)
                            .border(1.dp, style.borderColor, RectangleShape)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = "MB Bank",
                            tint = style.accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ngân hàng MB Bank",
                                color = style.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "MB : STK 220011026789",
                                color = style.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            val qrCard: @Composable (Modifier) -> Unit = { cardMod ->
                Column(
                    modifier = cardMod
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(style.cardSurface)
                        .then(cardBorderModifier)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = style.emeraldColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mã QR Ủng Hộ",
                            color = style.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Responsive square QR container that never overflows card bounds
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 220.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.qr_donate_mb),
                            contentDescription = "Mã QR MB Bank STK 220011026789",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .background(style.emeraldBg, RectangleShape)
                            .border(1.dp, style.emeraldBorder, RectangleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "MB Bank • 220011026789",
                            color = style.emeraldColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Quét mã bằng ứng dụng ngân hàng trên điện thoại",
                        color = style.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (isWideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        facebookCard(Modifier)
                        donateCard(Modifier)
                    }

                    Box(modifier = Modifier.weight(0.85f)) {
                        qrCard(Modifier)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    facebookCard(Modifier)
                    donateCard(Modifier)
                    qrCard(Modifier)
                }
            }
        }
    }
}

@Composable
private fun BimBimSnackIcon(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bagLeft = w * 0.16f
        val bagRight = w * 0.84f
        val bagTop = h * 0.14f
        val bagBottom = h * 0.86f
        val bagWidth = bagRight - bagLeft
        val bagHeight = bagBottom - bagTop

        val topSealPath = Path().apply {
            moveTo(bagLeft - w * 0.04f, bagTop)
            val teeth = 6
            val step = (bagWidth + w * 0.08f) / teeth
            for (i in 0 until teeth) {
                val x = (bagLeft - w * 0.04f) + i * step
                lineTo(x + step * 0.5f, bagTop - h * 0.08f)
                lineTo(x + step, bagTop)
            }
            close()
        }
        drawPath(topSealPath, color = Color(0xFFDC2626))

        val bottomSealPath = Path().apply {
            moveTo(bagLeft - w * 0.04f, bagBottom)
            val teeth = 6
            val step = (bagWidth + w * 0.08f) / teeth
            for (i in 0 until teeth) {
                val x = (bagLeft - w * 0.04f) + i * step
                lineTo(x + step * 0.5f, bagBottom + h * 0.08f)
                lineTo(x + step, bagBottom)
            }
            close()
        }
        drawPath(bottomSealPath, color = Color(0xFFDC2626))

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFEF4444), Color(0xFFEA580C)),
                start = Offset(bagLeft, bagTop),
                end = Offset(bagRight, bagBottom)
            ),
            topLeft = Offset(bagLeft, bagTop),
            size = Size(bagWidth, bagHeight),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
        )

        drawRoundRect(
            color = Color(0xFFFDE047),
            topLeft = Offset(bagLeft + bagWidth * 0.08f, bagTop + bagHeight * 0.25f),
            size = Size(bagWidth * 0.84f, bagHeight * 0.50f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )

        drawCircle(
            color = Color(0xFFF59E0B),
            radius = bagWidth * 0.22f,
            center = Offset(w * 0.50f, h * 0.50f)
        )
        drawCircle(
            color = Color(0xFFFEF08A),
            radius = bagWidth * 0.11f,
            center = Offset(w * 0.47f, h * 0.47f)
        )

        drawRoundRect(
            color = Color.White.copy(alpha = 0.28f),
            topLeft = Offset(bagLeft + bagWidth * 0.10f, bagTop + bagHeight * 0.08f),
            size = Size(bagWidth * 0.16f, bagHeight * 0.84f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
    }
}

private fun openUrlSafely(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        copyToClipboard(
            context = context,
            label = "Facebook",
            value = url,
            toastMessage = "Không tìm thấy trình duyệt, đã sao chép link!"
        )
    }
}

private fun copyToClipboard(
    context: Context,
    label: String,
    value: String,
    toastMessage: String
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
