Hier ist ein vollständiges Einzeldatei-Programm für deine KR Unified Language (`.kr`), das C++20, Rust und Go in einer einzigen Datei kombiniert.

Das Programm ist ein interaktiver Taschenrechner mit Addition, Subtraktion, Multiplikation, Division und Fehlerprüfung.

### Datei: `main.kr`

```

#include <iostream>
#include <cstdint>
#include <limits>

// =====================================================
// KR UNIFIED LANGUAGE - EINZELDATEI PROGRAMM
// C++20 + Rust + Go
// =====================================================

// -----------------------------------------------------
// RUST: Mathematische Berechnungen
// -----------------------------------------------------

fn rust_add(a: f64, b: f64) -> f64 {
    a + b
}

fn rust_subtract(a: f64, b: f64) -> f64 {
    a - b
}

fn rust_multiply(a: f64, b: f64) -> f64 {
    a * b
}

fn rust_divide(a: f64, b: f64) -> f64 {
    if b == 0.0 {
        0.0
    } else {
        a / b
    }
}

// -----------------------------------------------------
// GO: Validierung
// -----------------------------------------------------

func go_validate_operation(operation int32) bool {
    return operation >= 1 && operation <= 4
}

func go_validate_division(divisor float64) bool {
    return divisor != 0.0
}

// -----------------------------------------------------
// C++20: Benutzeroberflaeche
// -----------------------------------------------------

void cpp_show_menu() {
    std::cout << "\n============================\n";
    std::cout << "  KR UNIFIED CALCULATOR\n";
    std::cout << "============================\n";
    std::cout << "1. Addition\n";
    std::cout << "2. Subtraktion\n";
    std::cout << "3. Multiplikation\n";
    std::cout << "4. Division\n";
    std::cout << "0. Beenden\n";
    std::cout << "Auswahl: ";
}

int main() {
    std::cout << "KR Unified Language gestartet!\n";

    while (true) {
        cpp_show_menu();

        int32_t operation = 0;

        if (!(std::cin >> operation)) {
            std::cout << "Ungueltige Eingabe!\n";
            std::cin.clear();
            std::cin.ignore(
                std::numeric_limits<std::streamsize>::max(),
                '\n'
            );
            continue;
        }

        if (operation == 0) {
            std::cout << "Programm beendet.\n";
            break;
        }

        if (!go_validate_operation(operation)) {
            std::cout << "Unbekannte Operation!\n";
            continue;
        }

        double a = 0.0;
        double b = 0.0;

        std::cout << "Erste Zahl: ";
        if (!(std::cin >> a)) {
            std::cout << "Ungueltige Zahl!\n";
            std::cin.clear();
            std::cin.ignore(
                std::numeric_limits<std::streamsize>::max(),
                '\n'
            );
            continue;
        }

        std::cout << "Zweite Zahl: ";
        if (!(std::cin >> b)) {
            std::cout << "Ungueltige Zahl!\n";
            std::cin.clear();
            std::cin.ignore(
                std::numeric_limits<std::streamsize>::max(),
                '\n'
            );
            continue;
        }

        if (operation == 4 && !go_validate_division(b)) {
            std::cout << "Fehler: Division durch Null!\n";
            continue;
        }

        double result = 0.0;

        switch (operation) {
            case 1:
                result = rust_add(a, b);
                break;
            case 2:
                result = rust_subtract(a, b);
                break;
            case 3:
                result = rust_multiply(a, b);
                break;
            case 4:
                result = rust_divide(a, b);
                break;
        }

        std::cout << "Ergebnis: " << result << "\n";
    }

    return 0;
}

```

### Aufbau

| Sprache     | Aufgabe                                      |
| ----------- | -------------------------------------------- |
| C++20       | Menü, Eingaben, Programmschleife             |
| Rust        | Mathematische Berechnungen                   |
| Go          | Überprüfung der Operationen und Division     |
| KR Compiler | Automatische Verknüpfung aller drei Sprachen |

Verwendung: Speichere den Code als `main.kr` und kompiliere ihn in deiner KR Compiler Android IDE. Laut deinem Sprachleitfaden sind dafür weder Sprachmarker noch manuelle FFI-Definitionen oder eine `.krproject`-Datei erforderlich.

Hinweis: Der Code ist nach deinem KR-Sprachleitfaden geschrieben, wurde aber nicht mit dem KR-Compiler getestet.

Möchtest du als Nächstes ein größeres Einzeldatei-Programm, beispielsweise mit grafischer Benutzeroberfläche, Dateiverwaltung oder einem kleinen Spiel?
