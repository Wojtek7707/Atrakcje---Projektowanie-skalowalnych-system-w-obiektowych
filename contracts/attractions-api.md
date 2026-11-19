# Kontrakt HTTP mikroserwisu Atrakcje

W sieci projektu serwis jest dostępny pod adresem `http://172.20.147.24:8000`. Na hoście z Docker Compose można użyć `http://localhost:8000`. Endpointy biznesowe mają prefiks `/api/v1` i zwracają JSON w UTF-8. Kwoty są liczbami w PLN, a czasy zwiedzania i przejazdów podajemy w minutach.

W profilu `local` dostęp jest bez tokenu. W pozostałych profilach wywołania `/api/v1/**` wymagają nagłówka `Authorization: Bearer <JWT>` i zakresu `attractions.read`. Endpointy zdrowia są publiczne.

## Propozycje na dzień

`GET /api/v1/attractions/proposals`

| Parametr | Typ i zakres | Wymagany | Domyślnie |
|---|---|---|---|
| `date` | Data `YYYY-MM-DD`, od dziś w strefie `Europe/Warsaw` | tak | — |
| `mode` | `walk`, `transit` lub `car` | tak | — |
| `startLat` | Szerokość geograficzna od −90 do 90 | tak | — |
| `startLng` | Długość geograficzna od −180 do 180 | tak | — |
| `people` | Liczba osób od 1 do 20 | nie | `1` |
| `category` | Kody kategorii po przecinku, np. `museum,park` | nie | Wszystkie |
| `maxCost` | Maksymalny koszt całej grupy w PLN, ≥ 0 | nie | Bez limitu |
| `limit` | Maksymalna liczba propozycji od 1 do 50 | nie | `10` |

Przykładowe żądanie i odpowiedź `200 OK`

```http
GET /api/v1/attractions/proposals?date=2026-11-22&mode=transit&startLat=52.2297&startLng=21.0122&people=3&category=museum
```

```json
{
  "date": "2026-11-22",
  "start": { "lat": 52.2297, "lng": 21.0122, "resolvedLocationId": "start" },
  "mode": "transit",
  "people": 3,
  "weather": { "condition": "rain", "tempC": 5, "source": "ok" },
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
      "ticket": { "perPerson": 25.00, "total": 75.00 },
      "fromStart": { "timeMin": 9, "costPerPerson": 4.80, "costTotal": 14.40 },
      "toStart": { "timeMin": 9, "costPerPerson": 4.80, "costTotal": 14.40 },
      "estimate": { "totalTimeMin": 108, "totalCost": 103.80, "complete": true }
    }
  ]
}
```

- Punkt startu musi znaleźć się w promieniu skonfigurowanym dla lokalizacji `start`, domyślnie 1000 m od współrzędnych `52.2297, 21.0122`.
- `id` identyfikuje atrakcję w katalogu (`ATR01`–`ATR04`), a `location.id` określa jej lokalizację w Transporcie (`attr-01`–`attr-04`).
- `weather.condition` przyjmuje wartości `sun`, `cloudy`, `rain`, `snow` i `storm`. `weatherFit` przyjmuje `good`, `ok`, `poor` albo `null`. Gdy prognoza jest niedostępna, `weather.source` ma wartość `unavailable`, a `condition`, `tempC` i `weatherFit` przyjmują `null`.
- `openingHours` podaje godziny otwarcia danego dnia w formacie `HH:mm`. Wartość `null` oznacza brak zdefiniowanych godzin i dostępność przez cały dzień.
- `estimate` uwzględnia bilety dla całej grupy, dojazd, zwiedzanie i powrót. Gdy Transport jest niedostępny, `transport.source` ma wartość `unavailable`, `fromStart` i `toStart` przyjmują `null`, a koszt, czas oraz filtr `maxCost` uwzględniają tylko znane składniki. Wtedy `estimate.complete` ma wartość `false`. Atrakcja bez dostępnego połączenia zostaje pominięta, gdy Transport odpowiada.
- Kolejność propozycji określają pełność szacunku, dopasowanie do pogody, czas dojazdu i identyfikator atrakcji. Pusty wynik ma postać `"proposals": []` i status `200 OK`. Planer wybiera propozycje do finalnego planu.

## Katalog

| Endpoint | Parametry | Odpowiedź `200 OK` |
|---|---|---|
| `GET /api/v1/attractions` | Opcjonalne `category` (kody po przecinku), `indoor` (`true` / `false`), `page` (≥ 0, domyślnie `0`), `size` (1–100, domyślnie `20`) | `{ "items": [...], "page": 0, "size": 20, "totalItems": 4, "totalPages": 1 }`. Pozycje są posortowane po `id` i zawierają `id`, `name`, `category`, `location` (`id`, `lat`, `lng`), `indoor`, `visitDurationMin`, `pricePerPerson`. |
| `GET /api/v1/attractions/{id}` | Identyfikator katalogowy, np. `ATR01` | Pola pozycji katalogu oraz `description` i `openingHours`. Godziny mają postać mapy dni tygodnia, np. `{ "SUNDAY": { "open": "10:00", "close": "18:00" } }`. |
| `GET /api/v1/categories` | — | Tablica obiektów `code` i `name`, np. `[{ "code": "museum", "name": "Muzeum" }]`. |

## Stan aplikacji

`GET /actuator/health` zwraca bieżący stan, np. `{"status":"UP","groups":["liveness","readiness"]}`. Dostępne są także `GET /actuator/health/liveness` i `GET /actuator/health/readiness`.

## Błędy API

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid parameter: mode",
    "field": "mode"
  }
}
```

`field` przyjmuje `null`, gdy błąd nie dotyczy pojedynczego parametru. Do obsługi programowej służy `code`.

| HTTP | `code` | Znaczenie |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Brak wymaganego parametru lub nieprawidłowa wartość |
| 401 | `UNAUTHORIZED` | Brak ważnego tokenu w profilu wymagającym uwierzytelniania |
| 403 | `FORBIDDEN` | Token bez wymaganego zakresu |
| 404 | `NOT_FOUND` | Brak atrakcji o podanym identyfikatorze |
| 422 | `START_NOT_RESOLVED` | Punkt startu poza obsługiwanym obszarem |
| 500 | `INTERNAL_ERROR` | Wewnętrzny błąd serwisu |

Reguły biznesowe, konfigurację i kontrakty usług zewnętrznych opisuje [architektura](../architecture.md).
