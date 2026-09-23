package com.tuempresa.autodialer.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromCallState(value: CallState): String = value.name

    @TypeConverter
    fun toCallState(value: String): CallState = CallState.valueOf(value)

    @TypeConverter
    fun fromCallResult(value: CallResult?): String? = value?.name

    @TypeConverter
    fun toCallResult(value: String?): CallResult? = value?.let { CallResult.valueOf(it) }

    @TypeConverter
    fun fromAgendaItemType(value: AgendaItemType): String = value.name

    @TypeConverter
    fun toAgendaItemType(value: String): AgendaItemType = AgendaItemType.valueOf(value)

    @TypeConverter
    fun fromAgendaItemStatus(value: AgendaItemStatus): String = value.name

    @TypeConverter
    fun toAgendaItemStatus(value: String): AgendaItemStatus = AgendaItemStatus.valueOf(value)
}
