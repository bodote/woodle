# Woodle — Umfrage erstellen (Schritt 1 von 3, Typ: Datum)

## Überblick
Diese Seite ist Schritt 1 von 3 zur Erstellung einer Datums‑Umfrage auf woodle. Der Fokus liegt auf Basis‑Metadaten der Umfrage (Autor, Kontakt, Titel, Beschreibung) sowie Benachrichtigungen. Die E‑Mail‑Adresse wird bereits beim Verlassen des Feldes auf ein plausibles Format geprüft und visuell markiert, wenn sie ungültig ist.

## Seitendaten
- URL: `https://nuudel.digitalcourage.de/create_poll.php?type=date`
- Titel: `Umfrage erstellen (Schritt 1 von 3) - woodle`
- Sprache: Standard `Deutsch` (per Sprach‑Dropdown auswählbar)

## Layout
- Kopfbereich mit Sprachwahl + OK‑Button, Logo/Link „woodle“, Seitentitel.
- Hauptformular mit Pflichtfeldern und optionalen Einstellungen.
- Call‑to‑Action‑Button für Schritt 2.
- Fußbereich mit Spendenhinweisen und FAQ/Links.

## Pflichtfelder
- **Ihr Name** (Textbox, Pflichtfeld)
- **Ihre E‑Mail‑Adresse** (Textbox, Pflichtfeld)
  - Sofortige Format‑Prüfung nach Verlassen des Feldes (HTMX, ohne Seiten‑Reload).
  - Ungültiges Format wird rot markiert und blockiert den Übergang zu Schritt 2.
- **Titel der Umfrage** (Textbox, Pflichtfeld)

## Beschreibung
- Feld „Beschreibung“ als einfache Textarea.

## Benachrichtigungen
- Checkbox: „Bei jeder neuen Wertung eine E‑Mail erhalten“
- Checkbox: „Bei jedem neuen Kommentar eine E‑Mail erhalten“

## Navigation / Aktionen
- Button: **„Weiter zum 2. Schritt“** (führt zur nächsten Seite der Umfrageerstellung)

## Footer/Links (nicht Teil des Formulars)
- Spendenaufruf „Digitalcourage Jetzt spenden“ mit Link.
- FAQ‑Bereich mit Links zu Datenschutz, Impressum, AVV, Onion‑Service, Newsletter etc.

## Offene Fragen / Nicht sichtbar in Schritt 1
- Detailregeln für gültige E‑Mail‑Formate (z. B. erlaubte Sonderzeichen) sind nicht sichtbar.
- Inhalte von Schritt 2 und 3 sind hier nicht Teil der Spezifikation.

# Woodle — Umfragedaten (Schritt 2 von 3, Typ: Datum)

## Überblick
Schritt 2 dient der Definition der eigentlichen Terminvorschläge. Zuerst wird abgefragt, ob es sich um ganze Tage handelt (mindestens ganztägig oder mehrtägig) oder um ein untertägiges Event. Mindestens zwei alternative Zeitpunkte sind erforderlich.

## Seitendaten
- URL: `https://nuudel.digitalcourage.de/create_date_poll.php`
- Titel: `Umfragedaten (2 von 3) - woodle`
- Sprache: Standard `Deutsch` (per Sprach-Dropdown auswählbar)

## Layout
- Kopfbereich wie in Schritt 1.
- Hauptbereich mit Event‑Typ‑Auswahl und dynamischem Formularbereich für Terminvorschläge.
- Dynamik per HTMX: Der Bereich unterhalb der Event‑Typ‑Frage wird ohne kompletten Seiten‑Reload ausgetauscht.
- Navigation mit „Weiter“.

## Erklärungstexte
- „Mindestens zwei alternative Zeitpunkte“ erforderlich.
- Hinweise zum Hinzufügen/Entfernen von Tagen.
- Hinweis: Uhrzeiten sind abhängig vom Event‑Typ.

## Event-Typ (neu)
- Frage: Handelt es sich um ein ganz‑ bzw. mehrtägiges Event?
- Optionen:
  - **Ganz-/Mehrtägig**: Terminvorschläge bestehen nur aus Tagen. **Keine Uhrzeiten, keine Dauer sichtbar.**
  - **Untertägig**: Terminvorschläge bestehen immer aus **Tag + Startzeit** je Eintrag. Zusätzlich gibt es ein globales Feld für die **Dauer**.

## Terminvorschläge (ganz-/mehrtägig, wiederholbar)
Standardmäßig zwei Blöcke sichtbar (Tag 1-2). Jeder Block enthält:
- Datums‑Eingabe („Tag X“) mit Placeholder `yyyy-mm-dd`.

## Terminvorschläge (untertägig, wiederholbar)
Zusätzlich zu den Tag‑Feldern gibt es ein globales Feld für die **Dauer** des Events (gilt für alle Varianten).
Jeder Block enthält:
- Datums‑Eingabe („Tag X“) mit Placeholder `yyyy-mm-dd`.
- Startzeit („Startzeit X“, z. B. `11:00`).
- Jeder zusätzliche Block ist eine weitere **Tag+Startzeit**‑Kombination.

## Globale Aktionen
- Buttons „Einen Tag hinzufügen/entfernen“ (ganz-/mehrtägig).
- Buttons „Termin hinzufügen/entfernen“ (untertägig).

## Navigation / Aktionen
- Button „Weiter“ (führt zu Schritt 3).

## Offene Fragen / Nicht sichtbar in Schritt 2
- Exakte Validierungslogik für Datum/Uhrzeit (z. B. Format‑Fehler) ist hier nicht sichtbar.

# Woodle — Abstimmungszeitraum und Bestätigung (Schritt 3 von 3)

## Überblick
Schritt 3 zeigt eine Zusammenfassung der Auswahlmöglichkeiten, definiert das automatische Löschdatum und bietet die finale Erstellung der Umfrage an.

## Seitendaten
- URL: `https://nuudel.digitalcourage.de/create_date_poll.php`
- Titel: `Abstimmungszeitraum und Bestätigung (3 von 3) - woodle`
- Sprache: Standard `Deutsch` (per Sprach-Dropdown auswählbar)

## Zusammenfassung der Auswahlmöglichkeiten
- Überschrift „Liste Ihrer Auswahlmöglichkeiten“.
- Liste der vorgeschlagenen Termine.
  - Ganz-/Mehrtägig: Datumsliste.
  - Untertägig: Datum **mit Uhrzeit** je Eintrag (z. B. `2026-02-10 09:00`).

## Löschdatum
- Hinweis: Umfrage wird **720 Tage nach dem letzten Termin** automatisch gelöscht.
- Option: „Tag der Löschung“ als Datumsfeld (Placeholder `yyyy-mm-dd`) mit vorbefülltem Datum.

## Bestätigungshinweise
- Hinweis auf automatische Weiterleitung zur Administrationsseite nach Bestätigung.
- Hinweis: Zwei E-Mails werden versendet (Teilnehmenden-Link und Admin-Link).

## Nach Erstellung (Admin-Seite)
- Direkt auf der Seite werden zwei kopierbare Links angezeigt:
  - Link für Teilnehmende (öffentlich).
  - Admin-Link für Änderungen.
- Beide Links werden als **vollständige absolute URL** angezeigt (inkl. Protokoll und Host), nicht nur als Pfad.
  - Beispiel AWS: `https://woodle.click/poll/<pollId>`
  - Beispiel lokal: `http://localhost:<port>/poll/<pollId>`
  - Für den Admin-Link entsprechend: `.../poll/<pollId>-<adminSecret>`
- Die URL-Bildung ist request-basiert:
  - lokal `http` + tatsächlicher lokaler Port (z. B. `8088`)
  - deployed auf AWS `https`
- Admin kann bestehende Termine direkt in der Liste per „Termin löschen“-Button entfernen.
  - Bei untertägigen Terminen erfolgt das Löschen **nach Datum + Uhrzeit**.
- Die Links werden als nicht-editierbarer Text mit „Kopiere Link“ Buttons angezeigt.
- Der Bereich „Optionen bearbeiten“ steht **oberhalb** von „Links zum Teilen“.
- Der separate Block „Datum entfernen“ ist nicht vorhanden; Löschungen erfolgen ausschließlich über die Liste.

## Navigation / Aktionen
- Button „Zurück“ (führt zu Schritt 2).
- Button „Umfrage erstellen“ (finale Erstellung).

## Footer/Links
- Spendenaufruf und FAQ/Links analog zu Schritt 1/2.

# Screenshots (Formularbereich)
- Hinweis: Die Screenshots sind **nicht verbindlich**. Sie dienen nur als grobe Orientierung und können (oder müssen) von der finalen Umsetzung abweichen, wenn die Spezifikation dies erfordert.
- Schritt 1: ![Schritt 1 Formular](/Users/bodo.te/dev/woodle/screenshots/woodle-step1-form.png)
- Schritt 2: ![Schritt 2 Formular](/Users/bodo.te/dev/woodle/screenshots/woodle-step2-form.png)
- Schritt 3: ![Schritt 3 Formular](/Users/bodo.te/dev/woodle/screenshots/woodle-step3-form.png)
- Benutzersicht (Inline‑Bearbeitung): ![Terminabstimmung Inline-Edit](/Users/bodo.te/dev/woodle/screenshots/TerminUmfrage_Benutzersicht_Abstimmung.png)

# Woodle — Benutzersicht (Abstimmung auf bestehender Umfrage)

## Überblick
Die Benutzersicht zeigt eine tabellenbasierte Übersicht aller Teilnehmenden und ihrer Stimmen pro Termin. Neue Stimmen können als zusätzliche Zeile hinzugefügt werden; bestehende Zeilen sind editierbar.

## Tabellenlayout
- Tabellenkopf mit gruppierten Datumsbereichen nach Monat (z. B. „Februar 2026“, „März 2026“).
- Unter jedem Monat sind einzelne Spalten mit Datum (inkl. Wochentag/Datum, z. B. „Fr 20“).
- Zeilen entsprechen Teilnehmenden; jede Zeile enthält die Abstimmungen für alle Termine.

## Zeilen-Interaktion
- **Jede Zeile kann in den Edit-Mode versetzt werden**, um die eigene Stimmenzeile zu ändern.
- Pro Zeile gibt es einen **Edit-Button** (Stift-Icon) neben dem Namen.
- Im Edit-Mode werden die Zellen der Zeile bearbeitbar.

## Abstimmungswerte (pro Zelle)
- Drei Zustände je Termin:
  - **Ja**
  - **Wenn nötig**
  - **Nein**
- Die Zustände werden in der Tabelle visuell unterscheidbar dargestellt (z. B. Icons/Farben).

## Neue Stimme hinzufügen
- Die Eingabezeile zum **Hinzufügen** ist **direkt in die Übersichtstabelle integriert** (am Ende der Tabelle).
  - Eingabe für **Name**.
  - Auswahl pro Termin (Ja / Wenn nötig / Nein).
- Aktion: **Speichern** der neuen Zeile (Button rechts in der Tabellenzeile).
- Der bisherige, separate Bereich **„Ihr Name“ bis „Speichern“ unterhalb der Tabelle entfällt**.

## Bessere Inline-Interaktion (gewünschtes Verhalten)
- **Neue Einträge und Änderungen erfolgen direkt innerhalb der Gesamttabelle**, nicht in einem separaten Dialog.
- **Einfügezeile ist Teil der Tabelle** (am Ende, unter den bestehenden Teilnehmenden):
  - Eingabefeld „Ihr Name“ direkt in der Tabellenzeile.
  - Pro Termin eine **3‑Zustands‑Auswahl** (Ja / Wenn nötig / Nein) in der Zelle.
  - Ein **einzelner Speichern‑Button** rechts neben der Zeile.
- **Bearbeiten bestehender Zeilen**:
  - Jede Zeile hat einen **Edit‑Button (Stift)** auf Zeilenhöhe.
  - Beim Bearbeiten wird **nur diese Zeile** in einen editierbaren Zustand versetzt (3‑Zustands‑Auswahl pro Zelle).
  - Speicherung erfolgt über den Speichern‑Button rechts (pro Zeile).
- Ziel: **Übersicht + direkte Manipulation** ohne Kontextwechsel; Nutzer sehen beim Eintragen/Ändern weiterhin alle vorhandenen Stimmen.

## Zusammenfassung je Termin
- Unterhalb der Tabelle gibt es eine Zeile mit Summen (Anzahl der Stimmen pro Termin).
- Der beste Termin kann visuell markiert werden (z. B. Stern).

## Aktionen
- Primäre Aktion: **„Speichern“** für neue oder geänderte Stimmen.

# Edge Cases (beobachtet)
- Schritt 1: Leere Pflichtfelder (Name/Titel) verhindern das Weitergehen, aber es erscheint keine sichtbare Fehlermeldung im Formular.
- Schritt 2: Ungültiges Datum `2026-13-40` wird akzeptiert und als **2027-02-09** normalisiert (Überlauf in Monat/Tag).
- Schritt 2: Nur ein Termin ist möglich (Weiter zu Schritt 3 klappt), obwohl der Hinweis „mindestens zwei alternative Zeitpunkte“ sagt.
- Schritt 2: Doppelte Termine sind erlaubt (derselbe Tag erscheint zweimal in der Zusammenfassung).
- Schritt 3: Löschdatum vor dem letzten Termin (z. B. `2026-02-10` bei letztem Termin `2026-02-11`) wird akzeptiert, keine sichtbare Validierung.

# Fachliche Vorgaben (Persistenz & Identifikation)
- Keine Userverwaltung.
- Keine Account-/Passwort-Logins; E-Mail-Adressen bleiben fachlich relevant und dürfen validiert werden.
- Jede neue Umfrage erzeugt eine UUID als Primärschlüssel.
- Unter dieser UUID werden Stammdaten der Umfrage sowie später die Auswahl/Antworten der Teilnehmenden gespeichert.
- Jede Umfrage ist ausschließlich über einen Link erreichbar, der die UUID enthält.
- Speicherung erfolgt in Amazon S3 (keine Datenbank).
- Umfragen werden nach Ablauf des Verfallsdatums vollständig gelöscht (inklusive aller Antworten).

# Flow (Event-Typ und Zeitlogik)
- Schritt 2 startet mit der Auswahl des Event-Typs.
- Ganz-/Mehrtägig:
  - Eingabe nur für Tage/Datumsbereiche.
  - Keine Uhrzeiten, keine Dauer, keine Startzeiten.
- Untertägig:
  - Jeder Eintrag ist eine **Tag+Startzeit**‑Kombination; **eine globale Dauer** gilt für alle Varianten.
  - Globales Dauerfeld ist erforderlich und muss positiv sein (z. B. `180` Minuten).
  - Startzeit-Format: `HH:MM` im 24h-Format.
  - Validierung: Keine leeren Startzeiten; Dauerfeld muss gesetzt sein.

# Routing-Fallback (neu)
- Für **alle undefinierten Pfade** soll die Anwendung **nicht** auf eine Fehlerseite (z. B. 404/Whitelabel) gehen.
- Stattdessen erfolgt eine Weiterleitung auf die Startseite zum Erstellen einer neuen Umfrage:
  - Ziel: `/poll/new`
- Dies gilt für alle HTTP-Methoden (`GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`).

# Domain- und URL-Anforderung (neu)
- Die Benutzerführung bleibt durchgehend auf der Frontend-Domain:
  - `https://woodle.click/...`
- Das gilt mindestens für:
  - Schritt 1: `/poll/new` bzw. `/poll/new-step1.html`
  - Schritt 2: `/poll/step-2`
  - Schritt 3: `/poll/step-3`
  - Abstimmung/Teilnahme: `/poll/{pollId}` und Admin-Ansicht
- Während normaler Nutzung dürfen keine sichtbaren Weiterleitungen auf technische Backend-Domains (z. B. `*.execute-api.*.amazonaws.com`) stattfinden.
