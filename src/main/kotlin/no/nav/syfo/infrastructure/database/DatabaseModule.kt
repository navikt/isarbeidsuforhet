package no.nav.syfo.infrastructure.database

import no.nav.syfo.isLocal
import no.nav.syfo.isDevOrProd

lateinit var applicationDatabase: DatabaseInterface
fun databaseModule(
    databaseEnvironment: DatabaseEnvironment
) {
    isLocal {
        applicationDatabase = Database(
            DatabaseConfig(
                jdbcUrl = "jdbc:postgresql://localhost:5432/isarbeidsuforhet_dev",
                password = "password",
                username = "username",
            )
        )
    }

    isDevOrProd {
        applicationDatabase = Database(
            DatabaseConfig(
                jdbcUrl = databaseEnvironment.jdbcUrl(),
                username = databaseEnvironment.username,
                password = databaseEnvironment.password,
            )
        )
    }
}
