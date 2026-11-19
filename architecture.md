# Mikroserwis „Atrakcje” – dokumentacja architektoniczna

| | |
|---|---|
| **Serwis** | 1. Atrakcje |
| **Wersja dokumentu** | 0.3 (szkic) |
| **Data** | 2026-11-19 |
| **Klient serwisu** | 5. Planer i integracja |
| **Zależności** | 4. Warunki dnia, 3. Transport, opcjonalnie Discovery |
| **Stos** | Java 25, Spring Boot 4.1, JPA, Spring Security, PostgreSQL, Flyway |

---

## Zakres demonstracyjny

Aplikacja uruchamiana przez `docker compose up --build` używa domyślnie profili `local,mock`. Docker publikuje port kontenera 8001 na porcie hosta 8000 na wszystkich interfejsach. Katalog czterech atrakcji jest przechowywany w pamięci, a profil `mock` udostępnia stałą prognozę i symetryczne odcinki z punktu `start` według tabeli Transportu. W tym wariancie health checki sprawdzają stan aplikacji, a datę walidujemy w strefie `Europe/Warsaw`.

Docelowe połączenie z bazą opisuje [kontrakt PostgreSQL](contracts/database.md). Specyfikacja [OpenAPI zależności](contracts/downstream.openapi.yaml) opisuje wywołania z [kontraktu Transportu](contract_transport.md), [kontraktu Pogody](kontrakt-pogoda.md) i [kontraktu Discovery](KONTRAKT_discovery.txt). Etapy podłączenia zależności opisuje [plan implementacji](implementation-plan.md).

---

## 1. Cel i zakres

Serwis **Atrakcje** udostępnia katalog atrakcji (kategorie, lokalizacje, ceny, czasy zwiedzania, godziny otwarcia, informacja „pod dachem”) i na żądanie Planera zwraca **propozycje atrakcji na dany dzień**. Każda propozycja zawiera:

- dopasowanie do pogody (na podstawie serwisu Warunki dnia),
- czas i koszt dotarcia z punktu startowego oraz szacowany powrót (na podstawie serwisu Transport),
- godziny otwarcia w danym dniu,
- łączny szacunek czasu i kosztu dla podanej liczby osób.

### 1.1. Poza zakresem

| Odpowiedzialność | Kto ją realizuje |
|---|---|
| Wybór atrakcji do planu, kolejność wizyt | Planer |
| Kontrola budżetu całego dnia i okna 10:00–16:00 | Planer (Atrakcje zwracają godziny otwarcia, żeby mógł to sprawdzić) |
| Posiłki i lokale | Gastronomia |
| Wyliczanie odcinków między lokalizacjami | Transport |
| Prognozy pogody | Warunki dnia |
| Uwierzytelnianie użytkowników końcowych | Planer / gateway |

---

## 2. Kontekst w systemie

```mermaid
flowchart LR
    P[5. Planer i integracja] -- "propozycje" --> A[1. Atrakcje]
    P -- "posiłki" --> G[2. Gastronomia]
    P -- "pozostałe odcinki" --> T[3. Transport]
    A -- "pogoda" --> W[4. Warunki dnia]
    A -- "od startu / powrót" --> T
    G -- "od atrakcji" --> T
```

Strzałka prowadzi od klienta do dostawcy. Atrakcje są **klientem** Warunków dnia i Transportu oraz **dostawcą** dla Planera.

### 2.1. Rola w procesie składania planu

1. **Planer → Atrakcje:** prośba o propozycje na dzień → wybór pierwszej atrakcji. ← *ten serwis*
2. Planer → Gastronomia: posiłki z dojazdem od pierwszej atrakcji (Planer przekazuje `location.id` zwrócone przez Atrakcje).
3. Planer → Transport: kolejne odcinki obsługiwane przez Transport. Bieżący kontrakt obsługuje wyłącznie odcinki od lub do `start` oraz przejazd w obrębie jednego punktu.
4. Planer: kontrola budżetu i godzin.

---

## 3. API

Identyfikatory pokazowego katalogu to `ATR01`, `ATR02`, `ATR03` i `ATR04`. Pole `location.id` nadal przyjmuje identyfikatory lokalizacji Transportu `attr-01`–`attr-04`.

Bazowy prefiks: **`/api/v1`** (obowiązkowy dla wszystkich endpointów biznesowych). Format: JSON, UTF-8. Waluta: PLN, kwoty zaokrąglane do 2 miejsc po przecinku (HALF_UP).

### 3.1. `GET /api/v1/attractions/proposals` – propozycje na dzień

#### Parametry wejściowe

| Parametr | Typ | Wymagany | Domyślnie | Opis |
|---|---|---|---|---|
| `date` | `YYYY-MM-DD` | tak | – | Data planu, nie wcześniejsza niż dzisiejsza; przekazywana do Warunków dnia |
| `maxCost` | liczba ≥ 0 | nie | brak limitu | Maks. łączny koszt dla grupy (bilety + dojazd + powrót) |
| `mode` | `walk` \| `transit` \| `car` | tak | – | Sposób podróży; mapowanie na tryby Transportu w sekcji 4.2 |
| `people` | int 1–20 | nie | `1` | Liczba osób |
| `category` | string (lista kodów po przecinku) | nie | wszystkie | Filtr kategorii, np. `museum,park` |
| `startLat` | float [-90, 90] | tak | – | Szerokość geograficzna punktu startowego |
| `startLng` | float [-180, 180] | tak | – | Długość geograficzna punktu startowego |
| `limit` | int 1–50 | nie | `10` | Maksymalna liczba propozycji |

Przykład:

```
GET /api/v1/attractions/proposals?date=2027-06-13&maxCost=200&mode=transit&people=3&category=museum&startLat=52.2297&startLng=21.0122&limit=5
```

#### Odpowiedź `200 OK`

```json
{
  "date": "2027-06-13",
  "start": {
    "lat": 52.2297,
    "lng": 21.0122,
    "resolvedLocationId": "start"
  },
  "mode": "transit",
  "people": 3,
  "weather":   { "condition": "rain", "tempC": 14, "source": "ok" },
  "transport": { "source": "ok" },
  "proposals": [
    {
      "id": "ATR01",
      "name": "Muzeum Narodowe",
      "category": "museum",
      "location": { "id": "attr-01", "lat": 52.2316, "lng": 21.0247 },
      "indoor": true,
      "weatherFit": "good",
      "visitDurationMin": 90,
      "openingHours": { "open": "10:00", "close": "18:00" },
      "ticket":    { "perPerson": 25.00, "total": 75.00 },
      "fromStart": { "timeMin": 9, "costPerPerson": 4.80, "costTotal": 14.40 },
      "toStart":   { "timeMin": 9, "costPerPerson": 4.80, "costTotal": 14.40 },
      "estimate":  { "totalTimeMin": 108, "totalCost": 103.80, "complete": true }
    }
  ]
}
```

Przy niedostępnych zależnościach (patrz 6):

```json
"weather":   { "condition": null, "tempC": null, "source": "unavailable" },
"transport": { "source": "unavailable" }
```

#### Opis pól wyjściowych

| Pole | Opis |
|---|---|
| `start.resolvedLocationId` | Lokalizacja z katalogu przypisana do współrzędnych startu (patrz 5.2) |
| `weather` | Warunki dnia użyte do doboru; `source`: `ok` \| `unavailable` |
| `transport.source` | `ok` \| `unavailable` – czy uzyskano odpowiedź Transportu. Gdy kandydatów brak, flaga pozostaje `ok` i zapytanie jest pomijane |
| `location.id` | Identyfikator wspólny z Transportem; Planer przekazuje go do Gastronomii i Transportu |
| `weatherFit` | `good` \| `ok` \| `poor` \| `null` (gdy brak pogody) |
| `openingHours` | Godziny otwarcia w dniu `date`; `null`, gdy atrakcja nie ma zdefiniowanych godzin (traktowana jako otwarta cały dzień, patrz 5.4) |
| `ticket.perPerson` / `ticket.total` | Cena biletu dla jednej osoby / × `people` |
| `fromStart` | Dojazd **ze startu do atrakcji** w trybie `mode`: `timeMin` – czas w minutach, `costPerPerson` – koszt jednej osoby, `costTotal` – koszt grupy |
| `toStart` | Szacowany **powrót z atrakcji do startu**; ten sam czas i koszt co w `fromStart` dzięki symetrii odcinków |
| `estimate.totalTimeMin` | `fromStart.timeMin + visitDurationMin + toStart.timeMin` (brakujące składniki pomijane) |
| `estimate.totalCost` | `ticket.total + fromStart.costTotal + toStart.costTotal` (brakujące składniki pomijane) |
| `estimate.complete` | `false`, gdy `fromStart` lub `toStart` jest `null` – szacunek obejmuje wtedy tylko znane składniki |

`fromStart` / `toStart` mają wartość `null` przy niedostępności Transportu. Status `NO_CONNECTION` wyklucza daną atrakcję z propozycji.

**Zasady liczenia kosztu dojazdu dla grupy:**

| `mode` | `costPerPerson` | `costTotal` |
|---|---|---|
| `walk` | `0` | `0` |
| `transit` | `costGrosze / 100` (4,80 PLN za odcinek) | `costPerPerson × people` |
| `car` | `costGrosze / 100` (taxi, koszt jednej osoby) | `costPerPerson × people` |

`estimate` to szacunek dla wariantu „tylko ta jedna atrakcja”. Finalny budżet i czas dnia liczy Planer.

### 3.2. Endpointy pomocnicze

| Endpoint | Opis | Zależności |
|---|---|---|
| `GET /api/v1/attractions` | Katalog z filtrami `category`, `indoor`; paginacja `page` (od 0), `size` (1–100, domyślnie 20) | brak |
| `GET /api/v1/attractions/{id}` | Szczegóły atrakcji wraz z godzinami otwarcia | brak |
| `GET /api/v1/categories` | Słownik kategorii do formularza Planera | brak |
| `GET /actuator/health` | Stan serwisu (patrz 6.1) | baza; W i T tylko informacyjnie |

Odpowiedź katalogu (paginacja):

```json
{ "items": [ ... ], "page": 0, "size": 20, "totalItems": 134, "totalPages": 7 }
```

### 3.3. Błędy

Format błędu:

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "Parametr 'mode' musi być jednym z: walk, transit, car", "field": "mode" } }
```

| Kod HTTP | `code` | Kiedy |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Brak wymaganego parametru, zły format lub data w przeszłości, współrzędne lub `people`/`limit`/`size` poza zakresem, nieznany `mode` lub kategoria |
| 401 | `UNAUTHORIZED` | Brak lub nieprawidłowy token (patrz 9) |
| 403 | `FORBIDDEN` | Token bez wymaganego uprawnienia |
| 404 | `NOT_FOUND` | `GET /attractions/{id}` dla nieistniejącego ID |
| 422 | `START_NOT_RESOLVED` | Współrzędne startu zbyt daleko od jakiejkolwiek znanej lokalizacji (tylko w wariancie 5.2) |
| 200 | – | Brak wyników po filtrach → `proposals: []` |
| 500 | `INTERNAL_ERROR` | Błąd serwisu (bez szczegółów technicznych w odpowiedzi) |

Niedostępność zależności **nie** powoduje błędu – serwis degraduje odpowiedź (patrz 6).

---

## 4. Kontrakty z zależnościami

Bez profilu `mock` Atrakcje korzystają z klientów HTTP. Profil `mock` udostępnia przykładowe dane, a testy WireMock sprawdzają format wywołań do zależności.

### 4.1. Warunki dnia

Źródłem kontraktu jest [kontrakt Pogody](kontrakt-pogoda.md). Atrakcje korzystają z wariantu jednodniowego, przekazując datę i współrzędne startu podane przez Planer.

```
GET /api/weather/day?lat=52.2297&lon=21.0122&date=2026-11-20
```

```json
{
  "location": { "name": null, "lat": 52.2297, "lon": 21.0122 },
  "days": [
    { "date": "2026-11-20", "tempMin": 2.1, "tempMax": 7.4,
      "precipitationMm": 3.2, "windKph": 18.0, "category": "RAIN" }
  ],
  "stale": false
}
```

Klient mapuje `SUNNY`, `CLOUDY`, `RAIN`, `SNOW`, `STORM` na `sun`, `cloudy`, `rain`, `snow`, `storm`. Pole `tempC` to średnia `tempMin` i `tempMax` zaokrąglona do liczby całkowitej metodą `HALF_UP`. Dopuszczalne dane z `stale: true` są przetwarzane tak samo. Nieznana kategoria, brak prognozy dla żądanej daty lub błąd HTTP oznaczają niedostępność danych. Dla dat po dziś + 15 dni klient pomija wywołanie i zwraca tę samą informację o niedostępności. Adres i port dostawcy wymagają konfiguracji.

### 4.2. Transport

Źródłem kontraktu jest [contract_transport.md](contract_transport.md). Atrakcje wysyłają jedno `POST /api/v1/routes` z tablicą od 1 do 500 par pól `fromLocationId` i `toLocationId`. Tryby Planera `walk`, `transit` i `car` mapują się odpowiednio na `WALK`, `PUBLIC_TRANSPORT` i `CAR`.

```json
{
  "mode": "PUBLIC_TRANSPORT",
  "pairs": [{ "fromLocationId": "start", "toLocationId": "attr-01" }]
}
```

```json
{
  "mode": "PUBLIC_TRANSPORT",
  "results": [
    { "fromLocationId": "start", "toLocationId": "attr-01", "status": "OK", "durationMin": 9, "costGrosze": 480 }
  ]
}
```

Odpowiedź zachowuje liczbę i kolejność par. Symetria pozwala użyć jednego wyniku dla dojazdu i powrotu. Przy `NO_CONNECTION` odpowiedź ma kod `200`, a wynik tej pary pomija pola `durationMin` i `costGrosze`. Błędy `400` oraz `500` zawierają odpowiednio `BAD_REQUEST` i `INTERNAL_ERROR` w polu `error`, a szczegóły w `message`. Zalecany timeout to 2 s.

### 4.3. Discovery

Po ustawieniu `APP_DISCOVERY_URL` klienci pytają o IP przez `GET /services/{name}`. Zachowują schemat i port skonfigurowanego adresu usługi. Przy braku wpisu, błędzie lub przekroczeniu 2 s używają skonfigurowanego adresu awaryjnego. Jeśli ustawiono też `APP_DISCOVERY_ADVERTISE_IP`, aplikacja cyklicznie wysyła `POST /services` z nazwą `attractions` i IP hosta. Dzięki temu wpis jest odtwarzany po restarcie rejestru. Ponieważ Discovery nadpisuje wpis dla tej samej nazwy, przy wielu replikach należy publikować wspólne IP punktu wejściowego. Pełny format rejestru opisuje [kontrakt Discovery](KONTRAKT_discovery.txt).

---

## 5. Logika przetwarzania

### 5.1. Przepływ `GET /api/v1/attractions/proposals`

```mermaid
sequenceDiagram
    participant P as Planer
    participant A as Atrakcje
    participant C as Cache katalogu
    participant W as Warunki dnia
    participant T as Transport

    P->>A: GET /api/v1/attractions/proposals?date&mode&people&category&startLat&startLng&maxCost&limit
    A->>A: uwierzytelnienie, walidacja parametrów
    A->>C: lokalizacje, atrakcje, godziny otwarcia (przy pustym cache → PostgreSQL)
    C-->>A: katalog
    A->>A: porównanie współrzędnych z punktem start → resolvedLocationId
    A->>A: filtr (active, category, otwarte w dniu), MAX_CANDIDATES najbliższych
    par równolegle
        A->>W: GET /api/weather/day?lat&lon&date
        W-->>A: days (category, tempMin, tempMax)
    and
        A->>T: POST /api/v1/routes (start → kandydat, odcinek symetryczny)
        T-->>A: results (status, durationMin, costGrosze)
    end
    A->>A: weatherFit, koszty grupy, estimate
    A->>A: filtr maxCost, sortowanie, limit
    A-->>P: 200 { weather, transport, proposals[] }
```

Wywołania W i T są wykonywane poza transakcją bazodanową.

### 5.2. Rozwiązanie punktu startowego

Transport ma jeden punkt startowy i końcowy o identyfikatorze `start`. Atrakcje porównują współrzędne Planera z katalogowymi współrzędnymi tego punktu metodą haversine. Odległość większa niż `APP_START_MAX_DISTANCE_M` (domyślnie 1000 m) albo brak punktu `start` w katalogu skutkują odpowiedzią `422 START_NOT_RESOLVED`. Po udanym dopasowaniu `resolvedLocationId` ma wartość `start`.

### 5.3. Dopasowanie do pogody (`weatherFit`)

| `condition` | `indoor = true` | `indoor = false` |
|---|---|---|
| `sun` | `ok` | `good` |
| `cloudy` | `good` | `good` |
| `rain` | `good` | `poor` |
| `snow` | `good` | `poor` |
| `storm` | `good` | `poor` |
| brak danych / nieznana wartość | `null` | `null` |

### 5.4. Filtrowanie i sortowanie

1. **Filtr katalogowy:** `active = true`, `category` (jeśli podano), otwarte w dniu tygodnia `date`:
   - atrakcja **bez żadnych** wierszy w `opening_hours` → traktowana jako otwarta cały dzień (`openingHours = null`),
   - atrakcja z wierszami, ale **bez wiersza** dla dnia tygodnia `date` → zamknięta, odrzucana.
   Dzień tygodnia według ISO-8601: 1 = poniedziałek … 7 = niedziela.
2. **Wstępna selekcja:** spośród pozostałych zostaje `APP_MAX_CANDIDATES` (domyślnie 50, nie mniej niż maks. `limit`) najbliższych startowi w linii prostej (haversine). Wysyłamy maksymalnie jedną parę na lokalizację kandydata. `APP_MAX_CANDIDATES` mieści się w zakresie 50–500. Gdy kandydatów brak, pomijamy wywołanie Transportu.
3. **Wyliczenie** dla tras z `OK` lub przy niedostępności Transportu: `ticket`, `fromStart`, `toStart`, `estimate` (reguły z 3.1).
4. **Filtr `maxCost`** (jeśli podano): odrzucamy, gdy `estimate.totalCost > maxCost`. Dla `estimate.complete = false` porównujemy tylko znane składniki – pozycja nie jest odrzucana z powodu nieznanego kosztu dojazdu.
5. **Sortowanie** (kolejne klucze):
   1. `estimate.complete` – najpierw `true`,
   2. `weatherFit`: `good` → `ok` → `poor` → `null`,
   3. `fromStart.timeMin` rosnąco (`null` na końcu),
   4. `id` rosnąco (deterministyczne rozstrzyganie remisów).
6. **Obcięcie** do `limit`.

---

## 6. Odporność na awarie

| Sytuacja | Zachowanie |
|---|---|
| Warunki dnia nie odpowiadają / błąd | `weather.source = "unavailable"`, `condition = tempC = null`, `weatherFit = null` (drugi klucz sortowania z 5.4 nie różnicuje pozycji) |
| Transport nie odpowiada | `transport.source = "unavailable"`, `fromStart = toStart = null`, `estimate.complete = false` (liczone tylko z biletów i czasu zwiedzania) |
| `NO_CONNECTION` dla konkretnego odcinka | Atrakcja zostaje pominięta, a `transport.source` pozostaje `ok` |
| Timeout zależności | `APP_DISCOVERY_URL` | `http://172.20.147.20:8080` | Opcjonalny adres rejestru Discovery |
| `APP_DISCOVERY_ADVERTISE_IP` | `172.20.147.24` | IP hosta lub punktu wejściowego. Pusta wartość wyłącza rejestrację |
| `APP_DISCOVERY_REGISTRATION_INTERVAL_MS` | `60000` | Odstęp między zakończeniem rejestracji a jej ponowieniem |
| `APP_DEPENDENCY_TIMEOUT_MS` (np. 2000 ms), bez ponowień |
| Seria błędów zależności | Circuit breaker (Resilience4j) per zależność: po przekroczeniu progu błędów wywołania są pomijane na czas `APP_CIRCUIT_OPEN_MS` i od razu stosowana jest degradacja |

Awaria zależności skutkuje odpowiedzią `200` z niepełnymi danymi po poprawnej walidacji i przypisaniu startu. Planer decyduje, czy wykorzystać niepełne propozycje.

### 6.1. Health-check

| Endpoint | Co sprawdza |
|---|---|
| `/actuator/health/liveness` | Tylko stan procesu |
| `/actuator/health/readiness` | Proces + połączenie z bazą |
| `/actuator/health` | Jak readiness; dodatkowo w `details` stan W i T (`reachable: true/false`) |

Stan W i T **nie wpływa** na status serwisu: Atrakcje działają bez nich w trybie zdegradowanym, a uzależnienie od nich powodowałoby wyłączanie lub restartowanie instancji przy awarii innego serwisu.

---

## 7. Model danych

Serwis przechowuje wyłącznie katalog (PostgreSQL). Pogoda i odcinki są pobierane na żądanie i nie są zapisywane w bazie (mogą być krótko buforowane w pamięci, patrz 10).

```mermaid
erDiagram
    CATEGORIES ||--o{ ATTRACTIONS : "ma"
    LOCATIONS  ||--o{ ATTRACTIONS : "znajduje się w"
    ATTRACTIONS ||--o{ OPENING_HOURS : "otwarta w"

    CATEGORIES {
        int id PK
        string code UK
        string name
    }
    LOCATIONS {
        string id PK
        string name
        decimal lat
        decimal lng
        string address
    }
    ATTRACTIONS {
        string id PK
        string name
        int category_id FK
        string location_id FK
        decimal price_per_person
        int visit_duration_min
        bool indoor
        string description
        bool active
    }
    OPENING_HOURS {
        string attraction_id PK,FK
        int weekday PK
        time open_time
        time close_time
    }
```

| Tabela | Rola | Uwagi |
|---|---|---|
| `categories` | Słownik kategorii | Źródło dla `GET /categories` |
| `locations` | Punkty na mapie (atrakcje i punkty startowe) | `id` **zgodne z tabelą odcinków Transportu** |
| `attractions` | Katalog atrakcji | `price_per_person × people` = `ticket.total` |
| `opening_hours` | Godziny otwarcia *(opcjonalna)* | Jeden przedział na dzień; semantyka braku wierszy w 5.4 |

### 7.1. DDL (PostgreSQL, migracja Flyway `V1__init.sql`)

```sql
CREATE TABLE categories (
  id    SERIAL PRIMARY KEY,
  code  VARCHAR(32) UNIQUE NOT NULL,
  name  VARCHAR(64) NOT NULL
);

CREATE TABLE locations (
  id       VARCHAR(32) PRIMARY KEY,
  name     VARCHAR(128) NOT NULL,
  lat      DECIMAL(9,6) NOT NULL CHECK (lat BETWEEN -90 AND 90),
  lng      DECIMAL(9,6) NOT NULL CHECK (lng BETWEEN -180 AND 180),
  address  VARCHAR(255)
);

CREATE TABLE attractions (
  id                  VARCHAR(32) PRIMARY KEY,
  name                VARCHAR(128) NOT NULL,
  category_id         INT NOT NULL REFERENCES categories(id),
  location_id         VARCHAR(32) NOT NULL REFERENCES locations(id),
  price_per_person    DECIMAL(8,2) NOT NULL CHECK (price_per_person >= 0),
  visit_duration_min  INT NOT NULL CHECK (visit_duration_min > 0),
  indoor              BOOLEAN NOT NULL,
  description         TEXT,
  active              BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE opening_hours (
  attraction_id  VARCHAR(32) NOT NULL REFERENCES attractions(id) ON DELETE CASCADE,
  weekday        SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7),  -- ISO-8601: 1 = poniedziałek
  open_time      TIME NOT NULL,
  close_time     TIME NOT NULL,
  PRIMARY KEY (attraction_id, weekday),
  CHECK (close_time > open_time)
);

-- PostgreSQL nie indeksuje kluczy obcych automatycznie
CREATE INDEX idx_attr_category ON attractions(category_id);
CREATE INDEX idx_attr_location ON attractions(location_id);
```

Docelowe dane startowe będą ładowane migracją `V2__seed.sql` i wykorzystywane w testach z PostgreSQL. Demonstracja korzysta z danych katalogu zapisanych w kodzie.

---

## 8. Struktura wewnętrzna serwisu

Architektura warstwowa (kontroler → serwis → repozytorium / klient), pakiet bazowy `pl.psso.attractions`.

```mermaid
flowchart TB
    subgraph Atrakcje
        API[api<br/>ProposalController, AttractionController,<br/>CategoryController, GlobalExceptionHandler]
        SVC[service<br/>ProposalService, AttractionService]
        REPO[repository<br/>AttractionRepository, LocationRepository,<br/>CategoryRepository]
        WC[client<br/>WeatherClient]
        TC[client<br/>TransportClient]
    end
    API --> SVC
    SVC --> REPO
    SVC --> WC
    SVC --> TC
    REPO --> DB[(PostgreSQL)]
    WC --> W[Warunki dnia]
    TC --> T[Transport]
```

| Moduł | Odpowiedzialność |
|---|---|
| `api` | Mapowanie HTTP, walidacja (`@Valid`), mapowanie na DTO (`record`), kody błędów z 3.3 (`@RestControllerAdvice`) |
| `service` | Logika biznesowa z sekcji 5, transakcje (`@Transactional(readOnly = true)` dla zapytań), cache katalogu |
| `repository` | Spring Data JPA – dostęp do katalogu |
| `client` | `RestClient` z timeoutem i circuit breakerem; w profilu `mock` zastępowane implementacjami zwracającymi przykłady z sekcji 4 |
| `config` | Spring Security, klienci HTTP, `@ConfigurationProperties` (prefiks `app.`) |

### 8.1. Konfiguracja (zmienne środowiskowe)

| Zmienna | Przykład | Opis |
|---|---|---|
| `SERVER_PORT` | `8001` | Port serwisu |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/attractions` | Połączenie z bazą |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `attractions` / *(sekret)* | Dane dostępowe do bazy |
| `SPRING_PROFILES_ACTIVE` | `mock` | `mock` – lokalne mocki W i T; `local` – wyłączone uwierzytelnianie |
| `APP_WEATHER_URL` | *(adres dostawcy)* | Adres Warunków dnia. Pusta wartość oznacza brak prognozy |
| `APP_TRANSPORT_URL` | `http://172.20.147.17:8083` | Adres Transportu |
| `APP_DISCOVERY_URL` | `http://172.20.147.20:8080` | Opcjonalny adres rejestru Discovery |
| `APP_DISCOVERY_ADVERTISE_IP` | `172.20.147.24` | IP hosta lub punktu wejściowego. Pusta wartość wyłącza rejestrację |
| `APP_DISCOVERY_REGISTRATION_INTERVAL_MS` | `60000` | Odstęp między zakończeniem rejestracji a jej ponowieniem |
| `APP_DEPENDENCY_TIMEOUT_MS` | `2000` | Timeout wywołań zależności |
| `APP_CIRCUIT_OPEN_MS` | `30000` | Czas otwarcia circuit breakera |
| `APP_START_MAX_DISTANCE_M` | `1000` | Maks. odległość przy przypisaniu startu |
| `APP_MAX_CANDIDATES` | `50` | Maks. liczba kandydatów wysyłanych do Transportu |
| `APP_CATALOG_CACHE_TTL_S` | `300` | Czas życia cache katalogu |
| `APP_WEATHER_CACHE_TTL_S` | `600` | Czas życia cache pogody (per data) |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | `http://auth:9000` | Wystawca tokenów JWT (patrz 9) |

---

## 9. Bezpieczeństwo

- Serwis jest bezstanowy: brak sesji, CSRF wyłączony, brak logowania formularzem i Basic.
- Endpointy `/api/v1/**` wymagają tokenu JWT (Spring Security OAuth2 Resource Server) z uprawnieniem `SCOPE_attractions.read`. Tokeny dla wywołań między serwisami wystawia wspólny serwer autoryzacji (otwarta kwestia 9).
- `/actuator/health/**` jest publiczny; pozostałe endpointy Actuatora nie są wystawiane na zewnątrz.
- Profil `local` (wyłącznie lokalnie i w demonstracji) zezwala na wszystkie żądania bez tokenu.
- Atrakcje wywołują W i T z własnym tokenem (client credentials), jeśli te serwisy go wymagają.
- Odpowiedzi błędów nie zawierają stack trace'ów ani szczegółów wyjątków; sekrety wyłącznie w zmiennych środowiskowych.

---

## 10. Skalowalność i obserwowalność

| Obszar | Rozwiązanie |
|---|---|
| Skalowanie poziome | Serwis bezstanowy – dowolna liczba instancji za load balancerem; stan wyłącznie w PostgreSQL |
| Katalog | Rzadko zmieniany, czytany przy każdym zapytaniu → cache w pamięci instancji (Caffeine, TTL `APP_CATALOG_CACHE_TTL_S`); zmiany katalogu widoczne najpóźniej po TTL |
| Pogoda | Cache per `date` (TTL `APP_WEATHER_CACHE_TTL_S`) – wiele zapytań o ten sam dzień = jedno wywołanie W |
| Transport | Jedno zbiorcze wywołanie, liczba par ograniczona przez `APP_MAX_CANDIDATES` (5.4) |
| Współbieżność | Wątki wirtualne (`spring.threads.virtual.enabled=true`); wywołania W i T równolegle |
| Baza | Pula połączeń HikariCP; brak transakcji w trakcie wywołań zewnętrznych |
| Katalog `GET /attractions` | Paginacja (3.2) |
| Obserwowalność | Nagłówek `X-Correlation-Id` (przyjmowany lub generowany) przekazywany do W i T oraz w logach; metryki Micrometer (czas odpowiedzi, czas i błędy zależności, stan circuit breakera) |

---

## 11. Testy

| Poziom | Narzędzia | Zakres |
|---|---|---|
| Jednostkowe (`*Test`) | JUnit 5, Mockito | `weatherFit`, koszty grupy dla każdego `mode`, `estimate` i `complete`, filtr `maxCost`, kolejność sortowania, godziny otwarcia, haversine i przypisanie startu |
| Integracyjne (`*IT`) | `@SpringBootTest`, Testcontainers (PostgreSQL), WireMock | Endpointy z danymi z `V2__seed.sql` i zaślepionymi W/T; scenariusze degradacji (brak pogody, brak transportu, brak odcinka, timeout); kody błędów z 3.3; zabezpieczenia (401/403) |
| Kontraktowe | WireMock (stuby z przykładów z sekcji 4) | Zgodność z przykładowymi odpowiedziami udostępnionymi przez Warunki dnia i Transport; przykładowa odpowiedź Atrakcji udostępniona Planerowi |

---

## 12. Otwarte kwestie

| # | Kwestia | Z kim |
|---|---|---|
| 1 | Docelowe `locationId` atrakcji i ich odległości od `start` | Transport |
| 5 | Wspólny słownik `condition` | Wszyscy |
| 6 | Czy `opening_hours` wchodzi do zakresu | Zespół |
| 7 | Czy `maxCost` ma obejmować powrót, czy tylko dojazd + bilety | Planer |
| 9 | Wspólny serwer autoryzacji i uprawnienia (`scope`) dla wywołań między serwisami | Wszyscy |
| 10 | Horyzont prognozy pogody – jak daleko w przód może być `date` | Warunki dnia |
