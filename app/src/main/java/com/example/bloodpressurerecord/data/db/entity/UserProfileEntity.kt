package com.example.bloodpressurerecord.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String?,
    val age: Int?,
    val gender: String?,
    val targetSystolic: Int?,
    val targetDiastolic: Int?,
    val updatedAt: Long
)
