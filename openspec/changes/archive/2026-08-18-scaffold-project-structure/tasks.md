## 1. Build skeleton

- [x] 1.1 Create branch `feat/scaffold-project-structure`
- [x] 1.2 Check latest stable Maven version on Maven Central; generate Maven wrapper (`mvn -N wrapper:wrapper`), verify `./mvnw --version`
- [x] 1.3 Write parent `pom.xml`: `spring-boot-starter-parent:4.1.0`, groupId `io.github.sudoitir`, artifactId `taraz`, packaging `pom`, modules `core`/`adapters`/`container`/`architecture-tests`, Java 21 release, properties (spotless 3.10.0, palantir 2.97.0, archunit 1.5.0), dependencyManagement for internal modules
- [x] 1.4 Configure in parent pom: spotless-maven-plugin (palantirJavaFormat 2.97.0, Spring-Framework `importOrder`, `removeUnusedImports`, `apply` bound to `compile` phase + `check` in CI profile), maven-enforcer-plugin (Java 21, Maven min version), and Error Prone 2.50.0 + NullAway 0.13.8 as javac annotation-processor plugins (JSpecify mode, `io.github.sudoitir.taraz` annotated packages) with `.mvn/jvm.config` javac `--add-exports`/`--add-opens`

## 2. Modules

- [x] 2.1 `core/pom.xml` — zero dependencies; `core/domain` package with `package-info.java` marker
- [x] 2.2 `adapters/pom.xml` — dep `core`; `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`; package markers for `adapters/driving` + `adapters/driven`; MapStruct 1.6.3 + Lombok 1.18.46 (+lombok-mapstruct-binding 0.2.0) wired via annotationProcessorPaths
- [x] 2.3 `container/pom.xml` — dep `adapters`; `liquibase-core`, `org.postgresql:postgresql` (runtime), `spring-boot-docker-compose` (optional), `spring-boot-maven-plugin`, Lombok (provided); test: `spring-boot-starter-test`, `org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:testcontainers-postgresql`
- [x] 2.4 `TarazApplication` in `io.github.sudoitir.taraz.container`; `application.yaml` (virtual threads on, `ddl-auto=none`, datasource/valkey from env, server port); empty Liquibase master `db/changelog/db.changelog-master.xml`
- [x] 2.5 `architecture-tests/pom.xml` + `LayerBoundariesTest` (ArchUnit JUnit5): `core` does not depend on Spring or `adapters`; driving adapters don't touch outbound ports; plain JUnit, no Spring context, no Docker

## 3. Infra and repo glue

- [x] 3.1 `compose.yaml` — `postgres:18`, `valkey/valkey:9`, `apache/kafka:4.3` with healthchecks and env interpolation; `.env.example` with placeholders; `.env` with dev values; add `.env` to `.gitignore`
- [x] 3.2 `justfile` — `test`, `build`, `format`, `run`, `up`, `down` (ADR-0004)
- [x] 3.3 Persian `README.md` with the 8 challenge-mandated sections, scaffold status marked honestly (docs-fa rules)
- [x] 3.4 `docs/adr/0029-spotless-palantir-format.md` — Persian ADR per template (Spotless + palantir, Spring import order, apply-on-compile, check-in-CI); plus binding-rule ADRs 0030 (Error Prone/NullAway), 0031 (Lombok/MapStruct), 0032 (CI-friendly versioning)

## 4. Verify and ship

- [x] 4.1 `./mvnw -B verify` green: all modules compile, spotless apply+check pass, ArchUnit test passes
- [x] 4.2 `just test` works; `docker compose config` valid; `docker compose up -d` healthy; `./mvnw -pl container spring-boot:run` boots against Postgres/Valkey, then down
- [ ] 4.3 Conventional commits on the branch; merge to `main`; push; confirm CI green
- [ ] 4.4 Archive the OpenSpec change
