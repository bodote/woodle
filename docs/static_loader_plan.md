# Static Loader Plan for Poll Pages

## Ziel

Nutzer sollen bei langsamen Cold Starts **sofort** eine Seite sehen, statt 10-20 Sekunden auf die erste Antwort zu warten.

Gewünschter Flow:

1. Einstieg über `/poll/static/{pollId}`.
2. Diese URL liefert immer sofort eine statische Loader-Seite aus S3/CloudFront.
3. Loader-Seite pollt im Hintergrund die dynamische Readiness-URL.
4. Sobald Backend bereit ist, lädt HTMX den dynamischen Poll-Inhalt und ersetzt den Loader-Inhalt auf derselben URL.
5. Es erfolgt **kein Redirect** auf eine dynamische URL.

Der Text auf der Loader-Seite:

`Bitte noch ein bischen Geduld, wir laden gerade die Umfrage`

## Architektur im aktuellen Projekt

- CloudFront in `infra/template.yaml` hat aktuell ein breites Behavior `PathPattern: /poll*` auf `api-origin`.
- Statische Assets liegen in `src/main/resources/static` und werden über `web-bucket-origin` ausgeliefert.
- Poll-Views werden heute über `@GetMapping("/poll/{pollId}")` in `PollViewController` geliefert.

Für den neuen Flow brauchen wir eine saubere Trennung zwischen statischem Einstieg und dynamischer Poll-Route.

## Umsetzungsplan

## Pflicht-Reihenfolge (muss exakt so erfolgen)

1. Automatisierte Tests erstellen (zuerst, inklusive erstem failenden Test).
2. Funktionalität implementieren.
3. Test-Coverage prüfen.
4. Falls nötig: Tests/Code nachbessern, bis Coverage und Qualität passen.
5. Lokalen Server starten und mit Playwright verifizieren.
6. Bei Fehlern lokal fixen und erneut mit Playwright prüfen, bis es funktioniert.
7. Mit Native Image deployen.
8. Erneut mit Playwright gegen die deployed AWS-URL testen.
9. Bei Fehlern fixen und redeployen, bis es funktioniert.

## 1. URL-Design und Routing festlegen

1. Öffentliche Einstieg-URL: `/poll/static/{pollId}`.
2. Dynamische Fragment-URL: `/poll/dynamic/{pollId}/fragment` (liefert nur den in die statische Seite einzusetzenden Inhalt).
3. Readiness-URL: `/poll/dynamic/{pollId}/ready` (leichtgewichtig, keine Template-Renderlogik).
4. Optional: vollständige dynamische Seite `/poll/dynamic/{pollId}` nur intern/kompatibel behalten, aber nicht als Ziel-URL für Nutzer verwenden.

Begründung: `CloudFront` kann `/poll/static/*` gezielt auf S3 routen, während `/poll/dynamic/*` bei API bleibt.

## 2. CloudFront/SAM Template anpassen

Datei: `infra/template.yaml`

1. Neues CacheBehavior für `PathPattern: /poll/static/*` auf `web-bucket-origin`.
2. Bestehendes API-Behavior von `/poll*` auf `/poll/dynamic/*` eingrenzen.
3. Optional: Legacy-Weiterleitungen in App einbauen (siehe Schritt 3), damit alte Links `/poll/{id}` nicht brechen.
4. Caching für Loader-Ressource kontrollieren:
   - HTML Loader idealerweise kurz oder `no-store`.
   - CSS/JS weiterhin cachebar.

Akzeptanz auf Infra-Ebene:

- Request auf `/poll/static/<uuid>` trifft nie Lambda.
- Request auf `/poll/dynamic/<uuid>/ready` und `/poll/dynamic/<uuid>/fragment` geht an API/Lambda.

## 3. Backend-Routen ergänzen (kompatibel migrieren)

Datei: `src/main/java/io/github/bodote/woodle/adapter/in/web/PollViewController.java`

1. Dynamische Fragment-Route ergänzen:
   - `GET /poll/dynamic/{pollId}/fragment`
   - Gibt das HTML-Fragment zurück, das den Loader-Bereich ersetzt.
2. Readiness-Endpoint ergänzen, z. B.:
   - `GET /poll/dynamic/{pollId}/ready`
   - `200 OK`, wenn Poll lesbar ist.
   - `404`, wenn Poll nicht existiert.
   - `503`, wenn Infrastruktur noch nicht bereit ist bzw. Timeout.
3. Legacy-Route `/poll/{pollId}` per `302` auf `/poll/static/{pollId}` leiten.

Empfehlung: `/poll/static/{pollId}` ist die kanonische Nutzer-URL.

## 4. Statische Loader-Seite erstellen

Neue Datei (Vorschlag):

- `src/main/resources/static/poll/static/index.html`

Zusätzliche Pfadstrategie:

- Entweder exakt ein statisches HTML pro Pattern bereitstellen (mit CloudFront Function URL-Rewrite)
- oder einfacher: `/poll/static/{id}` auf ein fixes Objekt umschreiben (CloudFront Function), z. B. `/poll/static/loader.html`.

Da S3 keine Path-Parameter kennt, ist für "beliebiges `{id}` auf dieselbe Datei" ein Rewrite nötig.

Inhalt Loader-Seite:

1. Gleiche Basisoptik wie restliche Seiten:
   - gleiche CSS-Datei `/css/app.css`
   - gleiche Struktur mit `page-shell`, Header und Card-Komponenten.
2. Sichtbarer Spinner (CSS-Animation, barrierearm).
3. Text exakt:
   - `Bitte noch ein bischen Geduld, wir laden gerade die Umfrage`
4. HTMX-basiertes Verhalten:
   - `pollId` aus URL lesen.
   - Ein Container (z. B. `#poll-content`) enthält initial Spinner + Wartetext.
   - HTMX pollt alle 1-2 Sekunden auf `/poll/dynamic/{pollId}/ready`.
   - Sobald `ready` = `200`, triggert HTMX einen Request auf `/poll/dynamic/{pollId}/fragment`.
   - HTMX ersetzt den Inhalt von `#poll-content` mit dem dynamischen Fragment.
   - Bei `404` klare Fehlermeldung statt Endlosspinner.
   - Nach Max-Wartezeit (z. B. 60s) Hinweis mit "erneut versuchen".

## 5. Gleiche Layout-Sprache sicherstellen

Dateien:

- `src/main/resources/static/css/app.css`
- neue Loader-HTML Datei

1. Loader nutzt vorhandene Design-Tokens/Komponenten statt separatem Theme.
2. Nur minimale CSS-Erweiterung für Spinner + Status-Text ergänzen.
3. Keine visuelle Abweichung vom Woodle-Look (Farben, Typografie, Card-Layout).

## 6. TDD-Umsetzungsschritte (projektkonform)

Reihenfolge strikt klein und inkrementell:

1. **Erster failender API-Test** für neues Verhalten:
   - z. B. in `PollViewControllerTest` oder neuem Test für `/poll/dynamic/{pollId}/ready`.
   - Assertions schlagen initial fehl, Kompilierung bleibt grün.
2. Implementierung für Readiness + Fragment-Route.
3. Tests grün machen.
4. Infra-Test erweitern:
   - `CloudFrontSingleDomainRoutingTest` um neue PathPatterns (`/poll/static/*`, `/poll/dynamic/*`).
5. HTML-Funktionstests ergänzen:
   - Strukturtest für Loader-Elemente (Spinner, Text, HTMX-Attribute).
   - Optional Playwright-Smoke: Loader erscheint zuerst, danach In-Place-Ersatz durch dynamischen Inhalt.

Hinweis: Für diese Änderung primär API-/WebMvc-Tests und Infra-Template-Tests priorisieren.

## 6.1 Verbindlicher Delivery-Workflow

Die Umsetzung erfolgt in dieser festen Reihenfolge:

1. Automatisierte Tests zuerst erstellen (mindestens erster failender API-Test gemäß TDD-Gate).
2. Implementierung der Funktionalität.
3. Gesamte Tests ausführen und Coverage prüfen.
4. Falls Coverage/Qualität nicht ausreicht: gezielt Tests ergänzen und Code nachschärfen.
5. Lokalen Server starten und mit Playwright den End-to-End-Flow prüfen.
6. Bei Problemen lokal iterativ fixen, bis Playwright lokal stabil grün ist.
7. Native Image deployen (`DEPLOY_RUNTIME=native` Pfad).
8. Nach Deployment erneut mit Playwright gegen die AWS-URL prüfen.
9. Bei Problemen erneut fixen und redeployen, bis der Flow in AWS stabil funktioniert.

## 7. CloudFront URL-Rewrite für statische ID-Route

Da `/poll/static/{id}` immer dieselbe Datei liefern soll, braucht es einen Rewrite am Edge.

Option A (empfohlen): CloudFront Function (Viewer Request)

1. Wenn Pfad auf `/poll/static/<uuid>` matcht, dann URI intern auf `/poll/static/loader.html` setzen.
2. Querystring unverändert lassen.
3. Für alle anderen Pfade unverändert durchreichen.

Option B: Lambda@Edge (nur falls Function nicht reicht).

Akzeptanz:

- Jeder beliebige `/poll/static/<uuid>` liefert dasselbe Loader-HTML.
- JavaScript erhält die originale URL inkl. Poll-ID.

## 8. Backward Compatibility und Link-Erzeugung

1. Alle neu erzeugten Teilnehmer-Links auf `/poll/static/{pollId}` umstellen.
2. Admin-Link ebenfalls auf statische URL umstellen:
   - Statt dynamischer Ziel-URL ebenfalls statische Loader-URL verwenden.
3. E-Mail an den Admin so anpassen, dass sie immer den statischen Link enthält.
4. Bestehende geteilte Links `/poll/{pollId}` weiter unterstützen (Redirect auf `/poll/static/{pollId}`).

Betroffene Stelle prüfen:

- URL-Bildung im Controller/Template (`participantShareUrl`, `adminShareUrl` in `PollViewController`).
- E-Mail-Erstellung im Outbound-Email-Flow (`PollCreatedEmail`/Sender-Implementierungen).

## 9. Deployment- und Smoke-Checks

Vor Deployment (lokal):

1. `./gradlew test` und `./gradlew check` ausführen.
2. Coverage prüfen und bei Bedarf verbessern (`./gradlew jacocoTestReport`).
3. Lokalen Server starten.
4. Playwright-Szenario lokal ausführen:
   - `/poll/static/{id}` rendert sofort Loader.
   - Wartetext sichtbar.
   - Bei Readiness wird Inhalt per HTMX ersetzt.
   - Keine Redirect-URL auf `/poll/dynamic/...` im Browser.
5. Bei Fehlschlag: fixen und Playwright lokal erneut ausführen, bis grün.

Nach Deployment in AWS (native):

1. Native Deployment durchführen.
2. Aufruf `https://qs.woodle.click/poll/static/<bestehende-id>`:
   - Loader erscheint sofort.
3. Während kaltem Start:
   - Loader bleibt sichtbar, kein leerer Browser-Tab.
4. Sobald App warm ist:
   - HTMX ersetzt Loader-Inhalt durch dynamischen Poll-Inhalt auf derselben URL.
5. Nicht existierende Poll-ID:
   - sauberer Fehlerpfad (kein endloser Spinner).
6. Links zum Teilen:
   - `Admin-Link` zeigt `/poll/static/{id}`.
   - `Link für Teilnehmende` zeigt `/poll/static/{id}`.
7. Admin-E-Mail:
   - Enthält den statischen Link `/poll/static/{id}`.
8. Playwright-Szenario gegen AWS ausführen.
9. Bei Fehlschlag: fixen, redeployen, Playwright erneut ausführen, bis grün.

Zusätzlich bestehende Guardrail-Smoke-Checks für Poll-Bearbeitung weiter ausführen.

## 10. Done-Kriterien

Die Umsetzung gilt als fertig, wenn:

1. `/poll/static/{id}` immer sofort eine Seite rendert.
2. Loader-Seite nutzt Woodle-Layout und zeigt Spinner + Text
   `Bitte noch ein bischen Geduld, wir laden gerade die Umfrage`.
3. Kein Redirect auf dynamische URL erfolgt.
4. Loader-Inhalt wird per HTMX durch dynamischen Content ersetzt, sobald Readiness positiv ist.
5. Beide Share-Links (`Admin-Link`, `Link für Teilnehmende`) sind statische Links.
6. Admin-E-Mail enthält den statischen Link.
7. CloudFront-Routing ist testabgedeckt (`CloudFrontSingleDomainRoutingTest`).
8. Web/API-Verhalten ist testabgedeckt (WebMvc + ggf. E2E-Smoke).
9. `./gradlew check` läuft grün.
10. Coverage ist geprüft und bei Bedarf durch zusätzliche Tests verbessert.
11. Playwright lokal grün.
12. Playwright nach Native-Deployment in AWS grün.

## Geplante Dateiänderungen (bei Umsetzung)

- `infra/template.yaml`
- `src/main/java/io/github/bodote/woodle/adapter/in/web/PollViewController.java`
- `src/main/resources/static/poll/static/loader.html` (oder gleichwertiger statischer Pfad)
- `src/main/resources/static/css/app.css`
- `src/test/java/io/github/bodote/woodle/infra/CloudFrontSingleDomainRoutingTest.java`
- `src/test/java/io/github/bodote/woodle/adapter/in/web/...` (neue/erweiterte Tests für dynamic/ready)
- `src/main/java/io/github/bodote/woodle/application/port/out/PollCreatedEmail.java`
- `src/main/java/io/github/bodote/woodle/adapter/out/email/...` (falls Link-Erzeugung dort erfolgt)
