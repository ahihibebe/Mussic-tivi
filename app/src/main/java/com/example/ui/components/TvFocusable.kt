package com.example.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalTvStyle

@Composable
fun Modifier.tvFocusable(
    tag: String,
    shape: Shape = RoundedCornerShape(14.dp),
    scaleUpFactor: Float = 1.03f,
    defaultBgColor: Color = Color.Unspecified,
    focusedBgColor: Color = Color.Unspecified,
    defaultBorderColor: Color = Color.Unspecified,
    focusedBorderColor: Color = Color.Unspecified,
    elevation: Dp = 1.dp,
    onClick: () -> Unit
): Modifier {
    val style = LocalTvStyle.current
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val isActive = isFocused || isHovered

    val resolvedDefaultBg = if (defaultBgColor != Color.Unspecified) defaultBgColor else style.cardSurface
    val resolvedFocusedBg = if (focusedBgColor != Color.Unspecified) focusedBgColor else style.focusedSurface
    val resolvedDefaultBorder = if (defaultBorderColor != Color.Unspecified) defaultBorderColor else style.borderColor
    val resolvedFocusedBorder = if (focusedBorderColor != Color.Unspecified) focusedBorderColor else style.focusedBorderColor

    val scale by animateFloatAsState(
        targetValue = if (isActive) scaleUpFactor else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv_mouse_scale"
    )

    val activeElevation = if (style.isGlassStyle) {
        if (isActive) elevation + 10.dp else elevation + 2.dp
    } else {
        if (isActive) elevation + 4.dp else elevation
    }

    val borderModifier = if (style.isGlassStyle && !isActive && defaultBorderColor == Color.Unspecified) {
        Modifier.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(style.glassSpecularTop, style.glassSpecularBottom)
            ),
            shape = shape
        )
    } else {
        Modifier.border(
            BorderStroke(
                width = if (isActive) 1.75.dp else 1.dp,
                color = if (isActive) resolvedFocusedBorder else resolvedDefaultBorder
            ),
            shape = shape
        )
    }

    return this
        .testTag(tag)
        .scale(scale)
        .shadow(
            elevation = activeElevation,
            shape = shape,
            ambientColor = if (isActive) style.focusShadowColor else Color(0x18000000),
            spotColor = if (isActive) resolvedFocusedBorder else Color(0x22000000)
        )
        .clip(shape)
        .background(if (isActive) resolvedFocusedBg else resolvedDefaultBg)
        .then(borderModifier)
        .onFocusChanged { isFocused = it.isFocused }
        .focusable()
        .hoverable(interactionSource = interactionSource)
        .pointerHoverIcon(PointerIcon.Hand)
        .onKeyEvent { keyEvent ->
            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                val code = keyEvent.nativeKeyEvent.keyCode
                if (code == KeyEvent.KEYCODE_DPAD_CENTER ||
                    code == KeyEvent.KEYCODE_ENTER ||
                    code == KeyEvent.KEYCODE_NUMPAD_ENTER
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null
        ) {
            onClick()
        }
}
