package com.pocketweibo.ui.screens.identity

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.data.local.entity.Gender
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val avatarOptions = listOf(
    "avatar_default", "avatar_scholar", "avatar_artist", "avatar_scientist",
    "avatar_writer", "avatar_female_scholar", "avatar_western", "avatar_chinese_scholar"
)

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
    
    val title = if (identityId == 0L) "添加身份" else if (isEditing) "编辑身份" else "身份详情"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
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
                        contentDescription = "返回",
                        tint = WeiboOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

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
                        IdentityAvatar(
                            resName = avatarResName,
                            size = 80
                        )
                        
                        if (isEditing) {
                            Text(
                                text = "点击选择头像",
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
                        text = "选择头像",
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
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(avatarOptions) { resName ->
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (avatarResName == resName) 3.dp else 0.dp,
                                            color = if (avatarResName == resName) WeiboOrange else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { avatarResName = resName },
                                    contentAlignment = Alignment.Center
                                ) {
                                    IdentityAvatar(
                                        resName = resName,
                                        size = 48
                                    )
                                    if (avatarResName == resName) {
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
                        }
                    }
                }
                
                item {
                    EditField(
                        label = "姓名",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "输入身份名称"
                    )
                }
                
                item {
                    EditField(
                        label = "国籍/地区",
                        value = nationality,
                        onValueChange = { nationality = it },
                        placeholder = "如：中国、美国、英国"
                    )
                }
                
                item {
                    EditField(
                        label = "职业",
                        value = occupation,
                        onValueChange = { occupation = it },
                        placeholder = "如：哲学家、文学家、科学家"
                    )
                }
                
                item {
                    Text(
                        text = "性别",
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
                                        text = when(g) {
                                            Gender.MALE -> "男"
                                            Gender.FEMALE -> "女"
                                            Gender.OTHER -> "其他"
                                        },
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
                            label = { Text("出生年份") },
                            placeholder = { Text("如：1960") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = deathYear,
                            onValueChange = { deathYear = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text("去世年份") },
                            placeholder = { Text("留空表示在世") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                item {
                    EditField(
                        label = "名言/座右铭",
                        value = motto,
                        onValueChange = { motto = it },
                        placeholder = "输入名人名言或人生信条"
                    )
                }
                
                item {
                    EditField(
                        label = "代表作",
                        value = famousWork,
                        onValueChange = { famousWork = it },
                        placeholder = "输入主要作品或成就"
                    )
                }
                
                item {
                    EditField(
                        label = "简介",
                        value = bio,
                        onValueChange = { bio = it },
                        placeholder = "简要介绍这个身份",
                        minLines = 3
                    )
                }
                
                item {
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val newIdentity = IdentityEntity(
                                        id = if (identityId > 0) identityId else 0,
                                        name = name,
                                        avatarResName = avatarResName,
                                        nationality = nationality,
                                        gender = gender,
                                        birthYear = birthYear.toIntOrNull(),
                                        deathYear = deathYear.toIntOrNull(),
                                        occupation = occupation,
                                        motto = motto,
                                        famousWork = famousWork,
                                        bio = bio,
                                        isActive = identity?.isActive ?: false
                                    )
                                    app.repository.insertIdentity(newIdentity)
                                }
                                onBack()
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
                        Text("保存")
                    }
                }
            } else {
                identity?.let { i ->
                    item {
                        DetailItem(label = "姓名", value = i.name)
                    }
                    if (i.nationality.isNotEmpty()) {
                        item { DetailItem(label = "国籍/地区", value = i.nationality) }
                    }
                    item {
                        DetailItem(
                            label = "性别",
                            value = when(i.gender) {
                                Gender.MALE -> "男"
                                Gender.FEMALE -> "女"
                                Gender.OTHER -> "其他"
                            }
                        )
                    }
                    if (i.occupation.isNotEmpty()) {
                        item { DetailItem(label = "职业", value = i.occupation) }
                    }
                    if (i.birthYear != null) {
                        val years = if (i.deathYear != null) {
                            "${i.birthYear} - ${i.deathYear}"
                        } else {
                            "${i.birthYear} - "
                        }
                        item { DetailItem(label = "生卒年份", value = years) }
                    }
                    if (i.motto.isNotEmpty()) {
                        item { DetailItem(label = "名言/座右铭", value = i.motto) }
                    }
                    if (i.famousWork.isNotEmpty()) {
                        item { DetailItem(label = "代表作", value = i.famousWork) }
                    }
                    if (i.bio.isNotEmpty()) {
                        item { DetailItem(label = "简介", value = i.bio) }
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
                            Text("编辑")
                        }
                    }
                }
            }
            
            item { Divider(modifier = Modifier.padding(bottom = 32.dp)) }
        }
    }
}

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
