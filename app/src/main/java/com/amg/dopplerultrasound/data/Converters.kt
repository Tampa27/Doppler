package com.amg.dopplerultrasound.data

import androidx.room.TypeConverter
import com.google.gson.Gson // Necesitarás Gson u otra librería JSON
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromStringList(value: String?): MutableList<String>? {
        if (value == null) {
            return null
        }
        val listType = object : TypeToken<MutableList<String>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun toStringList(list: MutableList<String>?): String? {
        if (list == null) {
            return null
        }
        return Gson().toJson(list)
    }
}