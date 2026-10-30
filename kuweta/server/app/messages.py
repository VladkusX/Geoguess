"""Domyślne teksty powiadomień. Admin może dopisywać własne w aplikacji.

Zmienne: {name} - kto ma dyżur, {partner} - druga osoba, {days} - dni spóźnienia.
"""

DEFAULT_MESSAGES: list[tuple[str, int, str]] = [
    # (kategoria, poziom, tekst)
    ("reminder", 1, "Ej, {name}. Kuweta. Wiesz co robić."),
    ("reminder", 1, "Dzień kuwety, {name}. Koty już patrzą na ciebie z oczekiwaniem."),
    ("reminder", 1, "Przypominajka: łopatka czeka, piasek czeka, koty czekają."),
    ("reminder", 2, "Chcesz, żeby kotki chodziły z obsraną dupą? Sprzątnij kuwetę xd"),
    ("reminder", 2, "Kocie gówno samo się nie wyniesie, księżniczko. RUCHY."),
    ("reminder", 2, "{name}, kuweta wygląda jak plac budowy. Ogarnij."),
    ("reminder", 3, "Ta kuweta pachnie gorzej niż twoje skarpety po nocce. Rusz dupę."),
    ("reminder", 3, "Koty właśnie złożyły na ciebie skargę do kociego związku zawodowego."),
    ("reminder", 3, "Jeszcze chwila i koty zaczną srać obok. I będą miały rację."),
    ("reminder", 4, "Kot właśnie rozważa nasranie ci do kapcia. Spóźnienie: {days} dni."),
    ("reminder", 4, "{days} dni spóźnienia. Czujesz to? Bo cały dom czuje."),
    ("reminder", 4, "Gratulacje, {name}, hodujesz w kuwecie nową formę życia."),
    ("extension", 0, "Wczoraj przedłużone, dziś nie ma zmiłuj. Kuweta, {name}."),
    ("extension", 0, "Dzień zwłoki to nie wakacje. Łopatka w dłoń."),
    ("extension", 0, "Przedłużenie się skończyło, leniu. Koty pamiętają."),
    ("extension", 0, "{partner} dał(a) ci dzień luzu. Nie spierdol tego drugi raz."),
    ("partner", 0, "{name} olewa kuwetę już {days} dni. Możesz pocisnąć."),
    ("partner", 0, "Donos: kuweta dalej brudna, a dyżur ma {name}."),
    ("cleaned", 0, "Kuweta ogarnięta, robotę zrobił(a) {name}. Szacun. Twoja kolej za {days} dni."),
    ("cleaned", 0, "Kuweta czysta jak łza. Za {days} dni twój dyżur, nie zapomnij."),
    ("ext_request", 0, "{name} prosi o dzień zwłoki z kuwetą. Zgadzasz się?"),
]

LEVEL_NAMES = {1: "łagodnie", 2: "z jajem", 3: "ostro", 4: "bez litości"}
