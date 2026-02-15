# Test-Strategie

## Kontext und Performance
- Spring testet mit einem **Context-Cache**; gleiche Konfigurationen werden wiederverwendet, was Tests beschleunigt.
- Vermeide unnötige Kontext-Neuladeschritte; `@DirtiesContext` leert den Cache und macht Folgetests langsamer.
- Tests sollten möglichst in einem Prozess laufen, damit der Context-Cache greift.

## Integrationstest (einzige @SpringBootTest-Klasse)
- Es soll **genau eine** Klasse mit `@SpringBootTest` geben.
- Diese Klasse enthält **alle Integrationstests** und **auch die End-to-End-Tests (Playwright)**.
- Bei vielen Tests: Gruppierung mit `@Nested` und inneren Klassen.
- **Namensmuster:** `.*IT.java`
- **Charakter:** langsam (voller Stack, E2E inklusive)
- Wenn ein echter HTTP-Server benötigt wird, `@SpringBootTest(webEnvironment = RANDOM_PORT)` verwenden.
- Den Port mit `@LocalServerPort` injizieren.

## Standard: @WebMvcTest
- Die meisten Tests sind `@WebMvcTest`.
- **Namensmuster:** `.*Test.java`
- Externe Abhängigkeiten (Datenbank, S3, externe Services) werden gemockt.
- Ziel: schnelle Ausführung.
- Fokus: **Spezifikation/Verhalten**, nicht Implementierungsdetails.
- Diese Tests sollen den **Großteil der Test-Coverage** liefern.
- Test-Slices schließen bestimmte `@Configuration`-Klassen aus; benötigte Konfigurationen gezielt ergänzen.
- Für HTML-Seiten **keine** Tests für Farben, Schriftgrößen oder Layout-Details.
- Stattdessen prüfen wir:
  - ob die **erforderlichen HTML-Elemente** vorhanden sind,
  - ggf. deren **Reihenfolge** auf der Seite,
  - und deren **Funktion/Verhalten**.
- **Ausnahme:** Bei **reinen Layout-/CSS-Änderungen** (z. B. Abstände, Ausrichtung, Farben, Typografie),
  die **keine** Verhaltensänderung und **keine** Änderung der HTML-Struktur/DOM-Verträge verursachen,
  sind ausnahmsweise **keine automatisierten Tests** erforderlich.

## Unit Tests (Ausnahme)
- Nur wenn Coverage **nicht praktikabel** mit `@WebMvcTest` erreichbar ist.
- Dürfen bei Bedarf Implementierungsdetails testen.
- Schnell, aber **stärker an die Implementierung gebunden**.
- Soll es **so wenig wie möglich** geben.

## Context-Hygiene
- `@DirtiesContext` nur verwenden, wenn ein Test den Application Context mutiert; sonst vermeiden.
- Wenn `@DirtiesContext` nötig ist, bewusst einsetzen (klare Begründung im Test).

## Parallelität
- Parallele Ausführung kann instabile Tests verursachen, besonders bei gemeinsamem Kontext oder Shared Resources.
- Tests, die Context mutieren oder Shared Resources verändern, **nicht parallel** laufen lassen.

## Testcontainers (Integration)
- Für echte Backends (z. B. DB, S3-Emulator) Testcontainers verwenden.
- Mit `@Testcontainers` und `@Container` definieren.
- Wenn möglich `@ServiceConnection` nutzen, damit Spring Boot die Verbindung automatisch konfiguriert.

## Test-Slices (Auswahl)
- Web: `@WebMvcTest`
- Data: `@DataJpaTest`, `@DataJdbcTest`, `@DataMongoTest`, usw.
- Für echte HTTP-Endpunkte: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@LocalServerPort`

## Test-Konfiguration
- Für test-spezifische Beans eine `@TestConfiguration` nutzen (zusätzlich zur Haupt-Konfiguration).

## Coverage-Ziel
- Wir streben **95% Testabdeckung** an.
- Coverage wird **regelmäßig überprüft**.

## Referenzen
- [Spring Boot: Testing Spring Boot Applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)
- [Spring Boot: Test Slices](https://docs.spring.io/spring-boot/appendix/test-auto-configuration/slices.html)
- [Spring Boot: Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Spring Framework: Context Caching](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html)
- [Spring Framework: @DirtiesContext](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-dirtiescontext.html)
