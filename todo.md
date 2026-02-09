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
