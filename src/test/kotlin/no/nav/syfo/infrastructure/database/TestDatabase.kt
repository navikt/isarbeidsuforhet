package no.nav.syfo.infrastructure.database

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.util.*

class TestDatabase : DatabaseInterface {
    private val pg: EmbeddedPostgres = try {
        EmbeddedPostgres.start()
    } catch (e: Exception) {
        EmbeddedPostgres.builder().start()
    }

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    init {

        Flyway.configure().run {
            dataSource(pg.postgresDatabase).validateMigrationNaming(true).load().migrate()
        }
    }

    fun stop() {
        pg.close()
    }
}

fun TestDatabase.dropData() {
    val queryList = listOf(
        """
        DELETE FROM VURDERING
        """.trimIndent(),
        """
        DELETE FROM VARSEL
        """.trimIndent(),
        """
        DELETE FROM VARSEL_PDF
        """.trimIndent(),
    )

    this.connection.use { connection ->
        queryList.forEach { query ->
            connection.prepareStatement(query).execute()
        }
        connection.commit()
    }
}

private const val queryGetVurdering =
    """
        SELECT *
        FROM vurdering
        WHERE uuid = ?
    """

fun TestDatabase.getVurdering(
    uuid: UUID,
): PVurdering? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetVurdering).use {
            it.setString(1, uuid.toString())
            it.executeQuery()
                .toList { toPVurdering() }
                .firstOrNull()
        }
    }

private const val queryGetVarsel =
    """
        SELECT *
        FROM varsel
        WHERE uuid = ?
    """

fun TestDatabase.getVarsel(
    uuid: UUID,
): PVarsel? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetVarsel).use {
            it.setString(1, uuid.toString())
            it.executeQuery()
                .toList { toPVarsel() }
                .firstOrNull()
        }
    }

private const val queryGetVarselPdf =
    """
        SELECT *
        FROM varsel_pdf
        WHERE varsel_id = ?
    """

fun TestDatabase.getVarselPdf(
    varselId: Int,
): PVarselPdf? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetVarselPdf).use {
            it.setInt(1, varselId)
            it.executeQuery()
                .toList { toPVarselPdf() }
                .firstOrNull()
        }
    }

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")

    fun stop() {
    }
}
