package com.pocketweibo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    onRightIconClick: (() -> Unit)? = null,
    /** Shown under the title in the center (optional subtitle / chips). */
    centerBelowTitle: @Composable (() -> Unit)? = null,
    /** When [showDropdown] is true, spoken label for the title tap target (e.g. “Open quick access”). */
    titleDropdownContentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (centerBelowTitle != null) {
                    Modifier.heightIn(min = 44.dp).wrapContentHeight()
                } else {
                    Modifier.heightIn(min = 44.dp, max = 44.dp)
                }
            )
            .background(TabBackground)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leftIcon != null) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                leftIcon()
            }
        } else {
            Box(modifier = Modifier.size(48.dp)) {}
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (centerBelowTitle != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (showDropdown && titleDropdownContentDescription != null) {
                                    Modifier.semantics {
                                        contentDescription = titleDropdownContentDescription
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(enabled = showDropdown, onClick = onTitleClick),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
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
                    centerBelowTitle()
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (showDropdown && titleDropdownContentDescription != null) {
                                Modifier.semantics {
                                    contentDescription = titleDropdownContentDescription
                                }
                            } else {
                                Modifier
                            }
                        )
                        .clickable(enabled = showDropdown, onClick = onTitleClick),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
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
        }

        if (rightIcon != null) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(if (onRightIconClick != null) 48.dp else 24.dp)
                    .then(
                        if (onRightIconClick != null) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onRightIconClick
                            )
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                rightIcon()
            }
        } else {
            Box(modifier = Modifier.size(48.dp)) {}
        }
    }
}
