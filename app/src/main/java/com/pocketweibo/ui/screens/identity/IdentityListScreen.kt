package com.pocketweibo.ui.screens.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.R
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityListScreen(
    onBack: () -> Unit,
    onIdentityClick: (Long) -> Unit,
    onAddIdentity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val identities by app.repository.allIdentities.collectAsState(initial = emptyList())
    val activeIdentity by app.repository.activeIdentity.collectAsState(initial = null)
    var identityToDelete by remember { mutableStateOf<IdentityEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        WeiboTitleBar(
            title = stringResource(R.string.title_identities),
            leftIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back_cd),
                        tint = WeiboOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            rightIcon = {
                IconButton(onClick = onAddIdentity) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_identity_cd),
                        tint = WeiboOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Divider(thickness = 0.5.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(identities, key = { it.id }) { identity ->
                IdentityListItem(
                    identity = identity,
                    isActive = identity.id == activeIdentity?.id,
                    currentSuffix = stringResource(R.string.identity_current_suffix),
                    activateLabel = stringResource(R.string.identity_activate),
                    onClick = { onIdentityClick(identity.id) },
                    onDelete = { identityToDelete = identity },
                    onActivate = {
                        CoroutineScope(Dispatchers.IO).launch {
                            app.repository.setActiveIdentity(identity.id)
                        }
                    }
                )
                Divider(thickness = 0.5.dp)
            }
        }
    }

    identityToDelete?.let { identity ->
        AlertDialog(
            onDismissRequest = { identityToDelete = null },
            title = { Text(stringResource(R.string.identity_delete_title)) },
            text = {
                Text(stringResource(R.string.identity_delete_message, identity.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            app.repository.deleteIdentity(identity)
                        }
                        identityToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { identityToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun IdentityListItem(
    identity: IdentityEntity,
    isActive: Boolean,
    currentSuffix: String,
    activateLabel: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IdentityAvatar(
                resName = identity.avatarResName,
                size = 48
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = identity.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayDark
                    )
                    if (isActive) {
                        Text(
                            text = currentSuffix,
                            fontSize = 14.sp,
                            color = WeiboOrange
                        )
                    }
                }
                if (identity.nationality.isNotEmpty() || identity.occupation.isNotEmpty()) {
                    Text(
                        text = listOf(identity.nationality, identity.occupation)
                            .filter { it.isNotEmpty() }
                            .joinToString(" · "),
                        fontSize = 13.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            if (!isActive) {
                Text(
                    text = activateLabel,
                    fontSize = 13.sp,
                    color = WeiboOrange,
                    modifier = Modifier
                        .background(WeiboOrange.copy(alpha = 0.1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable { onActivate() }
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = GrayLight
            )
        }
    }
}

@Composable
fun IdentityAvatar(
    resName: String,
    size: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resourceId = remember(resName) {
        context.resources.getIdentifier(resName, "drawable", context.packageName)
    }
    
    if (resourceId != 0) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = resourceId),
            contentDescription = null,
            modifier = modifier.size(size.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .background(GrayLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = resName.firstOrNull()?.toString() ?: "?",
                fontSize = (size / 2.5).sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
