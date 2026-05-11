package com.pocketweibo.ui.screens.identity

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.R
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.data.local.entity.Gender
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hand-drawn sketch-style presets (Open Peeps, CC0) via DiceBear; see `legal/open_peeps_notice.txt`. */
internal val avatarSketchMalePresets = listOf(
    "avatar_sketch_m1", "avatar_sketch_m2", "avatar_sketch_m3", "avatar_sketch_m4"
)

internal val avatarSketchFemalePresets = listOf(
    "avatar_sketch_f1", "avatar_sketch_f2", "avatar_sketch_f3", "avatar_sketch_f4"
)

@Composable
private fun PresetAvatarThumbnail(resName: String, size: Int) {
    val context = LocalContext.current
    val resourceId = context.resources.getIdentifier(resName, "drawable", context.packageName)
    
    if (resourceId != 0) {
        Image(
            painter = androidx.compose.ui.res.painterResource(id = resourceId),
            contentDescription = null,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(Color(0xFF4A90D9), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.me_avatar_placeholder),
                color = Color.White,
                fontSize = (size / 2).sp
            )
        }
    }
}

@Composable
private fun PresetAvatarRow(
    resName: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) WeiboOrange else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center
    ) {
        PresetAvatarThumbnail(resName = resName, size = 48)
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = WeiboOrange,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityDetailScreen(
    identityId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    
    var identity by remember { mutableStateOf<IdentityEntity?>(null) }
    var isEditing by remember { mutableStateOf(identityId == 0L) }
    
    var name by remember { mutableStateOf("") }
    var avatarResName by remember { mutableStateOf("avatar_default") }
    var nationality by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(Gender.OTHER) }
    var birthYear by remember { mutableStateOf("") }
    var deathYear by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }
    var motto by remember { mutableStateOf("") }
    var famousWork by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    
    val identities by app.repository.allIdentities.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var pendingAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var preferCustomAvatar by remember { mutableStateOf(false) }
    var titleMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteIdentityDialog by remember { mutableStateOf(false) }

    val pickAvatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingAvatarUri = uri
            preferCustomAvatar = true
        }
    }

    LaunchedEffect(isEditing, identityId, identity) {
        if (!isEditing) return@LaunchedEffect
        pendingAvatarUri = null
        preferCustomAvatar = when {
            identityId == 0L -> false
            else -> identity?.customAvatarUri.isNullOrBlank().not()
        }
    }

    if (identityId > 0 && identity == null) {
        identity = identities.find { it.id == identityId }
        identity?.let { i ->
            name = i.name
            avatarResName = i.avatarResName
            nationality = i.nationality
            gender = i.gender
            birthYear = i.birthYear?.toString() ?: ""
            deathYear = i.deathYear?.toString() ?: ""
            occupation = i.occupation
            motto = i.motto
            famousWork = i.famousWork
            bio = i.bio
        }
    }
    
    val title = when {
        identityId == 0L -> stringResource(R.string.identity_add_title)
        isEditing -> stringResource(R.string.identity_edit_title)
        else -> stringResource(R.string.identity_detail_title)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .imePadding()
    ) {
        WeiboTitleBar(
            title = title,
            leftIcon = {
                IconButton(onClick = {
                    if (isEditing && identityId > 0) {
                        isEditing = false
                        identity?.let { i ->
                            name = i.name
                            avatarResName = i.avatarResName
                            nationality = i.nationality
                            gender = i.gender
                            birthYear = i.birthYear?.toString() ?: ""
                            deathYear = i.deathYear?.toString() ?: ""
                            occupation = i.occupation
                            motto = i.motto
                            famousWork = i.famousWork
                            bio = i.bio
                        }
                    } else {
                        onBack()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back_cd),
                        tint = WeiboOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            rightIcon = if (identityId > 0) {
                {
                    Box {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.identity_detail_more_cd),
                            tint = WeiboOrange,
                            modifier = Modifier.size(24.dp)
                        )
                        DropdownMenu(
                            expanded = titleMenuExpanded,
                            onDismissRequest = { titleMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.identity_delete_title),
                                        color = Color.Red
                                    )
                                },
                                onClick = {
                                    titleMenuExpanded = false
                                    showDeleteIdentityDialog = true
                                }
                            )
                        }
                    }
                }
            } else {
                null
            },
            onRightIconClick = if (identityId > 0) {
                { titleMenuExpanded = !titleMenuExpanded }
            } else {
                null
            }
        )

        if (showDeleteIdentityDialog && identity != null) {
            val del = identity!!
            AlertDialog(
                onDismissRequest = { showDeleteIdentityDialog = false },
                title = { Text(stringResource(R.string.identity_delete_title)) },
                text = { Text(stringResource(R.string.identity_delete_message, del.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    app.repository.deleteIdentity(del)
                                }
                                showDeleteIdentityDialog = false
                                onBack()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.delete), color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteIdentityDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        Divider(thickness = 0.5.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val displayName =
                            (if (isEditing) name else identity?.name ?: name).ifBlank { "?" }
                        val displayCustom: String? = when {
                            !isEditing -> identity?.customAvatarUri
                            pendingAvatarUri != null -> pendingAvatarUri.toString()
                            preferCustomAvatar -> identity?.customAvatarUri
                            else -> null
                        }
                        val displayRes =
                            if (isEditing) avatarResName else identity?.avatarResName ?: avatarResName
                        Avatar(
                            name = displayName,
                            color = Color(0xFF4A90D9),
                            size = 80.dp,
                            avatarResName = displayRes,
                            customAvatarUri = displayCustom
                        )

                        if (isEditing) {
                            Text(
                                text = stringResource(R.string.identity_tap_avatar),
                                fontSize = 12.sp,
                                color = GrayMiddle,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            if (isEditing) {
                item {
                    Text(
                        text = stringResource(R.string.select_avatar),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayDark,
                        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp)
                    )
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.identity_avatar_section_custom),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GrayDark
                            )
                            OutlinedButton(
                                onClick = {
                                    pickAvatarLauncher.launch(
                                        PickVisualMediaRequest(PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .semantics {
                                        contentDescription =
                                            context.getString(R.string.identity_avatar_from_gallery_cd)
                                    },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = WeiboOrange)
                            ) {
                                Text(stringResource(R.string.identity_avatar_from_gallery))
                            }
                            if (preferCustomAvatar &&
                                (pendingAvatarUri != null || !identity?.customAvatarUri.isNullOrBlank())
                            ) {
                                TextButton(
                                    onClick = {
                                        pendingAvatarUri = null
                                        preferCustomAvatar = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .semantics {
                                            contentDescription =
                                                context.getString(R.string.identity_avatar_clear_custom_cd)
                                        }
                                ) {
                                    Text(
                                        text = stringResource(R.string.identity_avatar_clear_custom),
                                        color = GrayMiddle
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.identity_avatar_section_sketch_male),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GrayDark,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(avatarSketchMalePresets) { resName ->
                                val selected =
                                    !preferCustomAvatar && avatarResName == resName
                                PresetAvatarRow(
                                    resName = resName,
                                    selected = selected,
                                    onSelect = {
                                        preferCustomAvatar = false
                                        pendingAvatarUri = null
                                        avatarResName = resName
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.identity_avatar_section_sketch_female),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GrayDark,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(avatarSketchFemalePresets) { resName ->
                                val selected =
                                    !preferCustomAvatar && avatarResName == resName
                                PresetAvatarRow(
                                    resName = resName,
                                    selected = selected,
                                    onSelect = {
                                        preferCustomAvatar = false
                                        pendingAvatarUri = null
                                        avatarResName = resName
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    EditField(
                        label = stringResource(R.string.identity_label_name),
                        value = name,
                        onValueChange = { name = it },
                        placeholder = stringResource(R.string.identity_ph_name)
                    )
                }
                
                item {
                    EditField(
                        label = stringResource(R.string.identity_label_region),
                        value = nationality,
                        onValueChange = { nationality = it },
                        placeholder = stringResource(R.string.identity_ph_region)
                    )
                }
                
                item {
                    EditField(
                        label = stringResource(R.string.identity_label_job),
                        value = occupation,
                        onValueChange = { occupation = it },
                        placeholder = stringResource(R.string.identity_ph_job)
                    )
                }
                
                item {
                    Text(
                        text = stringResource(R.string.identity_label_gender),
                        fontSize = 14.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Gender.entries.forEach { g ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { gender = g }
                                ) {
                                    RadioButton(
                                        selected = gender == g,
                                        onClick = { gender = g },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = WeiboOrange
                                        )
                                    )
                                    Text(
                                        text = genderLabel(g),
                                        fontSize = 14.sp,
                                        color = GrayDark
                                    )
                                }
                            }
                        }
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = birthYear,
                            onValueChange = { birthYear = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text(stringResource(R.string.identity_label_birth)) },
                            placeholder = { Text(stringResource(R.string.identity_ph_birth)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = deathYear,
                            onValueChange = { deathYear = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text(stringResource(R.string.identity_label_death)) },
                            placeholder = { Text(stringResource(R.string.identity_ph_death)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                item {
                    EditField(
                        label = stringResource(R.string.identity_label_motto),
                        value = motto,
                        onValueChange = { motto = it },
                        placeholder = stringResource(R.string.identity_ph_motto)
                    )
                }
                
                item {
                    EditField(
                        label = stringResource(R.string.identity_label_work),
                        value = famousWork,
                        onValueChange = { famousWork = it },
                        placeholder = stringResource(R.string.identity_ph_work)
                    )
                }
                
                item {
                    EditField(
                        label = stringResource(R.string.identity_label_bio),
                        value = bio,
                        onValueChange = { bio = it },
                        placeholder = stringResource(R.string.identity_ph_bio),
                        minLines = 3
                    )
                }
                
                item {
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val picked =
                                            pendingAvatarUri.takeIf { preferCustomAvatar }
                                        val deleteCustom =
                                            !preferCustomAvatar &&
                                                !identity?.customAvatarUri.isNullOrBlank()
                                        val newIdentity = IdentityEntity(
                                            id = if (identityId > 0) identityId else 0,
                                            name = name.trim(),
                                            avatarResName = avatarResName,
                                            customAvatarUri = identity?.customAvatarUri,
                                            nationality = nationality,
                                            gender = gender,
                                            birthYear = birthYear.toIntOrNull(),
                                            deathYear = deathYear.toIntOrNull(),
                                            occupation = occupation,
                                            motto = motto,
                                            famousWork = famousWork,
                                            bio = bio,
                                            createdAt = identity?.createdAt
                                                ?: System.currentTimeMillis(),
                                            isActive = identity?.isActive ?: false
                                        )
                                        app.repository.saveIdentityWithAvatarOptions(
                                            identity = newIdentity,
                                            pickedAvatarUri = picked,
                                            deleteCustomAvatar = deleteCustom
                                        )
                                    }
                                    onBack()
                                }
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WeiboOrange
                        )
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            } else {
                identity?.let { i ->
                    item {
                        DetailItem(label = stringResource(R.string.identity_label_name), value = i.name)
                    }
                    if (i.nationality.isNotEmpty()) {
                        item {
                            DetailItem(
                                label = stringResource(R.string.identity_label_region),
                                value = i.nationality
                            )
                        }
                    }
                    item {
                        DetailItem(
                            label = stringResource(R.string.identity_label_gender),
                            value = genderLabel(i.gender)
                        )
                    }
                    if (i.occupation.isNotEmpty()) {
                        item {
                            DetailItem(
                                label = stringResource(R.string.identity_label_job),
                                value = i.occupation
                            )
                        }
                    }
                    if (i.birthYear != null) {
                        val years = if (i.deathYear != null) {
                            "${i.birthYear} - ${i.deathYear}"
                        } else {
                            "${i.birthYear} - "
                        }
                        item {
                            DetailItem(
                                label = stringResource(R.string.identity_label_years),
                                value = years
                            )
                        }
                    }
                    if (i.motto.isNotEmpty()) {
                        item {
                            DetailItem(
                                label = stringResource(R.string.identity_label_motto),
                                value = i.motto
                            )
                        }
                    }
                    if (i.famousWork.isNotEmpty()) {
                        item {
                            DetailItem(
                                label = stringResource(R.string.identity_label_work),
                                value = i.famousWork
                            )
                        }
                    }
                    if (i.bio.isNotEmpty()) {
                        item {
                            DetailItem(
                                label = stringResource(R.string.identity_label_bio),
                                value = i.bio
                            )
                        }
                    }
                    
                    item {
                        Button(
                            onClick = { isEditing = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WeiboOrange
                            )
                        ) {
                            Text(stringResource(R.string.identity_edit))
                        }
                    }
                }
            }
            
            item { Divider(modifier = Modifier.padding(bottom = 32.dp)) }
        }
    }
}

@Composable
private fun genderLabel(gender: Gender): String = stringResource(
    when (gender) {
        Gender.MALE -> R.string.gender_male
        Gender.FEMALE -> R.string.gender_female
        Gender.OTHER -> R.string.gender_other
    }
)

@Composable
private fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = GrayMiddle,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = GrayLight) },
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp, 12.dp)
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = GrayMiddle
            )
            Text(
                text = value,
                fontSize = 15.sp,
                color = GrayDark,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
