package com.pocketweibo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Avatar(
    name: String,
    color: Color,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    avatarResName: String? = null
) {
    val context = LocalContext.current
    
    if (avatarResName != null && avatarResName != "avatar_default") {
        val resourceId = context.resources.getIdentifier(avatarResName, "drawable", context.packageName)
        if (resourceId != 0) {
            Image(
                painter = androidx.compose.ui.res.painterResource(id = resourceId),
                contentDescription = name,
                modifier = modifier.size(size),
                contentScale = ContentScale.Fit
            )
            return
        }
    }
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontSize = (size.value / 2).sp,
            fontWeight = FontWeight.Bold
        )
    }
}
