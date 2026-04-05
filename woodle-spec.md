# Woodle — Create Poll (Step 1 of 3, Type: Date)

## Overview
This page is step 1 of 3 for creating a date poll in Woodle. The focus is on the poll's basic metadata (author, contact, title, description) and notifications. The email address is validated for a plausible format as soon as the field loses focus and is visually marked if invalid.

## Page Data
- URL: `https://nuudel.digitalcourage.de/create_poll.php?type=date`
- Title: `Umfrage erstellen (Schritt 1 von 3) - woodle`
- Language: default `Deutsch` (selectable via language dropdown)

## Layout
- Header area with language selector + OK button, logo/link `woodle`, and page title.
- Main form with required fields and optional settings.
- Call-to-action button for step 2.
- Footer area with donation hint and FAQ/links.

## Required Fields
- **Ihr Name** (textbox, required)
- **Ihre E‑Mail‑Adresse** (textbox, required)
  - Immediate format validation after leaving the field (HTMX, without full page reload).
  - Invalid format is marked in red and blocks the transition to step 2.
- **Titel der Umfrage** (textbox, required)

## Description
- `Beschreibung` field as a simple textarea.

## Notifications
- Checkbox: `Bei jeder neuen Umfrage eine E‑Mail erhalten`
- Checkbox: `Bei jedem neuen Kommentar eine E‑Mail erhalten`

## Navigation / Actions
- Button: **`Weiter zum 2. Schritt`** (goes to the next page of poll creation)

## Footer / Links (Not Part of the Form)
- Donation callout `Digitalcourage Jetzt spenden` with a link.
- FAQ area with links to privacy policy, imprint, AVV, Onion service, newsletter, etc.

## Open Questions / Not Visible in Step 1
- Detailed rules for valid email formats (for example allowed special characters) are not visible.
- The contents of steps 2 and 3 are not part of this section of the specification.

# Woodle — Poll Data (Step 2 of 3, Type: Date)

## Overview
Step 2 defines the actual date suggestions. First, the UI asks whether this is an all-day / multi-day event or an intraday event. At least two alternative time options are required.

## Page Data
- URL: `https://nuudel.digitalcourage.de/create_date_poll.php`
- Title: `Umfragedaten (2 von 3) - woodle`
- Language: default `Deutsch` (selectable via language dropdown)

## Layout
- Header area as in step 1.
- Main area with event-type selection and a dynamic form section for date suggestions.
- Dynamic behavior via HTMX: the section below the event-type question is replaced without a full page reload.
- Navigation with `Weiter`.

## Explanatory Texts
- `Mindestens zwei alternative Zeitpunkte` is required.
- Hints for adding/removing days.
- Hint: times depend on the selected event type.

## Event Type
- Question: Is this an all-day / multi-day event?
- Options:
  - **Ganz-/Mehrtägig**: suggestions consist only of days. **No times, no duration visible.**
  - **Untertägig**: suggestions always consist of **day + start time** per entry. In addition, there is one global field for **duration**.

## Date Suggestions (All-Day / Multi-Day, Repeatable)
By default, two blocks are visible (`Tag 1-2`). Each block contains:
- date input (`Tag X`) with placeholder `yyyy-mm-dd`.

## Date Suggestions (Intraday, Repeatable)
In addition to the day fields, there is a global field for the event **Dauer** that applies to all variants.
Each block contains:
- date input (`Tag X`) with placeholder `yyyy-mm-dd`.
- start time (`Startzeit X`, for example `11:00`).
- each additional block is another **day+start-time** combination.

## Global Actions
- Buttons `Einen Tag hinzufügen/entfernen` (all-day / multi-day).
- Buttons `Termin hinzufügen/entfernen` (intraday).

## Navigation / Actions
- Button `Weiter` (goes to step 3).

## Open Questions / Not Visible in Step 2
- Exact validation logic for date/time (for example format errors) is not visible here.

# Woodle — Voting Period and Confirmation (Step 3 of 3)

## Overview
Step 3 shows a summary of the available options, defines the automatic deletion date, and offers final poll creation.

## Page Data
- URL: `https://nuudel.digitalcourage.de/create_date_poll.php`
- Title: `Abstimmungszeitraum und Bestätigung (3 von 3) - woodle`
- Language: default `Deutsch` (selectable via language dropdown)

## Summary of Available Options
- Heading `Liste Ihrer Auswahlmöglichkeiten`.
- List of the proposed dates.
  - all-day / multi-day: date list.
  - intraday: date **with time** per entry (for example `2026-02-10 09:00`).

## Deletion Date
- Hint: poll is deleted automatically **720 days after the last date**.
- Option: `Tag der Löschung` as a date field (placeholder `yyyy-mm-dd`) with a prefilled value.

## Confirmation Hints
- Hint about automatic redirect to the administration page after confirmation.
- Hint: two emails are sent (participant link and admin link).

## After Creation (Admin Page)
- Two copyable links are shown directly on the page:
  - `Link für Teilnehmende` (public).
  - `Admin-Link` for changes.
- Both links are shown as **full absolute URLs** including protocol and host, not only as a path.
  - AWS example: `https://woodle.click/poll/<pollId>`
  - local example: `http://localhost:<port>/poll/<pollId>`
  - admin-link equivalent: `.../poll/<pollId>-<adminSecret>`
- URL generation is request-based:
  - locally `http` + the actual local port (for example `8088`)
  - deployed on AWS: `https`
- Admin can remove existing dates directly in the list via a `Termin löschen` button.
  - For intraday dates, deletion happens **by date + time**.
- The links are shown as non-editable text with `Kopiere Link` buttons.
- The `Optionen bearbeiten` area appears **above** `Links zum Teilen`.
- A separate `Datum entfernen` block does not exist; deletions happen exclusively via the list.

## Navigation / Actions
- Button `Zurück` (goes to step 2).
- Button `Umfrage erstellen` (final creation).

## Footer / Links
- Donation callout and FAQ/links analogous to steps 1 and 2.

# Screenshots (Form Area)
- Note: the screenshots are **not binding**. They are only rough orientation and may need to differ from the final implementation if required by the specification.
- Step 1: ![Schritt 1 Formular](/Users/bodo.te/dev/woodle/screenshots/woodle-step1-form.png)
- Step 2: ![Schritt 2 Formular](/Users/bodo.te/dev/woodle/screenshots/woodle-step2-form.png)
- Step 3: ![Schritt 3 Formular](/Users/bodo.te/dev/woodle/screenshots/woodle-step3-form.png)
- User view (inline editing): ![Terminabstimmung Inline-Edit](/Users/bodo.te/dev/woodle/screenshots/TerminUmfrage_Benutzersicht_Abstimmung.png)

# Woodle — User View (Voting on an Existing Poll)

## Overview
The user view shows a table-based overview of all participants and their votes per date. New votes can be added as an extra row; existing rows are editable.

## Table Layout
- Table header with grouped date ranges by month (for example `Februar 2026`, `März 2026`).
- Under each month are individual columns with date labels (including weekday/date, for example `Fr 20`).
- Rows correspond to participants; each row contains votes for all dates.

## Row Interaction
- **Every row can be switched into edit mode** to change that participant's vote row.
- Each row has an **edit button** (pencil icon) next to the name.
- In edit mode, the cells of that row become editable.

## Vote Values (Per Cell)
- Three states per date:
  - **Ja**
  - **Wenn nötig**
  - **Nein**
- These states are visually distinguishable in the table (for example by icons/colors).

## Add New Vote
- The input row for **adding** is **integrated directly into the overview table** at the end of the table.
  - Input for **Name**.
  - Selection per date (`Ja` / `Wenn nötig` / `Nein`).
- Action: **`Speichern`** the new row via the button on the right in the table row.
- The previous separate area from `Ihr Name` to `Speichern` below the table is removed.

## Improved Inline Interaction (Desired Behavior)
- **New entries and edits happen directly inside the full table**, not in a separate dialog.
- **Insert row is part of the table** (at the end, below the existing participants):
  - input field `Ihr Name` directly in the table row.
  - for each date a **3-state selection** (`Ja` / `Wenn nötig` / `Nein`) inside the cell.
  - one single **`Speichern`** button on the right next to the row.
- **Editing existing rows**:
  - each row has an **edit button (`Stift`)** at row level.
  - when editing, **only that row** becomes editable (3-state selection per cell).
  - saving happens via the `Speichern` button on the right (per row).
- Goal: **overview + direct manipulation** without context switching; users continue to see all existing votes while entering or changing their own.

## Summary Per Date
- Below the table there is a summary row with totals (number of votes per date).
- The best date may be visually highlighted (for example with a star).

## Actions
- Primary action: **`Speichern`** for new or changed votes.

# Edge Cases (Observed)
- Step 1: empty required fields (name/title) block continuation, but no visible validation error appears in the form.
- Step 2: invalid date `2026-13-40` is accepted and normalized to **`2027-02-09`** (overflow in month/day).
- Step 2: only one date option is possible (`Weiter` to step 3 works), although the hint says `mindestens zwei alternative Zeitpunkte`.
- Step 2: duplicate dates are allowed (the same day appears twice in the summary).
- Step 3: deletion date before the last date (for example `2026-02-10` when the last date is `2026-02-11`) is accepted, with no visible validation.

# Domain Requirements (Persistence & Identification)
- No user management.
- No account/password logins; email addresses remain domain-relevant and may be validated.
- Every new poll gets a UUID as the primary key.
- Under that UUID, the poll's master data and later participant selections/responses are stored.
- Every poll is reachable exclusively by a link that contains the UUID.
- Storage is in Amazon S3 (no database).
- The stored poll JSON contains a top-level field `schemaVersion` (default `"1"`, configurable via `woodle.poll.schema-version`).
- Every change to the poll JSON schema must increment `schemaVersion` so older polls can be detected and migrated/converted.
- On poll read: if `schemaVersion` is missing or lower than `woodle.poll.schema-version`, the poll is migrated to the current schema and immediately overwritten in S3 before the response is returned to the UI.
- Polls are fully deleted after expiry (including all responses).

# Flow (Event Type and Time Logic)
- Step 2 starts with event-type selection.
- All-day / multi-day:
  - input only for days/date ranges.
  - no times, no duration, no start times.
- Intraday:
  - each entry is a **day+start-time** combination; **one global duration** applies to all variants.
  - global duration field is required and must be positive (for example `180` minutes).
  - start-time format: `HH:MM` in 24-hour format.
  - validation: no empty start times; duration field must be set.

# Routing Fallback
- For **all undefined paths**, the application should **not** go to an error page (for example 404/Whitelabel).
- Instead, it redirects to the start page for creating a new poll:
  - target: `/poll/new`
- This applies to all HTTP methods (`GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`).

# Domain and URL Requirement
- The user journey must stay entirely on the frontend domain:
  - `https://woodle.click/...`
- This applies at minimum to:
  - step 1: `/poll/new` or `/poll/new-step1.html`
  - step 2: `/poll/step-2`
  - step 3: `/poll/step-3`
  - voting/participation: `/poll/{pollId}` and admin view
- During normal usage, there must be no visible redirects to technical backend domains (for example `*.execute-api.*.amazonaws.com`).
