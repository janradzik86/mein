#!/usr/bin/env python3
"""Statyczna kontrola spójności zasobów tekstowych z kodem Kotlin.

Sprawdza (bez Gradle i bez Android SDK, więc działa też lokalnie):
  1. każdy `R.string.X` użyty w Kotlin istnieje w `res/values/strings.xml`,
  2. liczba argumentów w `stringResource(...)` / `getString(...)` zgadza się
     z liczbą placeholderów `%n$...` w zasobie,
  3. które klucze są zdefiniowane, ale nieużywane (raport porządkowy),
  4. brak duplikatów kluczy i poprawność XML.

Użycie:  python3 tools/check-strings.py
Kod wyjścia: 0 = OK, 1 = znaleziono błędy blokujące.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STRINGS_XML = ROOT / "app/src/main/res/values/strings.xml"
KOTLIN_DIRS = [ROOT / "app/src/main/kotlin", ROOT / "app/src/test/kotlin", ROOT / "app/src/androidTest/kotlin"]

R_STRING = re.compile(r"\bR\.string\.([A-Za-z0-9_]+)")
CALL = re.compile(r"\b(?:stringResource|getString)\s*\(")
PLACEHOLDER = re.compile(r"%(\d+)\$")


def load_strings() -> dict[str, str]:
    tree = ET.parse(STRINGS_XML)
    values: dict[str, str] = {}
    duplicates: list[str] = []
    for element in tree.getroot():
        if element.tag != "string":
            continue
        name = element.get("name")
        if name is None:
            continue
        if name in values:
            duplicates.append(name)
        text = "".join(element.itertext())
        values[name] = text
    if duplicates:
        print("BŁĄD: zduplikowane klucze w strings.xml:", ", ".join(sorted(set(duplicates))))
    return values


def split_args(source: str, start: int) -> list[str] | None:
    """Zwraca argumenty wywołania zaczynającego się za nawiasem na pozycji `start`."""
    depth = 1
    index = start
    args: list[str] = []
    current: list[str] = []
    in_string = False
    in_lambda = 0
    while index < len(source) and depth > 0:
        char = source[index]
        if in_string:
            current.append(char)
            if char == "\\" and index + 1 < len(source):
                current.append(source[index + 1])
                index += 2
                continue
            if char == '"':
                in_string = False
            index += 1
            continue
        if char == '"':
            in_string = True
            current.append(char)
            index += 1
            continue
        if char == "{":
            in_lambda += 1
        elif char == "}":
            in_lambda -= 1
        if char == "(" or char == "[":
            depth += 1
        elif char == ")" or char == "]":
            depth -= 1
            if depth == 0:
                args.append("".join(current).strip())
                break
        if char == "," and depth == 1 and in_lambda == 0:
            args.append("".join(current).strip())
            current = []
            index += 1
            continue
        current.append(char)
        index += 1
    if depth != 0:
        return None
    return [arg for arg in args if arg]


def placeholder_count(text: str) -> int:
    numbers = {int(match) for match in PLACEHOLDER.findall(text)}
    if numbers:
        return max(numbers)
    return len(re.findall(r"%[sdf]", text))


def main() -> int:
    values = load_strings()
    used: dict[str, list[str]] = {}
    kotlin_files: list[Path] = []
    for directory in KOTLIN_DIRS:
        if directory.exists():
            kotlin_files.extend(sorted(directory.rglob("*.kt")))

    errors: list[str] = []
    for path in kotlin_files:
        source = path.read_text(encoding="utf-8")
        for match in R_STRING.finditer(source):
            used.setdefault(match.group(1), []).append(str(path.relative_to(ROOT)))
        for match in CALL.finditer(source):
            args = split_args(source, match.end())
            if not args:
                continue
            first = args[0]
            key_match = R_STRING.search(first)
            if not key_match:
                continue
            key = key_match.group(1)
            if key not in values:
                continue
            expected = placeholder_count(values[key])
            actual = len(args) - 1
            if expected != actual:
                line = source[: match.start()].count("\n") + 1
                errors.append(
                    f"{path.relative_to(ROOT)}:{line}: {key} oczekuje {expected} argumentów, "
                    f"wywołanie przekazuje {actual}"
                )

    missing = sorted(key for key in used if key not in values)
    unused = sorted(key for key in values if key not in used)

    if missing:
        print(f"BŁĄD: {len(missing)} kluczy użytych w kodzie nie istnieje w strings.xml:")
        for key in missing:
            files = ", ".join(sorted(set(used[key]))[:3])
            print(f"  - {key}  ({files})")
    if errors:
        print(f"BŁĄD: {len(errors)} niezgodności liczby argumentów:")
        for error in errors:
            print(f"  - {error}")

    print()
    print(f"Klucze zdefiniowane: {len(values)}")
    print(f"Klucze użyte w kodzie: {len(used)}")
    print(f"Brakujące: {len(missing)}")
    print(f"Niezgodne argumenty: {len(errors)}")
    print(f"Nieużywane: {len(unused)}")
    if unused:
        print("  " + ", ".join(unused))

    if missing or errors:
        return 1
    print("\nOK: wszystkie odwołania R.string istnieją i mają zgodną liczbę argumentów.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
