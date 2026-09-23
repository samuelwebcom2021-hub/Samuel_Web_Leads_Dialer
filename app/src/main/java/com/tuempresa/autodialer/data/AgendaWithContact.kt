package com.tuempresa.autodialer.data

import androidx.room.Embedded
import androidx.room.Relation

data class AgendaWithContact(
    @Embedded val agendaItem: AgendaItemEntity,
    @Relation(
        parentColumn = "contactId",
        entityColumn = "id"
    )
    val contact: ContactEntity?
)
