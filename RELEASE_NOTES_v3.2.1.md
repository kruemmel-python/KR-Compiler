# KR Compiler Android IDE v3.2.1

Version 3.2.1 verbessert den Editor für Querformat, Bildschirmspiegelung und die Nutzung auf einem Fernseher.

## Verbessert

- Kopfzeile und Hauptnavigation teilen sich im Querformat automatisch eine Zeile.
- Dokument-, Symbol- und Buildleisten werden bei geringer Bildschirmhöhe kompakter.
- Die Dateiseitenleiste benötigt weniger Breite.
- Die Editoranzeige wird im kompakten Querformat gut lesbar begrenzt, ohne die persönliche Hochformat-Einstellung zu verändern.
- In der CODE-Ansicht erhält der Editor den Platz der unteren Buildleiste.
- Beim Wechsel zu BUILD erscheint die Zielauswahl mit der Schaltfläche **Kompilieren** sofort wieder.

## Geprüft

- Signierter Release-Build und automatische Gradle-Tests erfolgreich
- Upgrade-Installation auf RMX3853 erfolgreich
- automatische Umschaltung zwischen Hoch- und Querformat ohne Verlust des Projekts
- ungefähr sieben sichtbare Codezeilen statt zuvor nur einer Zeile
- BUILD-Tab und Kompilierleiste im Querformat funktionsfähig

Die Compilerlaufzeit sowie die Android-, Windows- und Linux-GUI-Unterstützung aus v3.2.0 bleiben unverändert erhalten.
