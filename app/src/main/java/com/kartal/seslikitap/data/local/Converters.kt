package com.kartal.seslikitap.data.local

import androidx.room.TypeConverter
import com.kartal.seslikitap.domain.model.NarratorGender

class Converters {

    @TypeConverter
    fun narratorGenderToString(value: NarratorGender): String = value.name

    @TypeConverter
    fun stringToNarratorGender(value: String): NarratorGender =
        runCatching { NarratorGender.valueOf(value) }.getOrDefault(NarratorGender.NEUTRAL)
}
