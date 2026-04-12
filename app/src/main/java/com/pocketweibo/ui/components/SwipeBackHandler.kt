package com.pocketweibo.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SwipeBackHandler(
    onSwipeBack: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) offsetX else 0f,
        label = "swipe_offset"
    )
    
    BackHandler(enabled = enabled) {
        onSwipeBack()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                if (offsetX > screenWidthPx * 0.3f) {
                                    onSwipeBack()
                                }
                                offsetX = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                offsetX = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val newOffset = offsetX + dragAmount
                                if (newOffset > 0) {
                                    offsetX = newOffset.coerceAtMost(screenWidthPx * 0.5f)
                                }
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
        ) {
            content()
        }
        
        if (animatedOffset > 0 && enabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset((animatedOffset - 50).roundToInt(), 0) }
                    .background(Color.Black.copy(alpha = (animatedOffset / screenWidthPx * 0.5f).coerceIn(0f, 0.3f)))
            )
        }
    }
}
