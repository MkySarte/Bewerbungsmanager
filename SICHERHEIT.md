# Sicherheitskonzept

Diese Anwendung verwaltet Bewerbungsunterlagen — Lebensläufe, Zeugnisse, Anschreiben,
Kontaktdaten. Das sind mit die persönlichsten Daten, die jemand auf seinem Rechner hat.
Dieses Dokument beschreibt, wie sie geschützt sind, **und wovor nicht**.

## Die Ausgangslage

Alles bleibt lokal: kein Server, keine Cloud, kein offener Netzwerkanschluss, keine
ausgehenden Verbindungen. Damit entfällt die ganze Klasse von Risiken, die aus einer
Serveranbindung entsteht — dafür verlagert sich alles auf eine Frage: **Was passiert, wenn
jemand an die Datei kommt?**

Ohne Verschlüsselung wäre die Antwort ernüchternd. Die Dokumente liegen als Blob in der
Datenbank (`DocumentEntity.content`); eine gewöhnliche SQLite-Datei lässt sich mit jedem
Standardwerkzeug öffnen und auslesen. Ein Anmeldebildschirm ändert daran nichts — er schützt
die Oberfläche, nicht die Daten.

## Was verschlüsselt ist

Die gesamte Datenbank, Datei für Datei, mit **SQLCipher** (AES-256, seitenweise). Nicht
einzelne Spalten, sondern alles: Firmennamen, Notizen, Dokumente, sogar die Tabellenstruktur.
Von außen betrachtet ist die Datei ein Haufen Zufallsbytes — sie trägt nicht einmal den
`SQLite format 3`-Kopf, an dem man sie als Datenbank erkennen würde.

Technisch über den Treiber `io.github.willena:sqlite-jdbc`, einen Ableger des üblichen
`org.xerial:sqlite-jdbc` mit eingebautem SQLCipher.

## Der Schlüssel wird nicht aus dem Passwort abgeleitet

Das ist der Kern des Entwurfs und der Punkt, den es zu verstehen lohnt.

Der naheliegende Weg wäre: Passwort durch eine Ableitungsfunktion schicken, Ergebnis als
Datenbankschlüssel nehmen. Das funktioniert — hat aber eine unangenehme Folge: **Jeder
Passwortwechsel bedeutet, die komplette Datenbank neu zu verschlüsseln.** Bei ein paar hundert
Megabyte Dokumenten dauert das, und wenn dabei der Strom ausfällt, ist die Datei halb
umgeschlüsselt und damit kaputt.

Stattdessen:

1. Beim Einrichten wird ein **zufälliger 32-Byte-Schlüssel** erzeugt (`SecureRandom`). Mit ihm
   wird die Datenbank verschlüsselt. Dieser Schlüssel ändert sich nie.
2. Dieser Schlüssel wird **zweimal verpackt** und in `keys.properties` abgelegt:
   - einmal mit dem Login-Passwort
   - einmal mit dem Masterpasswort
3. Verpackt heißt: Aus dem Passwort wird über **PBKDF2-HMAC-SHA256** (210.000 Runden, eigener
   Zufalls-Salt je Verpackung) ein Schlüssel abgeleitet, und mit diesem wird der
   Datenbankschlüssel per **AES-256-GCM** verschlüsselt.

Im Klartext steht der Datenbankschlüssel nirgends — weder auf der Platte noch in der Datenbank.

Der Gewinn: Ein Passwortwechsel schreibt **nur die eine Verpackung neu**, wenige hundert Bytes.
Die Datenbank wird nicht angefasst, und das Masterpasswort bleibt unabhängig davon gültig.

### Warum GCM und warum ein Salt je Verpackung

**AES-GCM** liefert nicht nur Verschlüsselung, sondern auch einen Authentifizierungswert. Passt
das Passwort nicht, schlägt schon die Prüfung dieses Werts fehl — man merkt sofort und
eindeutig, dass das Passwort falsch war, und muss nicht raten, ob stattdessen die Datei
beschädigt ist. Genau diese Unterscheidung macht die Anwendung: falsches Passwort → freundliche
Meldung, kaputte Datei → andere Meldung.

**Der eigene Salt je Verpackung** verhindert, dass sich Login- und Masterpasswort gegenseitig
verraten, und macht vorberechnete Tabellen wertlos.

**Die 210.000 Runden** sind Absicht: Sie machen das Ableiten für den berechtigten Nutzer
unmerklich langsam (Bruchteil einer Sekunde, einmal beim Anmelden), für jemanden, der Passwörter
durchprobiert, aber um denselben Faktor teuer.

## Anmeldung findet vor dem Programmstart statt

Ungewöhnlich, aber zwingend: Die Datenbankverbindung braucht den Schlüssel schon beim
Hochfahren. Deshalb läuft der Anmeldebildschirm **ohne** den Spring-Anwendungskontext, mit
einem einfachen FXML-Lader. Erst wenn der Schlüssel-Tresor aufgeht, startet der Kontext —
gebunden an genau eine entschlüsselte Datei.

Daraus folgt etwas Wichtiges: **Das Öffnen des Tresors *ist* die Authentifizierung.** Es gibt
keinen zusätzlichen Passwort-Vergleich gegen eine Benutzertabelle, denn die läge ja selbst in
der verschlüsselten Datei. Ein Passwort-Hash daneben wäre eine zweite Wahrheitsquelle, die
nichts absichert, aber leicht für die eigentliche Prüfung gehalten wird — deshalb enthält
`UserEntity` bewusst kein Passwortfeld.

## Zwei Konten, zwei Dateien, zwei Schlüssel

| Zugang | Datei | Schlüssel |
|--------|-------|-----------|
| Demo- und Notfallzugang (`mkysarte` / `changeme`) | `demo.db` | aus dem festen, öffentlich bekannten Passwort abgeleitet |
| Eigenes Konto | `data.db` | zufällig, verpackt im Tresor |

Der Demo-Zugang ist absichtlich öffentlich. Er erfüllt zwei Aufgaben: die Anwendung vor der
Entscheidung ausprobieren, und ein Weg zurück in die Anwendung, falls Passwort *und*
Masterpasswort verloren gehen — sonst bliebe nur Deinstallieren und Neuinstallieren.

**Er kommt an die echten Daten nicht heran**, und das ist nicht bloß in der Oberfläche geprüft,
sondern liegt an der Sache: Er besitzt den Schlüssel zu `data.db` schlicht nicht. Läge alles in
einer Datei, wäre die Verschlüsselung wertlos — ein öffentlich bekanntes `changeme` würde dann
die gesamte Datei aufschließen, Bewerbungsdaten eingeschlossen.

Pro Programmlauf ist immer nur **eine** der beiden Dateien geöffnet.

## Was das nicht schützt

Ehrlichkeit gehört dazu, sonst wiegt man sich in falscher Sicherheit:

- **Nicht gegen ein bereits übernommenes System.** Schadsoftware mit Ihren Benutzerrechten
  kann mitlesen, was Sie tippen, und den Arbeitsspeicher der laufenden Anwendung auslesen.
  Solange die Anwendung geöffnet ist, liegt der Datenbankschlüssel dort.
- **Nicht gegen jemanden am entsperrten Rechner.** Steht die Anwendung offen, sind die Daten
  sichtbar. Dagegen gibt es die automatische Sperre nach Inaktivität im Infobereich
  (Voreinstellung 30 Minuten), aber sie greift nur dort.
- **Nicht gegen ein schwaches Passwort.** Die 210.000 PBKDF2-Runden verteuern das Durchprobieren
  erheblich, aber „Sommer2024" ist auch dann irgendwann dran.
- **Kein Schutz vor Datenverlust.** Verschlüsselung ist kein Backup. Eine defekte Platte ist
  eine defekte Platte.

Wogegen es **schützt**: gestohlene oder verlorene Geräte, ausgebaute Festplatten, weitergegebene
Rechner, versehentlich kopierte Dateien, Zugriff durch andere Konten auf demselben Gerät,
neugierige Blicke in ein Backup.

## Wenn beide Passwörter verloren sind

Dann sind die Daten **endgültig weg**. Es gibt keine Hintertür, keinen Wiederherstellungsdienst,
keinen Hersteller, der helfen könnte.

Das ist kein Versäumnis, sondern die notwendige Kehrseite: Eine Hintertür für den Nutzer wäre
auch eine für jeden anderen. Deshalb die Masterpasswort-PDF beim Einrichten — sie ist die
Sicherheitsreserve und gehört ausgedruckt oder an einen sicheren Ort.

Über den Demo-Zugang kommt man weiterhin in die *Anwendung* und kann neu anfangen. An die
*Daten* kommt man nicht mehr.

## Was nicht ins Repository gehört

`data.db`, `demo.db` und `keys.properties` liegen unter `~/.bewerbungsmanager/`, also außerhalb
des Projektverzeichnisses, und waren nie versioniert. Die `.gitignore` schließt sie zusätzlich
aus — als Netz für den Fall, dass jemand eine Datenbank zum Nachsehen ins Projekt kopiert.

Ausgeliefert wird ausschließlich das Programm. Jede Installation legt beim ersten Start ihre
eigene, leere Datenbank im Benutzerordner an.

## Wo das im Code steht

| Was | Wo |
|-----|-----|
| Schlüssel-Tresor, Verpacken und Öffnen | `common/crypto/VaultService` |
| Datenbank und Schlüssel der Sitzung | `common/crypto/DbSession` |
| Ablageorte der beiden Datenbanken | `common/AppPaths` |
| Anmeldung vor dem Kontextstart | `BewerbungsmanagerApp`, `ui/screen/LoginController` |
| Rolle des Demo-Zugangs | `common/InitialAccess` |
| Tests dazu | `VaultServiceTest`, `DemoSessionIntegrationTest` |

`DemoSessionIntegrationTest` fährt eine echte verschlüsselte Sitzung hoch und prüft an der
fertigen Datei nach, dass weder der SQLite-Kopf noch Firmen- oder Personennamen im Klartext
darin stehen.
