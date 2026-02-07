# Woodle Tech Stack & Code-Struktur

## Programmiersprache & Framework
- Java
- Spring Boot
- Thymeleaf als HTML-Template-Engine
- HTMX (https://htmx.org) für dynamische Elemente

## Laufzeit & Deployment
- Kein dauerhaft laufender Spring-Boot-Server.
- Deployment als AWS Lambda Functions, damit Kosten nur bei Nutzung entstehen.

## Build & Quality
- Build-Tool: Gradle (`build.gradle`).
- Code Coverage: JaCoCo ist im `build.gradle` zu konfigurieren und wird für Coverage-Reports und Coverage-Checks genutzt.
- Mutation Testing: Pitest ist im `build.gradle` zu konfigurieren (`gradle pitest`).

## Identifikation & Datenmodell
- Keine Userverwaltung.
- Jede neue Umfrage erzeugt eine UUID.
- Unter dieser UUID werden Stammdaten der Umfrage sowie spätere Auswahl/Antworten der Teilnehmenden gespeichert.
- Autor:innen-Zugriff erfolgt über eine zusätzliche Secret-Komponente in der Admin-URL (neben der UUID).

## URL-Pattern
- Neue Umfrage: `<sitename>/poll/new`
- Teilnehmer-Link: `<sitename>/poll/<UUID>`
- Admin-Link: `<sitename>/poll/<UUID>-<admin-secret>`
- `admin-secret` ist eine zufällige, URL-sichere Zeichenkette mit ca. 12 Zeichen (z. B. Base62).

## Persistenz
- Speicherung nicht in einer Datenbank.
- Speicherung in Amazon S3 (z. B. JSON-Dateien pro Umfrage-UUID).
- Für lokale Tests wird ein S3-kompatibler Server via Testcontainers genutzt.

## Datenlebenszyklus
- Umfragen in S3 werden nach dem Verfallsdatum vollständig gelöscht.

## Code-Struktur: Hexagonale Architektur

Ziel: klare Trennung von Fachlogik, Use Cases und technischen Adaptern. Es gibt keine Modulith-Module; die Struktur ist rein hexagonal.

### Zielstruktur (Root-Paket)

```
io.github.bodote.woodle
├── application
│   ├── port
│   │   ├── in
│   │   └── out
│   └── service
├── domain
│   ├── model
│   ├── event
│   └── exception
├── adapter
│   ├── in
│   │   └── web
│   └── out
│       ├── persistence
│       └── integration
└── config
```

### Bedeutung der Pakete
- `domain`: Fachmodell, Invarianten, Value Objects, Domain-Events. Keine Abhängigkeit zu Spring oder technischen Details.
- `application`: Use Cases und Ports. Definiert die Eingangs- und Ausgangsschnittstellen des Systems.
- `adapter`: technische Umsetzung der Ports, z. B. Web-Controller, S3-Persistenz, externe Integrationen.
- `config`: Spring-Wiring und technische Konfiguration.

### Abhängigkeitsregeln
- `domain` hängt von nichts anderem im Projekt ab.
- `application` darf von `domain` abhängen, aber nie von `adapter` oder `config`.
- `adapter` darf von `application`, `domain` und `config` abhängen.
- `config` darf von allen Paketen abhängen; kein anderes Paket darf von `config` abhängen.

### Architekturtests (JUnit)
Wir prüfen die Hexagonal-Regeln mit einem JUnit-Architekturtest (ArchUnit), damit Abhängigkeiten messbar bleiben.
Abhängigkeit: `com.tngtech.archunit:archunit-junit5:latest`

Beispiel:

```java
@AnalyzeClasses(packages = "io.github.bodote.woodle")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainHasNoOutgoingDependencies =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..",
                            "..adapter..",
                            "..config.."
                    );
}
```

### Namenskonventionen
- `DTO` ist ausschließlich für öffentliche API-Transfer-Typen erlaubt.
- Interne/domain/application-Typen dürfen kein `DTO`-Suffix tragen.
- `Optional` wird nur als Rückgabetyp verwendet, nie als Methodenparameter.

## Lambda-Integration
- API Gateway + Spring Cloud Function.
- HTTP-Requests werden über Function-Adapter entgegengenommen, kein dauerhaft laufender Server.

## S3-Datenmodell (Empfehlung zur Review)

Ziel: genau **eine** JSON-Datei pro Umfrage. Der S3-Key ist die generierte UUID der Umfrage.

### S3-Keys
- `polls/{pollId}.json` (ein einziges Dokument mit Stammdaten, Optionen und Antworten)

### poll.json (Gesamtdokument)
```json
{
  "pollId": "UUID",
  "type": "date",
  "title": "string",
  "descriptionHtml": "string",
  "language": "de",
  "createdAt": "2026-02-06T12:00:00Z",
  "updatedAt": "2026-02-06T12:00:00Z",
  "author": {
    "name": "string",
    "email": "string"
  },
  "access": {
    "customSlug": "string|null",
    "passwordHash": "string|null",
    "resultsPublic": true,
    "adminToken": "string"
  },
  "permissions": {
    "voteChangePolicy": "ALL_CAN_EDIT|ONLY_OWN_CAN_EDIT|NONE_CAN_EDIT"
  },
  "notifications": {
    "onVote": true,
    "onComment": true
  },
  "resultsVisibility": {
    "onlyAuthor": false
  },
  "status": "DRAFT|OPEN|CLOSED",
  "expiresAt": "2026-03-10",
  "options": {
    "eventType": "ALL_DAY|INTRADAY",
    "durationMinutes": 180,
    "items": [
    {
      "optionId": "UUID",
      "date": "2026-02-10",
      "startTime": "11:00",
      "endTime": "14:00"
    }
    ]
  },
  "responses": [
    {
      "responseId": "UUID",
      "participantName": "string",
      "createdAt": "2026-02-06T12:00:00Z",
      "votes": [
        { "optionId": "UUID", "value": "YES|NO|IF_NEEDED" }
      ],
      "comment": "string|null"
    }
  ]
}
```

### Aufbewahrungsfrist
- `expiresAt` = letzter Termin + 4 Wochen.
- Danach vollständige Löschung aller `polls/{pollId}/**`-Objekte.

## Validierung
- Keine E-Mail- oder Passwort-Validierung erforderlich (Zugriff über UUID und Admin-Secret).

## Änderbarkeit der Optionen
- `endTime` wird bei intraday explizit gespeichert und nicht nachträglich aus `startTime + durationMinutes` abgeleitet.
- Autor:innen dürfen nach Erstellung Termine/Zeiten hinzufügen oder entfernen; bestehende Zeiten behalten ihre ursprünglich gespeicherten Werte.
