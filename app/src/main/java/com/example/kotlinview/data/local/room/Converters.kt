package com.example.kotlinview.data.local.room

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String =
        list?.joinToString(separator = "||") ?: ""

    @TypeConverter
    fun toStringList(serialized: String?): List<String> =
        serialized?.takeIf { it.isNotEmpty() }?.split("||") ?: emptyList()
}
