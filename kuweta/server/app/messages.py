"""Wbudowane teksty powiadomień. Admin może dopisywać własne w aplikacji.

Zmienne: {name} - kto ma dyżur, {partner} - druga osoba, {days} - dni spóźnienia,
{cat} - losowy kot z listy admina, {cats} - wszystkie koty ("Beżi i Mruczek").
Poziom 1-4 dotyczy przypomnień: im dłużej kuweta czeka, tym wyższy.
"""

SEED_VERSION = 3

DEFAULT_MESSAGES: list[tuple[str, int, str]] = [
    # (kategoria, poziom, tekst)
    ("reminder", 1, "Kuweta, {name}. Ruszaj dupę, zanim koty zaczną się wkurwiać."),
    ("reminder", 1, "Dzień kuwety. {cats} czekają, a ty co, kurwa, medytujesz?"),
    ("reminder", 1, "{cat} patrzy na kuwetę, potem na ciebie. Dobrze wiesz, co ten wzrok znaczy."),
    ("reminder", 1, "Łopatka, piasek, worek. Trzy rzeczy, {name}. Nawet ty to ogarniesz."),
    ("reminder", 1, "Kuweta sama się nie posprząta. Gówno samo się nie wyniesie. Ty się ruszysz."),
    ("reminder", 2, "Chcesz, żeby kotki chodziły z obsraną dupą? Sprzątnij kuwetę xd"),
    ("reminder", 2, "Kocie gówno samo się nie wyniesie, księżniczko. RUCHY."),
    ("reminder", 2, "{cat} już patrzy, żeby zeszczać się na panele. I będzie miał(a) rację."),
    ("reminder", 2, "{name}, to już nie kuweta, to jebane wysypisko."),
    ("reminder", 2, "{cats} srają w gówno, bo czystego miejsca już nie ma. Ogarnij to, kurwa."),
    ("reminder", 2, "{cat} wchodzi do kuwety i wychodzi z obrzydzeniem. Kot. Z obrzydzeniem. Pomyśl o tym."),
    ("reminder", 2, "Kuweta śmierdzi tak, że sąsiedzi zaraz zadzwonią po sanepid."),
    ("reminder", 3, "Kurwa, {name}, ta kuweta jebie gorzej niż twoje gacie po nocce. Rusz dupsko."),
    ("reminder", 3, "{cat} rozważa nasranie ci na poduszkę. I wiesz co? Kibicuję."),
    ("reminder", 3, "Jeszcze chwila i koty zaczną srać obok. I będą miały w chuj rację."),
    ("reminder", 3, "Ja pierdolę, ile można? Weź tę łopatkę w końcu."),
    ("reminder", 3, "{cats} zakładają związek zawodowy. Pierwszy postulat: ogarnij to jebane gówno."),
    ("reminder", 3, "Ta kuweta ma już własny ekosystem. Wypierdol go, zanim zacznie płacić czynsz."),
    ("reminder", 3, "{name}, koty przynajmniej zakopują swoje gówno. Ty nawet tego nie umiesz."),
    ("reminder", 4, "{days} dni spóźnienia. Cały dom jebie gównem, a ty dalej udajesz, że nie czujesz."),
    ("reminder", 4, "Kurwa mać, {name}! {cat} sra już na dywan i ma do tego pełne prawo."),
    ("reminder", 4, "Gratulacje, {name}, w kuwecie wyhodowało się nowe życie. Nazwij je po sobie, pasuje."),
    ("reminder", 4, "{cats} oficjalnie mają cię w dupie. Tak samo jak ty ich kuwetę. {days} dni, serio, kurwa?"),
    ("reminder", 4, "To już nie przypomnienie, to interwencja. Łopatka. Teraz. Kurwa."),
    ("reminder", 4, "{cat} właśnie wybiera, w które buty nasrać. Podpowiem: w twoje ulubione."),
    ("reminder", 4, "Smród z kuwety ma już własny adres zameldowania. Wypierdol go stąd."),
    ("extension", 0, "Wczoraj ci odpuszczono. Dziś nie ma chuja we wsi, {name}. Kuweta."),
    ("extension", 0, "Dzień zwłoki to nie urlop, leniu. Łopatka w łapę."),
    ("extension", 0, "{partner} dał(a) ci dzień luzu. Nie spierdol tego drugi raz."),
    ("extension", 0, "Przedłużenie się skończyło. {cat} pamięta i nie wybacza. Rusz dupę."),
    ("extension", 0, "Drugi dzień tego samego gówna. Dosłownie. Ogarnij, kurwa."),
    ("partner", 0, "{name} olewa kuwetę już {days} dni. Pociśnij śmiało."),
    ("partner", 0, "Donos: kuweta dalej zajebana gównem, a dyżur ma {name}."),
    ("partner", 0, "{cat} skarży się na {name}: {days} dni w gównie. Zrób z tym porządek."),
    ("cleaned", 0, "Kuweta ogarnięta, robotę zrobił(a) {name}. Szacun. Twoja kolej za {days} dni."),
    ("cleaned", 0, "{cats} dziękują, kurwa, w końcu czysto. Za {days} dni twoja kolej, nie spierdol."),
    ("cleaned", 0, "Czysto jak łza. Za {days} dni twój dyżur. Zapamiętaj, bo przypomnę i nie będzie miło."),
    ("ext_request", 0, "{name} prosi o dzień zwłoki z kuwetą. Dajesz luz czy każesz zapierdalać?"),
    ("ext_request", 0, "{name} jęczy, że dziś nie da rady z kuwetą. Zgoda czy niech zapierdala?"),
]

LEVEL_NAMES = {1: "łagodnie", 2: "z jajem", 3: "ostro", 4: "bez litości"}
