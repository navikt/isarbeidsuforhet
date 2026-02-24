![Build status](https://github.com/navikt/isarbeidsuforhet/workflows/main/badge.svg?branch=main)

# isarbeidsuforhet

Applikasjon for vurdering av arbeidsuførhet (§8-4). § 8-4 er grunnvilkåret for rett til sykepenger.
Se "§ 8-4 Medisinske vilkår" på Navet for mer informasjon.

## Technologies used

* Docker
* Gradle
* Kafka
* Kotlin
* Ktor
* Postgres

##### Test Libraries:

* Mockk
* JUnit

#### Requirements

* JDK 21

### Build

Run `./gradlew clean shadowJar`

### Lint (Ktlint)

##### Command line

Run checking: `./gradlew --continue ktlintCheck`

Run formatting: `./gradlew ktlintFormat`

##### Git Hooks

Apply checking: `./gradlew addKtlintCheckGitPreCommitHook`

Apply formatting: `./gradlew addKtlintFormatGitPreCommitHook`

## Kafka

Kafka is not currently in place, but will be added in the near future.

## Contact

### For NAV employees

We are available at the Slack channel `#isyfo`.