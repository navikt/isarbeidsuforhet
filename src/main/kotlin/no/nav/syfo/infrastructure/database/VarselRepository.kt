package no.nav.syfo.infrastructure.database

import no.nav.syfo.application.IVarselRepository
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.model.UnpublishedVarsel
import no.nav.syfo.util.nowUTC
import java.sql.SQLException
import java.util.UUID

class VarselRepository(private val database: DatabaseInterface) : IVarselRepository {
    override fun getUnpublishedVarsler(): List<UnpublishedVarsel> = database.connection.use { connection ->
        connection.prepareStatement(GET_UNPUBLISHED_VARSEL).use {
            it.executeQuery().toList {
                UnpublishedVarsel(
                    personident = PersonIdent(getString("personident")),
                    varselUuid = UUID.fromString(getString("uuid")),
                    journalpostId = getString("journalpost_id")
                )
            }
        }
    }

    override fun setPublished(varsel: UnpublishedVarsel) {
        val now = nowUTC()
        database.connection.use { connection ->
            connection.prepareStatement(SET_VARSEL_PUBLISHED).use {
                it.setObject(1, now)
                it.setObject(2, now)
                it.setString(3, varsel.varselUuid.toString())
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
            connection.commit()
        }
    }

    companion object {
        private const val GET_UNPUBLISHED_VARSEL =
            """
                SELECT vu.personident, v.uuid, v.journalpost_id FROM varsel v
                INNER JOIN vurdering vu
                ON v.vurdering_id = vu.id
                WHERE v.journalpost_id IS NOT NULL AND v.published_at IS NULL
            """

        private const val SET_VARSEL_PUBLISHED =
            """
                 UPDATE varsel
                 SET published_at = ?, updated_at = ?
                 WHERE uuid = ?
            """
    }
}
