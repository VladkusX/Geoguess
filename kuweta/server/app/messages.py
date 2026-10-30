"""Wbudowane teksty powiadomień. Admin może dopisywać własne w aplikacji.

Zmienne: {name} - kto ma dyżur, {partner} - druga osoba,
{ile_dni} - "1 dzień" / "3 dni" (spóźnienie albo czas do następnego dyżuru), {days} - sama liczba,
{cat} - losowy kot z listy admina, {cats} - wszystkie koty ("Beżi i Mruczek").
Imiona stoją zawsze w mianowniku, bo nie odmieniamy ich automatycznie.
Poziom 1-4 dotyczy przypomnień: im dłużej kuweta czeka, tym wyższy. {ile_dni} tylko od poziomu 4,
bo niżej spóźnienie wynosi zero.
"""

# Podniesienie wersji kasuje wszystkie wbudowane teksty na serwerze i wgrywa te poniżej.
SEED_VERSION = 5

_REMINDER = {
    1: [
        "{cat} patrzy na tę zasraną kuwetę, potem na ciebie. Rusz dupę, {name}.",
        "Dzień kuwety, kurwa. {cats} czekają, a ty dalej leżysz jak kłoda.",
        "{cat} wącha kuwetę i robi minę jak po zgniłym jajku. Ogarnij to.",
        "Gówno samo się nie wyniesie, {name}. {cats} też go nie wyniosą.",
        "{cat} ma w kuwecie gorzej niż ty w kiblu na dworcu. Sprzątaj, kurwa.",
        "Łopatka w łapę, {name}. {cat} nie będzie czekać wiecznie.",
        "{cats} srają, ty sprzątasz. Taki deal. Do roboty.",
        "Kuweta pełna, {cat} na granicy wkurwienia. Twój ruch.",
        "Ej, leniu. {cat} czeka na czysty kibel, a nie na twoje wymówki.",
        "{cats} pozdrawiają z zasranej kuwety. Ogarnij ten syf.",
        "{name}, kurwa, kuweta. Dziś. Nie jutro.",
        "{cat} grzebie w piasku i trafia tylko na gówno. Zrób coś z tym.",
        "Jak {cat} ma się wysrać w tym bajzlu? Sprzątaj, {name}.",
        "{cats} mówią wprost: dziś twój dyżur, dupku. Wywiąż się.",
        "Smród z kuwety już się budzi. {cat} też. Ty też się obudź.",
        "{cat} zasługuje na czysty kibel. Ty zasługujesz na łopatkę w łapie.",
        "Zanim znowu weźmiesz telefon, weź łopatkę. {cats} czekają.",
        "{cat} siedzi obok kuwety i ocenia twoje życiowe wybory. Słabo wypadasz.",
        "Kocie gówno nie wyparuje, {name}. Czuć to aż tutaj.",
        "{cats} mają gówniany dzień. Dosłownie. Napraw to.",
        "{name}, ruchy. {cat} nie będzie srać w tym burdelu.",
        "Kuweta to dziś twoja robota, a nie dekoracja. {cat} się niecierpliwi.",
        "{cat} prosi grzecznie ostatni raz. Potem już nie będzie grzecznie.",
        "Hej, {name}. {cats} zgłaszają awarię kibla. Potrzebny mechanik z łopatką.",
        "Kurwa, ta kuweta sama prosi o sprzątnięcie. {cat} też prosi. Nie olewaj.",
    ],
    2: [
        "Chcesz, żeby kotki chodziły z obsraną dupą? Sprzątnij kuwetę xd",
        "Kocie gówno samo się nie wyniesie, księżniczko. {cat} czeka. RUCHY.",
        "{cat} już patrzy, żeby zeszczać się na panele. I będzie mieć rację.",
        "{name}, to już nie kuweta, to jebane wysypisko. {cats} płaczą.",
        "{cats} srają w gówno, bo czystego miejsca już nie ma. Ogarnij to, kurwa.",
        "{cat} wchodzi do kuwety i wychodzi z obrzydzeniem. Kot. Z obrzydzeniem. Pomyśl o tym.",
        "Kuweta śmierdzi tak, że {cat} woli trzymać do jutra. Nie męcz kota, kurwa.",
        "Ile razy mam, kurwa, przypominać? {cats} czekają, {name}.",
        "{cat} stoi nad kuwetą jak turysta nad szambem. Zrób coś.",
        "Ten zapach to nie odświeżacz, {name}. To {cat} i twoje lenistwo.",
        "{cat} ma dziewięć żyć i w każdym ma gównianą obsługę. Popraw się.",
        "{cats} piszą petycję. Temat: nasza służba to śmierdzący leń.",
        "Gapisz się w telefon, a {cat} gapi się na ciebie. Z nienawiścią.",
        "{cat} szuka miejsca do srania. Podpowiedź: nie będzie to kuweta, jeśli jej nie ogarniesz.",
        "{cats} lubią już bardziej gówno w kuwecie niż ciebie. Zastanów się.",
        "Kuweta wygląda jak plaża po festiwalu. {cat} nie chce tam wchodzić. Sprzątaj, kurwa.",
        "{name}, łopatka nie gryzie. {cat} za to zaraz zacznie.",
        "Każde twoje „zaraz” to kolejna kupa na kupie. {cat} liczy.",
        "{cats} grzebią w piasku jak poszukiwacze złota. Złota tam nie ma, jest gówno.",
        "Ogarnij kuwetę, zanim {cat} ogarnie twoje łóżko.",
        "{cats} nie umieją przeklinać, więc zrobię to za nie: kurwa, sprzątaj.",
        "To nie jest prośba, {name}. {cat} stawia ultimatum.",
        "Nos ci odpadł, że nie czujesz? {cat} czuje. Kuweta, mordo.",
        "{cat} patrzy na ciebie z pogardą. W pełni zasłużoną.",
        "Jeszcze trochę i z kuwety zrobi się Mount Gówno. {cats} już tam nie wejdą.",
    ],
    3: [
        "Kurwa, {name}, ta kuweta jebie gorzej niż twoje gacie po nocce. {cat} się dusi. Rusz dupsko.",
        "{cat} rozważa nasranie ci na poduszkę. I wiesz co? Kibicuję.",
        "Jeszcze chwila i {cats} zaczną srać obok. I będą miały w chuj rację.",
        "Ja pierdolę, ile można? {cat} czeka, łopatka czeka, wszyscy czekają na ciebie.",
        "{cats} zakładają związek zawodowy. Pierwszy postulat: ogarnij to jebane gówno.",
        "Ta kuweta ma już własny ekosystem. {cat} boi się tam wejść. Wypierdol to.",
        "{name}, {cats} przynajmniej zakopują swoje gówno. Ty nawet tego nie umiesz.",
        "Dupa w troki i do kuwety, bo {cat} zaraz zrobi z mieszkania gnojownik.",
        "{cat} sika już na samym brzegu, bo w środku nie ma miejsca. Gratulacje, kurwa.",
        "Pierdolony smród dociera do kuchni. {cat} je w tym smrodzie. Wstyd.",
        "{cats} patrzą na ciebie jak na zdrajcę. Bo nim jesteś, dopóki kuweta jest pełna.",
        "{cats} mają już plan zemsty. Etap pierwszy: twoje buty.",
        "Przesuń jeszcze raz to powiadomienie, a {cat} przesunie dupą po twoim kocu.",
        "Ta kuweta to jebany pomnik twojego lenistwa. {cat} chętnie by go zburzył, ale nie ma rąk.",
        "Wiesz, co jest gorsze od kociego gówna? Kocie gówno, w którym {cat} musi stać. Ogarnij.",
        "{cat} trzyma kupę od rana, bo brzydzi się kuwety. Przez ciebie, kurwa.",
        "{cat} ma tylko łapki, a i tak ogarnia więcej niż ty.",
        "{cat} patrzy na twoje łóżko jak na nową, czystą kuwetę. Logiczne.",
        "Zapierdalaj do kuwety, zanim {cat} przyjdzie do ciebie z pretensjami. I z kupą.",
        "{cats} nie mają prawa głosu, ale mają prawo nasrać ci do kapcia. Pamiętaj.",
        "Nawet muchy nad kuwetą piszą skargę. {cat} podpisuje łapą. Sprzątnij to w pizdu.",
        "Twoje „za chwilę” trwa już dłużej niż kocia ciąża. {cats} mają dość.",
        "Jebać wymówki. Łopatka, worek, kosz. {cat} patrzy. Teraz.",
        "{cats} już nie miauczą, tylko warczą. Wiesz czemu.",
        "Ten syf w kuwecie jest bardziej ogarnięty niż ty. {cat} to widzi i ma cię za lamusa.",
    ],
    4: [
        "{ile_dni} spóźnienia. {cats} żyją w gównie, a ty udajesz, że nie czujesz.",
        "Kurwa mać, {name}! {cat} sra już na dywan i ma do tego pełne prawo.",
        "Gratulacje, {name}, w kuwecie wyhodowało się nowe życie. {cat} chce się wyprowadzić.",
        "{cats} oficjalnie mają cię w dupie. Tak samo jak ty ich kuwetę. {ile_dni}, serio, kurwa?",
        "To już nie przypomnienie, to interwencja w imieniu kotów. Łopatka. Teraz. Kurwa.",
        "{cat} właśnie wybiera, w które buty nasrać. Podpowiem: w twoje ulubione.",
        "Smród z kuwety ma już własny adres zameldowania. {cats} chcą eksmisji. Wypierdol go.",
        "{ile_dni} w gównie. {cats} szukają numeru do rzecznika praw zwierząt.",
        "Ja pierdolę, {name}. To już nie kuweta, to jebana zbrodnia. {cat} jest ofiarą.",
        "{cat} ma dość i sra, gdzie popadnie. Każda kupa poza kuwetą to twój podpis.",
        "{ile_dni} spóźnienia. Nawet karaluchy wyprowadziły się z obrzydzenia. {cats} nie mogą.",
        "Kuweta jest tak zasrana, że {cat} rozważa emigrację. Ulituj się, kurwa.",
        "{name}, wypierdalaj do tej kuwety, zanim {cats} wypierdolą cię z domu.",
        "Alarm biologiczny. Źródło: twoja kuweta. Ofiary: {cats}. Winny: {name}.",
        "{cats} już nie srają do kuwety. Srają na twoją godność. Zostało jej niewiele.",
        "{ile_dni} bez sprzątania. To nie lenistwo, to jebany rekord świata. {cat} nie bije brawa.",
        "Jeszcze dzień i ta kuweta dostanie własny PESEL. {cat} będzie jej sąsiadem.",
        "{cat} mówi: albo kuweta, albo twoja pościel. Wybierz i nie zjeb.",
        "Kurwa, ile można? {partner} czuje, {cats} czują, sąsiedzi czują. Tylko ty nie.",
        "Najgorszy dyżur w historii tego domu: {name}. {cats} to zapamiętają.",
        "Gówno w kuwecie ma już warstwy jak tort. {cats} nie mają dziś urodzin. Pokrój to łopatką.",
        "{cats} potrzebują czystego kibla, a nie archeologa. Rusz dupę TERAZ.",
        "{cat} wydaje ostatnie ostrzeżenie. Potem niespodzianka w twoich butach.",
        "{ile_dni}, {name}. {ile_dni}. {cats} cierpią. Kurwa. Mać.",
        "Sanepid, policja i {cats} są w drodze. Tylko jedno z nich jest prawdziwe, ale za to najgorsze.",
    ],
}

_EXTENSION = [
    "Wczoraj ci odpuszczono. Dziś nie ma chuja we wsi, {name}. {cat} czeka.",
    "Dzień zwłoki to nie urlop, leniu. {cats} odliczają minuty. Łopatka w łapę.",
    "{partner} dał(a) ci dzień luzu. {cat} nie da. Nie spierdol tego drugi raz.",
    "Przedłużenie się skończyło. {cat} pamięta i nie wybacza. Rusz dupę.",
    "Drugi dzień tego samego gówna. Dosłownie. {cats} mają dość, kurwa.",
    "Wczoraj było „nie dam rady”. Dziś {cat} pyta: a teraz dasz?",
    "Kredyt zaufania wykorzystany. {cats} chcą spłaty łopatką.",
    "Przedłużenie to nie abonament, {name}. Dziś sprzątasz, {cat} dopilnuje.",
    "{cats} czekały dodatkowy dzień. Nie każ im, kurwa, czekać kolejnego.",
    "Luz był wczoraj. Dziś jest gówno, {cat} i ty.",
    "{cats} liczą dni. Ty liczysz wymówki. Dziś koniec liczenia.",
    "Jedno przedłużenie to przypadek, dwa to styl życia. {cat} nie toleruje takiego stylu.",
    "{partner} był(a) miły(a). {cats} nie będą.",
    "Ile jeszcze tego luzu? Zero. {cat} mówi: kuweta, teraz.",
    "Wczorajsza wymówka już śmierdzi. Tak samo jak kuweta, w której {cat} musi stać.",
]

_PARTNER = [
    "{name} olewa kuwetę już {ile_dni}. {cats} proszą: pociśnij.",
    "Donos: kuweta dalej zajebana gównem, a dyżur ma {name}. {cat} cierpi.",
    "{cat} składa skargę: {ile_dni} w gównie, dyżurny: {name}. Zrób porządek.",
    "Info z frontu: {name} ma kuwetę głęboko w dupie. {cats} błagają o interwencję.",
    "{name} udaje, że nie widzi kuwety. Ty widzisz. {cat} też. Zrób awanturę.",
    "{cats} wzywają wsparcie. Dyżurny {name} nie ogarnia od {ile_dni}.",
    "Masz pełne prawo nawrzeszczeć. Kuweta stoi od {ile_dni}, winny: {name}.",
    "Raport: kuweta pełna, {cats} wściekłe, dyżurny {name} zaginiony w akcji.",
    "Czujesz smród? {cat} czuje go bardziej. Winny: {name}.",
    "{name} przegrywa z kocim gównem już {ile_dni}. {cats} liczą na ciebie, kurwa.",
    "Kuweta płacze, {cats} płaczą, a {name} ma to w dupie. Pogadaj z tym leniem.",
    "Kocia straż donosi: {name} miga się od kuwety od {ile_dni}. {cat} potwierdza.",
]

_CLEANED = [
    "Kuweta ogarnięta, robotę zrobił(a) {name}. {cats} zadowolone. Twoja kolej za {ile_dni}.",
    "{cats} dziękują, kurwa, w końcu czysto. Za {ile_dni} twoja kolej, nie spierdol.",
    "Czysto jak łza. {cat} już testuje. Twój dyżur za {ile_dni}, zapamiętaj.",
    "{name} wygrywa z gównem. {cat} świętuje świeżą kupą. Twoja runda za {ile_dni}.",
    "Kuweta lśni. {cat} już się szykuje, żeby to zjebać. Twój dyżur za {ile_dni}.",
    "Gówno wyniesione, sprawca: {name}. Następny w kolejce: ty, za {ile_dni}.",
    "Cud się zdarzył: czysta kuweta. {cats} nie wierzą. Za {ile_dni} twoja kolej na cud.",
    "Ogarnięte. Masz {ile_dni} spokoju, potem {cats} znowu wołają ciebie.",
    "Kocia toaleta odświeżona, {name} na medal. Szykuj łopatkę na za {ile_dni}.",
    "Czysto! {cats} mogą znowu srać z godnością. Twoja zmiana za {ile_dni}.",
    "Raport: kuweta czysta, {cat} mruczy. Za {ile_dni} twoja szansa na chwałę.",
    "Odhaczone. Za {ile_dni} to ty grzebiesz w gównie. Do tego czasu smacznej kawy.",
]

_EXT_REQUEST = [
    "{name} prosi o dzień zwłoki z kuwetą. Dajesz luz czy każesz zapierdalać?",
    "{name} jęczy, że dziś nie da rady z kuwetą. {cats} patrzą na ciebie. Zgoda czy niech zapierdala?",
    "Prośba od: {name}. {cats} mają cierpieć jeszcze dzień. Litość czy łopatka?",
    "{name} błaga o +1 dzień. {cats} mówią nie, a ty?",
    "{name} chce odroczenia kuwety. {cat} czeka na wyrok. Ty go wydajesz.",
    "Wniosek o przedłużenie, autor: {name}. Zatwierdzasz czy jebać to?",
    "{name} twierdzi, że dziś nie da rady. {cat} w to nie wierzy. A ty?",
    "Kuweta czeka, {name} prosi o dzień. {cats} liczą na surowy wyrok.",
]

DEFAULT_MESSAGES: list[tuple[str, int, str]] = (
    [("reminder", level, text) for level, texts in _REMINDER.items() for text in texts]
    + [("extension", 0, t) for t in _EXTENSION]
    + [("partner", 0, t) for t in _PARTNER]
    + [("cleaned", 0, t) for t in _CLEANED]
    + [("ext_request", 0, t) for t in _EXT_REQUEST]
)

LEVEL_NAMES = {1: "średnio", 2: "z jajem", 3: "ostro", 4: "bez litości"}
