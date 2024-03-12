package no.nav.syfo.domain

@JvmInline
value class JournalpostId(val value: String) {
    init {
        if (value.toDoubleOrNull() == null) {
            throw IllegalArgumentException("Value is not a valid JournalpostId")
        }
    }
}
