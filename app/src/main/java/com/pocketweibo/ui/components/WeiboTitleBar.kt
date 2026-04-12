package com.pocketweibo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.TabBackground
import com.pocketweibo.ui.theme.WeiboOrange

@Composable
fun WeiboTitleBar(
    title: String,
    showDropdown: Boolean = false,
    onTitleClick: () -> Unit = {},
    leftIcon: @Composable (() -> Unit)? = null,
    rightIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(TabBackground)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leftIcon != null) {
            Box(modifier = Modifier.size(24.dp)) {
                leftIcon()
            }
        } else {
            Box(modifier = Modifier.size(24.dp)) {}
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = showDropdown, onClick = onTitleClick)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = GrayDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (showDropdown) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = WeiboOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (rightIcon != null) {
            Box(modifier = Modifier.size(24.dp)) {
                rightIcon()
            }
        } else {
            Box(modifier = Modifier.size(24.dp)) {}
        }
    }
}
