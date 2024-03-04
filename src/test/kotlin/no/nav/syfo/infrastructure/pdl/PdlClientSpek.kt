package no.nav.syfo.infrastructure.pdl

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import no.nav.syfo.infrastructure.clients.pdl.dto.PdlPerson
import no.nav.syfo.infrastructure.clients.pdl.dto.PdlPersonNavn
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PdlClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val pdlClient = PdlClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    describe(PdlClient::class.java.simpleName) {
        describe("Happy case") {
            it("returns person from pdl") {
                runBlocking {
                    val person = pdlClient.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    person.navn.size shouldBeEqualTo 1
                    person.navn[0].fornavn shouldBeEqualTo UserConstants.PERSON_FORNAVN
                }
            }

            it("returns fullname from person") {
                val pdlPerson = PdlPerson(
                    navn = listOf(
                        PdlPersonNavn(
                            fornavn = UserConstants.PERSON_FORNAVN,
                            mellomnavn = UserConstants.PERSON_MELLOMNAVN,
                            etternavn = UserConstants.PERSON_ETTERNAVN,
                        )
                    )
                )
                runBlocking {
                    val person = pdlClient.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    person.fullName shouldBeEqualTo pdlPerson.fullName
                }
            }

            it("returns full name when person has name with dashes") {
                runBlocking {
                    val fullname = pdlClient.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH).fullName
                    fullname shouldBeEqualTo UserConstants.PERSON_FULLNAME_DASH
                }
            }
        }

        describe("Unhappy case") {
            afterEachTest {
                clearAllMocks()
            }

            it("throws exception when person is missing name") {
                runBlocking {
                    assertFailsWith(RuntimeException::class) {
                        pdlClient.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME)
                    }
                }
            }

            it("throws exception when pdl has error") {
                runBlocking {
                    assertFailsWith(RuntimeException::class) {
                        pdlClient.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS)
                    }
                }
            }

            it("throws exception when AzureAdClient has error") {
                val azureAdMock = mockk<AzureAdClient>(relaxed = true)
                val pdlClientMockedAzure = PdlClient(
                    azureAdClient = azureAdMock,
                    pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
                )

                coEvery { azureAdMock.getSystemToken(any()) } returns null

                runBlocking {
                    assertFailsWith(RuntimeException::class) {
                        pdlClientMockedAzure.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    }
                }
            }
        }
    }
})
