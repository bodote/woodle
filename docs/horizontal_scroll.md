# Plan: Horizontales Scrollen nur für Terminspalten in der Teilnehmeransicht

## Zusammenfassung
Die bestehende Teilnehmeransicht bleibt als eine einzige HTMX-kompatible Tabelle erhalten. Statt die Tabelle in mehrere Teilbereiche aufzuteilen, wird die aktuelle Struktur so angepasst, dass nur der Terminbereich horizontal scrollt, während die linke Spalte `Teilnehmende` und die rechte Spalte `Bearbeiten` per CSS `position: sticky` immer sichtbar bleiben. Das ist die kleinste und robusteste Änderung, weil alle bestehenden HTMX-Row-Replacements weiter auf derselben Tabellenstruktur arbeiten.

## Geplante Änderungen
- TDD zuerst in `PollParticipantViewTest`:
  - Einen neuen API/View-Test mit vielen Terminspalten anlegen, der absichtlich zunächst fehlschlägt.
  - Der Test soll das neue Markup absichern:
    - Scroll-Container für die Teilnehmer-Tabelle vorhanden.
    - Sticky-Klassen/Attribute für linke Namensspalte und rechte Bearbeiten-Spalte vorhanden.
    - Header-Platzhalterzellen in den oberen Kopfzeilen ebenfalls als linke/rechte Sticky-Zellen markiert.
    - Add-Row, bestehende Teilnehmerzeilen, Edit-Fragment und Summary-Zeile bleiben im gleichen Tabellenmodell nutzbar.
- Template-Anpassung in `src/main/resources/templates/poll/view.html`:
  - Den bestehenden `votes-table-wrap` beibehalten und semantisch als horizontalen Scroll-Container für die Tabelle verwenden.
  - Den Kopfzeilen links und rechts explizite Sticky-Hilfsklassen geben, auch für die heute leeren `th` in Zeile 2 und 3.
  - Die mittleren Terminspalten unverändert durch `th:each` rendern, damit HTMX und Server-Rendering unverändert bleiben.
- Fragment-Anpassungen:
  - `poll/participant-row.html`, `poll/participant-row-edit.html` und `poll/summary-row.html` so angleichen, dass linke Namens-/Summenspalte und rechte Aktions-/Leerzelle dieselben Sticky-Klassen tragen wie die Hauptansicht.
  - Keine Änderung an HTMX-Endpunkten, `hx-get`, `hx-post`, `hx-target` oder `hx-swap`; nur Klassen/Markup für Layout-Stabilität ergänzen.
- CSS-Anpassung in `src/main/resources/static/css/app.css`:
  - `votes-table-wrap` als horizontalen Scroll-Container belassen.
  - Tabelle auf `width: max-content` / `min-width: 100%` behalten, damit nur bei Bedarf horizontal gescrollt wird.
  - Linke Spalte (`Teilnehmende`, Teilnehmername, Summenslabel, Add-Row-Name) mit `position: sticky; left: 0; z-index: ...; background: ...` fixieren.
  - Rechte Spalte (`Bearbeiten`, Buttons, rechte Leerzelle in Footer/Header) mit `position: sticky; right: 0; z-index: ...; background: ...` fixieren.
  - Für Sticky-Spalten klare Hintergrundfarben und Trennkanten/Schatten ergänzen, damit die scrollenden Terminspalten optisch darunter durchlaufen, ohne Lesbarkeitsprobleme.
  - Mobile-Regel bei `max-width: 740px` anpassen: kein `display: block` direkt auf `.votes-table`, weil das Sticky-Verhalten an der echten Tabelle hängen soll; horizontales Scrollen bleibt am Wrapper.
  - Selektbreiten der Terminzellen so belassen, dass die Terminspalten ihre natürliche Breite haben und nur der Mittelteil scrollt.

## Öffentliche Interfaces / Verhalten
- Keine Änderung an URLs, HTMX-Verträgen, Controllern oder DTOs.
- HTML-Struktur der Teilnehmeransicht ändert sich nur insofern, dass zusätzliche CSS-Klassen für Sticky-Links/Rechts-Spalten und Scroll-Container-Absicherung eingeführt werden.
- HTMX-Fragmentantworten bleiben weiterhin vollständige `<tr>`-Fragmente innerhalb derselben Tabelle.

## Testplan
- Neuer fehlschlagender MockMvc-Test für Teilnehmeransicht mit vielen Optionen:
  - prüft Scroll-Wrapper und Sticky-Klassen in Header, Body und Footer.
  - prüft, dass `Teilnehmende` und `Bearbeiten` weiterhin im HTML vorhanden und separat markiert sind.
- Bestehende View-Tests grün halten:
  - normale Teilnehmeransicht.
  - Edit-Row-Fragment.
  - gruppierte Intraday-Header.
- Browser-/E2E-Validierung:
  - Teilnehmeransicht mit vielen Terminen in schmalem Viewport öffnen.
  - horizontal scrollen und prüfen, dass Terminspalten erreichbar und klickbar bleiben.
  - neue Zeile `Speichern`.
  - bestehende Zeile `Bearbeiten`, ändern, erneut `Speichern`.
  - dabei verifizieren, dass Name links und Bearbeiten/Speichern rechts sichtbar bleiben.
- Vor Abschluss `./gradlew check` ausführen.

## Annahmen
- Geltungsbereich ist nur die Teilnehmeransicht, nicht die Admin-Ansicht.
- Das gewünschte Verhalten soll für Desktop und kleine Viewports gelten; es gibt keinen separaten Mobile-Only-Layoutwechsel für diese Tabelle.
- Eine CSS-basierte Sticky-Lösung ist bevorzugt gegenüber einer Aufteilung in mehrere Tabellen, weil sie HTMX-Fragmente unverändert kompatibel hält und das Risiko deutlich kleiner ist.
