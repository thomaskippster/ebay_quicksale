# Quicksale - KI eBay Assistent

**Quicksale** ist eine hochspezialisierte Android-Applikation, die den Prozess des Verkaufens auf eBay durch den Einsatz moderner Künstlicher Intelligenz (Google Gemini) revolutioniert. Von der Fotoaufnahme bis zum Live-Angebot auf eBay dauert der gesamte Prozess mit Quicksale nur wenige Augenblicke.

## 🚀 Hauptfunktionen

### 1. Intelligente KI-Analyse (Gemini 1.5 Flash)
- **Automatische Erkennung:** Analysiert Produktfotos und ergänzende Notizen in Echtzeit.
- **Strukturierte Entwürfe:** Generiert automatisch optimierte eBay-Titel (max. 80 Zeichen), detaillierte HTML-Beschreibungen und realistische Preisvorschläge.
- **Merkmal-Extraktion:** Erkennt automatisch Details wie Marke, Herstellernummer (MPN), Material und Farbe (Aspects).
- **Zustands-Vorschlag:** Schlägt basierend auf den Bildern den passenden eBay-Zustand (z.B. USED_GOOD) vor.

### 2. Nahtlose eBay-Integration
- **Vollständiger Workflow:** Unterstützt den gesamten eBay-Listing-Prozess (Inventory Item -> Offer -> Publish).
- **eBay Picture Service (EPS):** Bilder werden direkt zu den eBay-Servern hochgeladen (inkl. historisch korrektem XML-Multipart-Protokoll für maximale Stabilität).
- **Business Policies:** Automatisches Laden und Zuweisen von Versand-, Zahlungs- und Rückgabebedingungen über komfortable Dropdown-Menüs.
- **Marktplatz-Support:** Unterstützung für verschiedene Marktplätze (EBAY_DE, EBAY_AT, EBAY_GB, EBAY_US).

### 3. Profi-Verkäufer Features
- **Stückzahl & Gewicht:** Manuelle Anpassung von Mengen und Versandgewichten.
- **Preisvorschläge:** Optionale Aktivierung der "Preisvorschlag"-Funktion pro Artikel.
- **Kategorie-Suche:** Integrierte Suchfunktion für eBay-Kategorien mit automatischer ID-Zuweisung.
- **Bild-Management:** Komfortable Galerie-Ansicht mit der Möglichkeit, das Haupt-Galeriebild festzulegen.

### 4. Sicherheit & Stabilität
- **Auto-Save:** Laufende Entwürfe und Bilder werden lokal gespeichert und überleben App-Neustarts oder Systempausen.
- **Safe-Input:** Speziell entwickelte UI-Komponenten verhindern den bekannten "springenden Cursor"-Bug in Jetpack Compose.
- **Validierung:** Proaktive Prüfung auf fehlende Angaben oder kritischen Speicherplatz am Gerät vor dem Upload.
- **Retry-Logik:** Automatisierte Wiederholungsversuche bei Bild-Upload-Fehlern.

## ⚖️ Rechtliche Konformität (App Store Ready)
- **Onboarding-Disclaimer:** Expliziter Haftungsausschluss beim ersten Start.
- **Impressum:** Vollständige Anbieterkennzeichnung inkl. Hinweis zur Kleinunternehmerregelung (§ 19 UStG).
- **Datenschutzerklärung:** Detaillierte Informationen zur Datenverarbeitung (lokale Speicherung vs. Cloud-Übertragung).
- **DSGVO/GDPR:** Transparenter Hinweis zur Bildanalyse durch Google Gemini vor der ersten Nutzung.
- **EU-DSA Status:** Offizielle Bestätigung des Trader-Status für kommerzielle Angebote.

## 🛠 Technischer Stack
- **Sprache:** Kotlin
- **UI-Framework:** Jetpack Compose (Material 3)
- **Architektur:** MVVM (Model-View-ViewModel)
- **Netzwerk:** Retrofit & OkHttp
- **Authentifizierung:** AppAuth (OAuth2)
- **KI-Modell:** Google Gemini AI SDK
- **Persistenz:** Jetpack DataStore & lokaler Dateispeicher

## 📋 Voraussetzungen
Um Quicksale produktiv zu nutzen, werden folgende Keys benötigt:
1. **Google Gemini API Key:** Erhältlich über die Google AI Studio Console.
2. **eBay Developer Credentials:** Client ID (App ID) und Client Secret (Cert ID) aus dem eBay Developers Program (Production Environment).

---
*Hinweis: Quicksale ist kein offizielles Produkt von eBay Inc. Alle Marken gehören ihren jeweiligen Eigentümern.*
