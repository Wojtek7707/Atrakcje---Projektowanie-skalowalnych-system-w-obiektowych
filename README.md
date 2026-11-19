# Atrakcje – mikroserwis systemu planowania wycieczek

Projekt w ramach kursu *Projektowanie skalowalnych systemów obiektowych* (PSSO).

Serwis **Atrakcje** udostępnia katalog atrakcji turystycznych i na żądanie serwisu **Planer i integracja** zwraca propozycje atrakcji na wybrany dzień. Każda propozycja zawiera:

- dopasowanie do pogody (serwis **Warunki dnia**),
- czas i koszt dojazdu oraz powrotu (serwis **Transport**),
- godziny otwarcia,
- szacunek łącznego czasu i kosztu dla grupy.

Wybór atrakcji do planu należy do Planera.

```
Planer ──► Atrakcje ──► Warunki dnia
                   └──► Transport
```

## Dokumentacja

- [architecture.md](architecture.md) – API, kontrakty z zależnościami, logika, model danych, konfiguracja (źródło prawdy)
- [AGENTS.md](AGENTS.md) – konwencje kodu i polecenia dla agentów AI oraz programistów

## Stos technologiczny

Java 25 · Spring Boot 4.1 · JPA (Hibernate) · Spring Security · PostgreSQL · Flyway · Maven · JUnit 5 / Mockito / Testcontainers / WireMock

## Uruchomienie lokalne

> Kod serwisu jest w przygotowaniu – poniższe polecenia opisują docelowy sposób pracy.

Wymagania: JDK 25, Docker.

Uruchomienie z mockami Warunków dnia i Transportu oraz bez uwierzytelniania (wymaga bazy PostgreSQL, np. z `docker compose up db`):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,mock
```

Przykładowe zapytanie:

```bash
curl "http://localhost:8001/api/v1/attractions/proposals?date=2026-06-14&mode=transit&people=3&startLat=52.2297&startLng=21.0122"
```

Konfiguracja przez zmienne środowiskowe – pełna lista w [architecture.md §8.1](architecture.md#81-konfiguracja-zmienne-środowiskowe).

## Testy

```bash
./mvnw test     # testy jednostkowe
./mvnw verify   # testy jednostkowe + integracyjne (wymaga Dockera)
```
