# Bewerbungsmanager Desktop

Lokale Desktop-Anwendung zur Bewerbungsverwaltung — **Local-First und verschlüsselt**.

Alle Daten bleiben auf dem eigenen Rechner: kein Server, keine Cloud, kein Docker, kein
offener Netzwerkanschluss, keine ausgehenden Verbindungen. Die Datenbank ist mit AES-256
verschlüsselt und ohne das Passwort nicht lesbar — auch nicht mit einem SQLite-Werkzeug.
So gebaut, weil Bewerbungsunterlagen mit zum Persönlichsten gehören, was auf einem Rechner
liegt.

## Was die Anwendung kann

**Bewerbungen verwalten** — Firma, Stelle, Ansprechpartner, Ort, Link und Notiz. Der Status
(Entwurf, Abgeschickt, Absage, Erfolg) lässt sich direkt auf der Karte umstellen, und jede
Änderung wird mit Datum festgehalten. Dadurch steht auf der Karte, was zählt:

| Status | Anzeige |
|--------|---------|
| Entwurf | „Entwurf seit 06.09.2026" |
| Abgeschickt | „Abgeschickt am 06.09.2026 · Nachfassen ab 20.09.2026" |
| Absage / Erfolg | das jeweilige Datum |

**Unterlagen** — Lebenslauf, Zeugnisse und Zertifikate einmal hinterlegen und mit Bewerbungen
verknüpfen, das Anschreiben je Bewerbung. Alles mit PDF-Vorschau in der Anwendung.

**PDF-Bündel** — führt Anschreiben, Lebenslauf, Zeugnisse und Zertifikate in dieser Reihenfolge
zu einer einzigen PDF zusammen (die übliche Reihenfolge einer deutschen Bewerbungsmappe).
Wird bei jedem Herunterladen frisch erzeugt und kann deshalb nicht veralten.

**Erinnerungen** — an ein fehlendes Anschreiben, an liegengebliebene Entwürfe (Rhythmus je
Bewerbung wählbar: täglich, wöchentlich, monatlich, aus) und ans Nachfassen nach dem
Abschicken. Die Frist steht standardmäßig auf 14 Tagen und lässt sich je Bewerbung
überschreiben. Fällige Bewerbungen erscheinen als eigene Kachel und lassen sich damit filtern.

**Windows-Benachrichtigungen** (abschaltbar) — Erinnerungen über den Infobereich, wahlweise
mit Autostart. Beim Schließen des Fensters wird gefragt, ob die Anwendung im Infobereich
weiterlaufen soll; nach einstellbarer Inaktivität sperrt sie sich dort von selbst wieder.

**Markdown-Export** — die gerade angezeigte Liste als Tabelle in eine `.md`-Datei; Suche und
Statusfilter wirken sich aus.

Die Anwendung merkt sich Größe, Position und den maximierten Zustand des Fensters.

## Technik

- **JavaFX 21** — Oberfläche
- **Spring Boot** — Dienste und JPA, ohne Web-Layer (`spring.main.web-application-type: none`)
- **SQLite mit SQLCipher** — eingebettete, verschlüsselte Datei-Datenbank, kein Datenbankserver
- **PDFBox** — Vorschau und Zusammenführen der Unterlagen

## Zwei Zugänge, zwei Datenbanken

| Zugang | Datei | Inhalt |
|--------|-------|--------|
| Demo- und Notfallzugang | `~/.bewerbungsmanager/demo.db` | nur Musterdaten |
| Eigenes Konto | `~/.bewerbungsmanager/data.db` | die echten Bewerbungsdaten |

Der Demo-Zugang (`mkysarte` / `changeme`) ist absichtlich fest und steht sichtbar im Login.
Er hat zwei Aufgaben: die Anwendung vor der Entscheidung ausprobieren, und ein Weg zurück
hinein, falls Passwort *und* Masterpasswort verloren gehen — dann muss niemand deinstallieren
und neu installieren.

**An die echten Daten kommt er nicht heran.** Beide Datenbanken haben eigene Schlüssel, und
der Demo-Zugang besitzt den zu `data.db` schlicht nicht. Die Trennung ist damit kryptografisch
erzwungen und nicht bloß in der Oberfläche geprüft. Pro Sitzung ist immer nur eine der beiden
Dateien geöffnet.

Es gibt genau **ein** echtes Konto — ein zweites lässt sich nicht anlegen.

## Verschlüsselung — die Kurzfassung

Die Datenbank wird nicht mit dem Passwort verschlüsselt, sondern mit einem zufälligen
32-Byte-Schlüssel. Der liegt **zweifach verpackt** in `keys.properties` — einmal mit dem
Login-Passwort, einmal mit dem Masterpasswort. Ein Passwortwechsel schreibt deshalb nur die
Verpackung neu und lässt die Datenbank unangetastet; das Masterpasswort bleibt unabhängig
davon gültig.

> **Gehen Passwort und Masterpasswort beide verloren, sind die Daten endgültig weg.**
> Das ist der Preis echter Verschlüsselung — eine Hintertür für den Nutzer wäre auch eine
> für jeden anderen.

Ausführlich, samt einer ehrlichen Liste dessen, wogegen das alles **nicht** schützt:
**[SICHERHEIT.md](SICHERHEIT.md)**

## Erster Start

```bash
./mvnw javafx:run
```

Mit `mkysarte` / `changeme` anmelden, die Musterdaten ausprobieren, dann über
**„Eigenes Konto einrichten"** Benutzername und Passwort festlegen. Dabei entsteht die
Masterpasswort-PDF — bitte ausdrucken oder sicher ablegen. Das neue Konto startet leer; die
Musterdaten bleiben im Demo-Zugang zurück.

## Wo die Daten liegen

```
~/.bewerbungsmanager/
  demo.db            Musterdaten des Demo-Zugangs
  data.db            das eigene, verschlüsselte Konto
  keys.properties    der zweifach verpackte Datenbankschlüssel
  fenster.properties zuletzt verwendete Fenstergröße
```

Nichts davon gehört ins Repository; die `.gitignore` schließt es aus. Ein Backup ist das
Kopieren dieses Ordners — die Anwendung vorher beenden, damit die WAL-Datei geschrieben ist.

## Tests

```bash
./mvnw test
```

131 Tests, ohne Datenbank oder Container. Überwiegend Unit-Tests mit Mockito, dazu drei, die
sich lohnen zu kennen:

- **`DemoSessionIntegrationTest`** fährt eine echte verschlüsselte Sitzung hoch und prüft an
  der fertigen Datei nach, dass weder der SQLite-Kopf noch Firmen- oder Personennamen im
  Klartext darin stehen.
- **`SchemaUpgradeTest`** legt eine befüllte Datenbank im alten Schema an und startet den
  heutigen Code darüber — fängt Änderungen, die vorhandene Daten unlesbar machen würden.
- **`MasterPasswordPdfServiceTest`** liest die erzeugte PDF zurück und prüft, dass Umlaute
  ankommen und die Byte-Verweise im Dokument stimmen.

## Installer bauen

Erzeugt einen nativen Installer mit gebündelter JRE und den nativen SQLCipher-Bibliotheken.
Auf dem Zielrechner ist weder Java noch sonst etwas nachzuinstallieren, und es wird nichts aus
dem Netz geladen:

```bash
./mvnw -Pwindows clean package jpackage:jpackage   # Windows: EXE
./mvnw -Pmac     clean package jpackage:jpackage   # macOS:   DMG
./mvnw -Plinux   clean package jpackage:jpackage   # Linux:   DEB
```

Ergebnis unter `target/installer/`.

**Hinweis:** `jpackage` kann nicht cross-kompilieren — jedes Paket muss auf seinem eigenen
Betriebssystem gebaut werden. Genau dafür gibt es die CI.

## CI/CD

Zwei Abläufe unter `.github/workflows/`:

**`tests.yml`** — läuft bei jedem Push und jedem Pull Request auf Linux und führt alle Tests
aus. Bewusst nur ein Läufer: Der Code ist plattformunabhängig, und Linux kostet ein Zehntel
eines macOS-Läufers.

**`release.yml`** — läuft nur, wenn eine Version markiert wird:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Dann laufen erst die Tests, danach bauen drei Läufer parallel die Installer für Windows, macOS
und Linux, und die fertigen Dateien landen als GitHub-Release zum Herunterladen. Die
Versionsnummer im Installer wird dabei aus der Marke übernommen.

**Die Installer sind nicht signiert.** Windows meldet beim Start „Der Computer wurde durch
Windows geschützt", macOS „Entwickler nicht verifiziert" — beides lässt sich wegklicken, wirkt
aber unseriös. Dagegen hülfen nur kostenpflichtige Zertifikate (Windows-Code-Signing, Apple
Developer Program).
