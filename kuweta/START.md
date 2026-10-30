# Kuweta Patrol: start dla nowej sesji

Przeczytaj w całości przed pierwszą zmianą. Szczegóły dla użytkownika są w `kuweta/README.md`.

## Co to jest

Aplikacja na Androida dla dwóch osób, która wulgarnie przypomina o sprzątaniu kuwety. Serwer stoi na homelabie użytkownika.

- **Repo:** `VladkusX/Geoguess`, gałąź `claude/cat-litter-reminder-app-wqblc9`. Cały projekt jest w `kuweta/`. Reszta repo to niezwiązana aplikacja Geoguess (Vue), której nie ruszaj.
- **Pull requesta nie ma**, gałąź nie jest scalona z `master`. Nie zakładaj PR bez prośby.
- `kuweta/server`: Python 3.12, FastAPI, SQLite (`app/core.py` logika, `app/schedule.py` grafiki, `app/messages.py` teksty, `app/main.py` HTTP + SSE), testy w `tests/`.
- `kuweta/android`: Kotlin, Jetpack Compose, minSdk 26, target/compile 35, AGP 8.7.3, Kotlin 2.0.21, Gradle 8.11.1 (wrapper w repo).
- `.github/workflows/kuweta.yml`: testy serwera, obraz Dockera, APK, release.

## Budowanie i wydawanie

- **APK nie da się zbudować w kontenerze chmurowym**, bo `dl.google.com` jest zablokowany i nie ma Android SDK. Buduje wyłącznie GitHub Actions.
- Każdy push zmieniający `kuweta/**` uruchamia workflow:
  1. `server-tests` (pytest),
  2. `server-image`, czyli obraz `ghcr.io/vladkusx/kuweta-server:latest` (amd64 + arm64),
  3. `apk`, czyli APK podpisane kluczem z sekretów i prerelease z tagiem `kuweta-<run_number>`, plik `kuweta-patrol-N.apk`.
- Sekrety repo: `KUWETA_KEYSTORE_B64` i `KUWETA_KEYSTORE_PASSWORD` (alias `kuweta`). Bez nich job `apk` celowo kończy się błędem.
  - **Nigdy nie commituj keystore.** Kopię pliku i hasła ma użytkownik.
  - Stary klucz leży publicznie w historii repo. `kuweta-1` i `kuweta-2` są nim podpisane i nie należy ich używać.
- Ten sam klucz oznacza zwykłą aktualizację: instalacja na starą wersję, bez odinstalowania i bez parowania.
- GitHub sortuje release'y tekstowo (`kuweta-9` nad `kuweta-16`). Użytkownikowi zawsze podawaj bezpośredni link do najwyższego numeru.
- Po pushu sprawdź wynik runu (narzędzia `mcp__github__actions_*`, logi przez `get_job_logs`). Dopiero potem dawaj link.
- Lokalne testy serwera: `cd kuweta/server && pip install -r requirements.txt pytest httpx && python -m pytest -q tests` (ostatnio 19/19).

## Serwer na homelabie (maszyna `100tb`)

- Katalog `~/kuweta`: `docker-compose.yml` (obraz z GHCR), `.env` z `KUWETA_ADMIN_CODE`, dane w `~/kuweta/data` (`kuweta.db`, `sounds/`).
- Port 8787. Strefa czasowa `Europe/Warsaw`.
- Aktualizacja po zmianie serwera: `cd ~/kuweta && docker compose pull && docker compose up -d`
- Sprawdzenie: `curl http://localhost:8787/api/health`
- **cloudflared:** Public Hostname kieruje na `localhost:8787`. **Bez Cloudflare Access**, bo aplikacja trzyma stałe połączenie SSE i nie przejdzie przez logowanie. Zabezpieczeniem są tokeny urządzeń i kody parowania.
- Kod admina nie jest znany tej sesji i nie trzeba go znać.

## Jak działa aplikacja (najważniejsze decyzje)

- **Push bez Firebase.** Usługa pierwszoplanowa (`specialUse`) trzyma SSE na `/api/events?after=<id>`, a serwer wysyła ping co 25 s. Watchdog w WorkManagerze co 15 min.
  - Kanał usługi `service_hidden` ma od Androida 13 `IMPORTANCE_NONE`, czyli brak widocznego powiadomienia „czuwa”. Użytkownik tak chciał.
- **Dźwięki gra aplikacja (`SoundPlayer`), a nie kanał,** bo dźwięku kanału nie da się zmienić. Admin wgrywa pliki (do 2 MB) i przypisuje je do „przypomnień” i „od drugiej osoby”. Domyślnie `raw/meow.ogg` i `raw/mrrp.ogg` (syntetyczne).
- **Dyżur:** domyślnie co 2 dni, na zmianę. Kliknięcie „Ogarnięte” przekazuje dyżur drugiej osobie i wysyła jej powiadomienie.
- **Przedłużenie:** +1 dzień, druga osoba musi się zgodzić. Do zgody przypomnienia lecą dalej, a następnego dnia teksty wypominają przedłużenie.
- **Przypomnienia:** co N minut (osobno dla każdej osoby, domyślnie 30), tylko gdy osoba może je dostać:
  - nie w pracy ani 30 min przed zmianą (dojazd),
  - po popołudniówce i nocce jeszcze 60 min „męczenia”, potem sen (po nocce 7 h, poza tym cisza nocna 23–8),
  - po rannej zmianie nie ma snu.
- **Eskalacja:** poziom = które przypomnienie dnia (1., 2., 3., od 4. poziom 4). W dniu po terminie start od 3, a druga osoba dostaje donos. Teksty z `{ile_dni}` idą tylko przy faktycznym spóźnieniu.
- **Teksty:** `app/messages.py`, 147 sztuk, losowane od najdawniej użytych (bez powtórek).
  - Podbicie `SEED_VERSION` kasuje wszystkie wbudowane (`builtin = 1`) i wgrywa nowe. Teksty admina (`builtin = 0`) zostają.
  - Zmienne: `{cat}`, `{cats}`, `{name}`, `{partner}`, `{ile_dni}`, `{days}`. Imiona tylko w mianowniku.
- **Grafiki:** cykl kodów R/P/N/- od daty „dzień 1 cyklu”, plus wyjątki na pojedyncze dni (kalendarz w zakładce Grafik).
  - Admin: pięciobrygadówka `R R P P N N - - - -`.
  - Druga osoba: pn–pt, rotacja tygodniowa `N×5 - - P×5 - - R×5 - -`.
  - Godziny zmian 6–14, 14–22, 22–6.
- **Motywy:** Brutal (domyślny, żółto-czarny), Arcade (pikselowy), Pastel, Nocka (ciemny), Piekło (czerń i krew, gotyk). Wybór na ekranie Start, osobno na każdym telefonie. Widżet przyjmuje kolory motywu.
  - Kolory czyta się przez gettery `Yellow`/`Ink`/`Paper`/... z `ThemeState`, więc nie twórz statycznych map kolorów (patrz `SHIFT_COLORS` jako getter).
  - Rozmiary czcionek ustawiaj przez `dsp()`/`bsp()`.

## Użytkownicy i sprzęt

- **Admin:** Vlad, telefon POCO F7 (HyperOS): Autostart, bateria „Bez ograniczeń”, kłódka w ostatnich.
- **Druga osoba:** Samsung S21 Ultra: „Aplikacje nigdy nieusypiane”.
- Mają koty, imiona wpisuje admin w aplikacji (np. „Beżi”).

## Zasady użytkownika (ważne)

- **Pisz po polsku.** Godziny podawaj w czasie polskim (Europe/Warsaw), nie w UTC.
- **Polecenia do wklejenia na serwerze podawaj zawsze w JEDNEJ linii.**
- **Przed wrzuceniem nowego projektu na GitHub zapytaj:** czy w ogóle, i czy repo publiczne czy prywatne. To repo jest publiczne, użytkownik planuje je ukryć.
- **Teksty mają być naprawdę wulgarne** (poziom 1 już średnio ostry). Grzeczne teksty „dla 4-latków” zostały odrzucone. Imiona kotów mają padać często.
- **Nie twierdź, że coś działa na telefonie.** Aplikacji nie da się tu uruchomić. Mów wprost, co jest sprawdzone (testy serwera, kompilacja w CI), a co nie, i proś o zrzuty ekranu.
- **UI musi działać z klawiaturą:** godziny wybierane z zegara, `imePadding`, długie teksty z `weight` i wielokropkiem. Wcześniejsze błędy: pola pod klawiaturą, karta dźwięków rozjechana przez długą nazwę pliku.
- Bez identyfikatorów modelu w commitach. Commity po polsku, opisowe.

## Stan na 30.10.2026 wieczór

- **Ostatnia wersja:** APK `kuweta-16` (motywy i widżet w motywach), commit `0637dfd`. Serwer: obraz `latest` z tej samej gałęzi. Użytkownik zrobił `pull` po zmianie eskalacji.
- **Otwarte sprawy:**
  - Zrzuty ekranu motywów Arcade i Piekło. Pikselowa czcionka może się nie mieścić, popraw, co wyjdzie.
  - **Zegarek:** użytkownik chce miauczenia na zegarku i mówi, że jego zegarek obsługuje dźwięk. Czekamy na dokładny model. Jeśli to Wear OS, trzeba zrobić osobną małą aplikację na zegarek, bo przekazane powiadomienie nie niesie pliku dźwięku.
  - Sprawdzić, czy na POCO przypomnienia dochodzą po 30+ min blokady, przy ukrytym powiadomieniu usługi. Jeśli nie, przywrócić widoczny kanał.
  - Parowanie drugiej osoby i jej grafik: nie wiadomo, czy już zrobione.
  - Propozycja uporządkowania release'ów (oznaczenie „Latest”, usunięcie starych): czeka na decyzję.
  - Z listy pomysłów użytkownik wybrał tylko widżet w kolorach motywu (zrobione). Reszta (zdjęcie jako dowód, osiągnięcia, zapas żwirku, tryb urlopu, raport tygodniowy) odrzucona na razie.
