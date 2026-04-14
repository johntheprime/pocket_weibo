package com.pocketweibo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Gender {
    MALE, FEMALE, OTHER
}

@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarResName: String = "avatar_default",
    val customAvatarUri: String? = null,
    val nationality: String = "",
    val gender: Gender = Gender.OTHER,
    val birthYear: Int? = null,
    val deathYear: Int? = null,
    val occupation: String = "",
    val motto: String = "",
    val famousWork: String = "",
    val bio: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)
