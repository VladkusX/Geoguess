# Kuweta Patrol

Aplikacja na Androida, która przypomina o sprzątaniu kuwety. Dwie osoby, grafiki zmianowe, wulgarne powiadomienia, serwer na homelabie.

```
Telefon A ─┐                    ┌─ Telefon B
           └─ HTTPS (cloudflared) ┘
                    │
        kuweta-server (Docker, SQLite)
        ├ grafiki, dyżury, prośby o przedłużenie
        ├ co minutę sprawdza, czy kogoś trzeba pomęczyć
        └ wysyła powiadomienia przez stałe połączenie (SSE), bez Google/Firebase
```

## Jak to działa

- **Dyżur co 2 dni** (zmienia admin). Po kliknięciu „Ogarnięte” dyżur przechodzi na drugą osobę, a ta dostaje powiadomienie.
- **Przypomnienia** lecą w dniu dyżuru co 30 minut (osobno ustawiane dla każdej osoby), tylko gdy osoba nie pracuje i nie śpi.
- **Grafik**: cykl dni (R/P/N/wolne) i data startu. Gotowe szablony:
  - pięciobrygadówka: `R R P P N N - - - -`
  - pn–pt, 3 zmiany rotowane co tydzień: `N×5 - - P×5 - - R×5 - -`
  - do tego wyjątki na pojedyncze dni (urlop, L4, zamiana).
- **Kiedy cisza**:
  - praca + 30 min dojazdu przed zmianą,
  - po popołudniówce i nocce jeszcze **godzina męczenia**, potem sen (po nocce 7 h, po popołudniówce cisza nocna 23–8),
  - po rannej zmianie nie ma snu.
  - Wszystkie wartości da się zmienić w zakładce Grafik.
- **Eskalacja**: pierwsze przypomnienie dnia ma poziom 1, drugie poziom 2, trzecie poziom 3, a od czwartego leci już najostrzej (poziom 4). Dzień po terminie zaczyna się od poziomu 3, a druga osoba dostaje donos. Admin może ograniczyć maksymalny poziom.
- **Przedłużenie**: „Nie dam rady → +1 dzień” wysyła prośbę. Druga osoba klika Zgoda/Odmowa (także prosto z powiadomienia). Do czasu zgody przypomnienia lecą dalej. Po zgodzie termin przesuwa się o dzień, a następnego dnia teksty wypominają przedłużenie.
- **Streak**: liczba sprzątnięć z rzędu na czas i bez przedłużania. Na ekranie głównym jest też kolejny gracz i za ile dni.
- **Koty**: admin dodaje imiona kotów, a powiadomienia ich używają („Beżi już patrzy, żeby zeszczać się na panele”). W tekstach: `{cat}` to losowy kot, `{cats}` to wszystkie koty.
- **Kalendarz** w zakładce Grafik: cykl ustawiasz raz i powtarza się sam. Urlop, zamianę czy nadgodziny poprawiasz, stukając dzień. Zmiana zapisuje się od razu, a każdy edytuje swój grafik.
- **Dźwięki**: domyślnie przypomnienia miauczą, a wiadomości od drugiej osoby robią krótkie „mrrp”. Admin może wgrać własne pliki (mp3/ogg/wav/m4a, do 2 MB) w zakładce Admin → Dźwięki powiadomień i przypisać je do przypomnień albo wiadomości. Oba telefony pobiorą je same. Dźwięk gra aplikacja, więc w trybie cichym i w trybie Nie przeszkadzać jest cisza (zostają wibracje).
- **Ranking miesiąca**, **widżet** na ekran główny, **własne teksty** dopisywane przez admina.

## 1. Serwer na homelabie

```bash
mkdir kuweta && cd kuweta
# skopiuj kuweta/server/docker-compose.yml z repo
echo "KUWETA_ADMIN_CODE=$(openssl rand -hex 4)" > .env
cat .env                      # zapamiętaj kod admina
docker compose up -d
curl http://localhost:8787/api/health
```

Obraz `ghcr.io/vladkusx/kuweta-server:latest` buduje GitHub Actions. Jeśli pakiet jest prywatny, zrób `docker login ghcr.io` albo zbuduj lokalnie (`build: .` w compose, w katalogu `kuweta/server`).

Baza leży w `./data/kuweta.db`, wystarczy ją backupować.

### cloudflared

Dodaj trasę w swoim tunelu, np. w `config.yml`:

```yaml
ingress:
  - hostname: kuweta.twojadomena.pl
    service: http://localhost:8787
```

albo w panelu Zero Trust → Tunnels → Public Hostname: `kuweta.twojadomena.pl` → `HTTP` → `localhost:8787`.

Nie włączaj Cloudflare Access (logowania) dla tej domeny, bo aplikacja się nie przebije. Zabezpieczeniem są tokeny urządzeń i kod parowania.

## 2. Aplikacja

APK buduje się w GitHub Actions (workflow **Kuweta Patrol**). Gotowy plik jest w zakładce **Releases** repo (`kuweta-NN`). Otwórz link na telefonie, pobierz i zainstaluj, zezwalając na instalację z nieznanych źródeł. Kolejne wersje instalują się jako aktualizacja.

Parowanie:

1. **Admin**: adres serwera, imię, kod admina z `.env`.
2. Admin → zakładka **Admin** → „Kod dla drugiej osoby”.
3. **Druga osoba**: adres serwera, imię, ten 6-znakowy kod (ważny 24 h).
4. Ustawcie grafiki w zakładce **Grafik** i sprawdźcie podgląd 14 dni.
5. Kliknijcie „Test powiadomienia”.

### Żeby powiadomienia dochodziły

Aplikacja trzyma stałe połączenie z serwerem (cicha ikonka kota na pasku), dlatego system nie może jej ubijać.

- **POCO F7 / Xiaomi (HyperOS)**: Ustawienia aplikacji → Oszczędzanie baterii → **Bez ograniczeń**, włącz **Autostart**, w ostatnich aplikacjach przytrzymaj Kuwetę i daj **kłódkę**.
- **Samsung S21 Ultra**: Ustawienia → Bateria → Limity użycia w tle → **Aplikacje nigdy nieusypiane** → dodaj Kuweta Patrol.

Na ekranie głównym aplikacji jest karta z przyciskami do tych ustawień.

## Budowanie lokalnie

```bash
cd kuweta/android && ./gradlew assembleRelease     # potrzebny Android SDK
cd kuweta/server && pip install -r requirements.txt pytest httpx && python -m pytest tests
```

## Klucz podpisu APK

Klucz nie leży w repo. GitHub Actions bierze go z sekretów repozytorium (Settings → Secrets and variables → Actions):

- `KUWETA_KEYSTORE_B64`: plik keystore zakodowany w base64,
- `KUWETA_KEYSTORE_PASSWORD`: hasło do keystore i klucza (alias `kuweta`).

Nowy klucz:

```bash
keytool -genkeypair -keystore kuweta.keystore -alias kuweta -keyalg RSA -keysize 2048 -validity 20000 -dname "CN=Kuweta Patrol"
base64 -w0 kuweta.keystore    # wynik wklej jako KUWETA_KEYSTORE_B64
```

Trzymaj kopię pliku i hasła poza GitHubem. Bez tego samego klucza nowe wersje nie zainstalują się jako aktualizacja i trzeba będzie odinstalować aplikację.
