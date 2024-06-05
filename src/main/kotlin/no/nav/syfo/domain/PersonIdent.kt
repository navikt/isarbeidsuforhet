package no.nav.syfo.domain

import java.util.*

data class PersonIdent(val value: String) {
    private val elevenDigits = Regex("^\\d{11}\$")

    init {
        if (!elevenDigits.matches(value)) {
            throw IllegalArgumentException("Value is not a valid PersonIdent")
        }
    }
}

fun PersonIdent.asProducerRecordKey(): String = UUID.nameUUIDFromBytes(value.toByteArray()).toString()
