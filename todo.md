# TODO: CloudFront CNAME-Konflikt im AWS-Deploy beheben

## Problem

Beim `sam deploy` für den Stack `woodle-dev` schlägt das Update der Resource `WebDistribution` fehl.

Fehler (CloudFormation/CloudFront):

- `Invalid request provided: One or more of the CNAMEs you provided are already associated with a different resource.`
- Statuscode: `409`
- Folge: Stack endet in `UPDATE_ROLLBACK_COMPLETE`.

## Auswirkungen

- Vollständige Deployments über `aws-deploy.sh` sind nicht stabil möglich.
- Infrastruktur-Änderungen an CloudFront/Domain-Routing werden nicht übernommen.
- Backend-Fixes mussten als Workaround direkt per `aws lambda update-function-code` ausgerollt werden.

## Vermutete Ursache

- Mindestens ein verwendeter Custom-Domain-Name (CNAME/Alias) ist bereits bei einer anderen CloudFront-Distribution registriert.

## Nächste Schritte

1. In AWS CloudFront alle Distributions prüfen und den kollidierenden CNAME identifizieren.
2. CNAME aus der falschen/alten Distribution entfernen (oder Distribution stilllegen).
3. DNS/Route53-Einträge gegen gewünschte Ziel-Distribution verifizieren.
4. Danach `DEPLOY_RUNTIME=native ./aws-deploy.sh` erneut ausführen.
5. Post-Deploy Smoke-Test durchführen:
   - `/poll/new` bis Schritt 3
   - Poll erstellen
   - Admin-View öffnen
   - "Link für Teilnehmende" und "Admin-Link" prüfen
   - Vote bearbeiten/speichern prüfen

## Erledigt, wenn

- `sam deploy` ohne Rollback durchläuft.
- CloudFront-Routing für Poll-Seiten stabil funktioniert.
- Share-Links auf AWS korrekt mit Protokoll + Hostname erscheinen.

---

# TODO: Test-Coverage verbessern (gemäß test-strategie.md)

## Ziel

- Test-Coverage gezielt erhöhen und dabei die Vorgaben aus `test-strategie.md` strikt einhalten.

## Leitplanken aus test-strategie.md (verbindlich)

- API-first testen: bevorzugt `@WebMvcTest` auf öffentlichen Endpunkten.
- Interne Implementierungsdetails nicht direkt testen, sofern API-Tests die Abdeckung erreichen.
- Externe Abhängigkeiten mocken, nicht interne Kollaboration übermocken.
- HTML funktional testen (Elemente/Verhalten), keine visuellen Layout-Assertions.
- Namenskonvention einhalten: schnelle Unit-Tests als `*Test.java`, langsame Integrationstests als `*IT.java`.
- Coverage-Ziele einhalten: 95% Instruction, 90% Branch.

## Nächste Schritte

1. Aktuellen Coverage-Stand ermitteln (`./gradlew test jacocoTestReport`).
2. Klassen/Branches mit niedriger Abdeckung identifizieren.
3. Fehlende API-Tests priorisiert ergänzen (`@WebMvcTest`), nur wo nötig interne Tests ergänzen.
4. Für jede Klasse mit Verhalten sicherstellen, dass eine dedizierte Testabdeckung vorhanden ist.
5. Abschließend Schwellen validieren (`./gradlew check`).

## Erledigt, wenn

- JaCoCo-Schwellen im Build erfüllt sind (Instruction >= 95%, Branch >= 90%).
- Neue Tests folgen der Struktur und den Regeln aus `test-strategie.md`.
