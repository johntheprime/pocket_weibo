package com.pocketweibo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "identity_schedules",
    foreignKeys = [
        ForeignKey(
            entity = IdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["identityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("identityId")]
)
data class IdentityScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val identityId: Long,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String = "",
    val enabled: Boolean = true
)
