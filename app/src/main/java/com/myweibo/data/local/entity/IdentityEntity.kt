package com.myweibo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarColor: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)
