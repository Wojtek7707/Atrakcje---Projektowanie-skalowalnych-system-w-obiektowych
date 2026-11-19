# Pogoda

> Punkty oznaczone **[JUTRO]** są jeszcze otwarte.

**Dostawca:** zespół Pogoda (weather-service). Dane pobieramy na bieżąco z zewnętrznego API [Open-Meteo](https://open-meteo.com) (prognoza i geokodowanie). Nie przechowujemy ich u siebie.

**Klient:** zespół Atrakcje (attractions-service). Pyta o pogodę dla miejsca i dnia albo zakresu dni wycieczki, żeby zdecydować, które atrakcje są dostępne.

**Przykładowe żądanie (metoda, ścieżka, parametry):**

Jeden dzień:
```
GET /api/weather/day?city=Kraków&date=2026-11-20
GET /api/weather/day?lat=50.0614&lon=19.9366&date=2026-11-20
```

Zakres dni:
```
GET /api/weather/range?city=Kraków&from=2026-11-20&to=2026-11-22
GET /api/weather/range?lat=50.0614&lon=19.9366&from=2026-11-20&to=2026-11-22
```

| parametr | wymagany | format | opis |
|---|---|---|---|
| `city` | `city` albo `lat`+`lon` (nie oba) | tekst, w URL zakodowany (`Krak%C3%B3w`) | nazwa miasta |
| `lat`, `lon` | `city` albo `lat`+`lon` (nie oba) | liczba dziesiętna, `lat` od -90 do 90, `lon` od -180 do 180 | współrzędne |
| `date` | tak (`/day`) | `YYYY-MM-DD` | dzień |
| `from`, `to` | tak (`/range`) | `YYYY-MM-DD` | pierwszy i ostatni dzień (włącznie) |

Zakres dat:
- każda data musi mieścić się w przedziale **od dziś do dziś + 15 dni** (tyle obejmuje prognoza),
- daty z przeszłości dają `400`,
- zakres `/range` ma więc najwyżej 16 dni.

Nie ustaliliśmy jeszcze:
- **[JUTRO]** port i adres bazowy (do uzgodnienia z pozostałymi zespołami).

**Wzór odpowiedzi:**

`200 OK`, JSON. `/day` i `/range` mają ten sam kształt. `/day` zwraca listę `days` z jednym elementem.

```json
{
  "location": { "name": "Kraków", "lat": 50.0614, "lon": 19.9366 },
  "days": [
    {
      "date": "2026-11-20",
      "tempMin": 2.1,
      "tempMax": 7.4,
      "precipitationMm": 3.2,
      "windKph": 18.0,
      "category": "RAIN"
    }
  ],
  "stale": false
}
```

| pole | typ | opis |
|---|---|---|
| `location.name` | tekst albo `null` | nazwa miasta; `null`, gdy pytano po `lat`/`lon` |
| `location.lat`, `location.lon` | liczba | współrzędne, dla których liczona jest prognoza |
| `days[].date` | `YYYY-MM-DD` | dzień, dni posortowane rosnąco |
| `days[].tempMin`, `days[].tempMax` | liczba, °C | minimalna i maksymalna temperatura dnia |
| `days[].precipitationMm` | liczba, mm | suma opadów w ciągu dnia |
| `days[].windKph` | liczba, km/h | maksymalna prędkość wiatru |
| `days[].category` | `SUNNY` \| `CLOUDY` \| `RAIN` \| `SNOW` \| `STORM` | ogólna kategoria pogody dnia |
| `stale` | bool | obecnie zawsze `false`. Pole zostaje, żeby można było dołożyć cache albo bazę bez zmiany kontraktu |

Błędy mają postać `{ "error": "<opis>" }`:

| kod | kiedy |
|---|---|
| `400 Bad Request` | brak `city` i `lat`/`lon`, podane jednocześnie `city` i `lat`/`lon`, `lat`/`lon` poza zakresem, zły format daty, data poza przedziałem dziś…dziś+15, `from > to` |
| `404 Not Found` | nie znaleziono miasta |
| `503 Service Unavailable` | zewnętrzne API nie odpowiada albo zwraca błąd. Dochodzi nagłówek `Retry-After: 60` |

**Limit oczekiwania na odpowiedź:** **[JUTRO]**

**Zachowanie przy błędzie i niedostępności usługi:**

Po naszej stronie (dostawca):
1. Klient wysłał błędne parametry: od razu `400`, bez wywoływania zewnętrznego API.
2. Zewnętrzne API nie odpowiada: `503` z nagłówkiem `Retry-After: 60` i treścią `{"error": "..."}`. Nie mamy danych zapasowych.

Po stronie klienta (Atrakcje), gdy nasz serwis nie odpowie: **[JUTRO]**
