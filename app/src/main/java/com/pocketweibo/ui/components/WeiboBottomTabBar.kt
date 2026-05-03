package com.pocketweibo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pocketweibo.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.ui.theme.TabBackground
import com.pocketweibo.ui.theme.TabSelected
import com.pocketweibo.ui.theme.TabUnselected

enum class MainTab {
    HOME,
    MESSAGE,
    PLUS,
    DISCOVER,
    ME
}

@Composable
private fun MainTab.tabLabel(): String = stringResource(
    when (this) {
        MainTab.HOME -> R.string.tab_home
        MainTab.MESSAGE -> R.string.tab_message
        MainTab.PLUS -> R.string.tab_compose_cd
        MainTab.DISCOVER -> R.string.tab_discover
        MainTab.ME -> R.string.tab_me
    }
)

@Composable
fun WeiboBottomTabBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onPlusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(TabBackground)
    ) {
        TabItem(
            title = MainTab.HOME.tabLabel(),
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            isSelected = selectedTab == MainTab.HOME,
            onClick = { onTabSelected(MainTab.HOME) }
        )
        TabItem(
            title = MainTab.MESSAGE.tabLabel(),
            selectedIcon = Icons.Filled.Email,
            unselectedIcon = Icons.Outlined.Email,
            isSelected = selectedTab == MainTab.MESSAGE,
            onClick = { onTabSelected(MainTab.MESSAGE) }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clickable(onClick = onPlusClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(TabSelected, shape = androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = MainTab.PLUS.tabLabel(),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        TabItem(
            title = MainTab.DISCOVER.tabLabel(),
            selectedIcon = Icons.Filled.Search,
            unselectedIcon = Icons.Outlined.Search,
            isSelected = selectedTab == MainTab.DISCOVER,
            onClick = { onTabSelected(MainTab.DISCOVER) }
        )
        TabItem(
            title = MainTab.ME.tabLabel(),
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
            isSelected = selectedTab == MainTab.ME,
            onClick = { onTabSelected(MainTab.ME) }
        )
    }
}

@Composable
private fun RowScope.TabItem(
    title: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = title,
            tint = if (isSelected) TabSelected else TabUnselected,
            modifier = Modifier.size(26.dp)
        )
        Text(
            text = title,
            fontSize = 10.sp,
            color = if (isSelected) TabSelected else TabUnselected
        )
    }
}
