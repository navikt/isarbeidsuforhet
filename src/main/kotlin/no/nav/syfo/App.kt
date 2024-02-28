package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.application.service.ForhandsvarselService
import no.nav.syfo.infrastructure.azuread.AzureAdClient
import no.nav.syfo.infrastructure.database.applicationDatabase
import no.nav.syfo.infrastructure.database.databaseModule
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.pdfgen.VarselPdfService
import no.nav.syfo.infrastructure.pdl.PdlClient
import no.nav.syfo.infrastructure.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.wellknown.getWellKnown
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val environment = Environment()
    val logger = LoggerFactory.getLogger("ktor.application")

    val wellKnownInternalAzureAD = getWellKnown(
        wellKnownUrl = environment.azure.appWellKnownUrl
    )
    val azureAdClient = AzureAdClient(
        azureEnvironment = environment.azure
    )
    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlEnvironment = environment.clients.pdl,
    )
    val veilederTilgangskontrollClient =
        VeilederTilgangskontrollClient(
            azureAdClient = azureAdClient,
            clientEnvironment = environment.clients.istilgangskontroll
        )

    val pdfGenClient = PdfGenClient(
        pdfGenBaseUrl = environment.clients.isarbeidsuforhetpdfgen.baseUrl,
    )

    val varselPdfService = VarselPdfService(
        pdfGenClient = pdfGenClient,
        pdlClient = pdlClient,
    )

    lateinit var forhandsvarselService: ForhandsvarselService
    lateinit var vurderingRepository: IVurderingRepository

    val applicationEngineEnvironment =
        applicationEngineEnvironment {
            log = logger
            config = HoconApplicationConfig(ConfigFactory.load())
            connector {
                port = applicationPort
            }
            module {
                databaseModule(
                    databaseEnvironment = environment.database,
                )

                vurderingRepository = VurderingRepository(applicationDatabase)
                forhandsvarselService = ForhandsvarselService(
                    vurderingRepository = vurderingRepository,
                    varselPdfService = varselPdfService,
                )

                apiModule(
                    applicationState = applicationState,
                    database = applicationDatabase,
                    environment = environment,
                    wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                    veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                    forhandsvarselService = forhandsvarselService,
                )
            }
        }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        logger.info("Application is ready, running Java VM ${Runtime.version()}")
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment
    )

    Runtime.getRuntime().addShutdownHook(
        Thread { server.stop(10, 10, TimeUnit.SECONDS) }
    )

    server.start(wait = true)
}
