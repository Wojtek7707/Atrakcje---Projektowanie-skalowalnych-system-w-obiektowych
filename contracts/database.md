# Kontrakt katalogu PostgreSQL

[CatalogRepository](../src/main/java/pl/psso/attractions/repository/CatalogRepository.java) udostępnia warstwie serwisowej spójny, niemutowalny odczyt kategorii, lokalizacji i atrakcji z godzinami otwarcia. Odczyt obejmuje również lokalizacje startowe, które mogą istnieć bez przypisanej atrakcji. W wariancie demonstracyjnym repozytorium zwraca katalog z pamięci.

[Migracja V1](../src/main/resources/db/migration/V1__init.sql) określa tabele, klucze, indeksy i ograniczenia według sekcji 7 architektury. Identyfikatory lokalizacji muszą odpowiadać identyfikatorom używanym przez Transport. Punkt startowy ma identyfikator `start`. Brak wszystkich wierszy `opening_hours` oznacza całodobową dostępność. Brak wiersza dla konkretnego dnia przy zdefiniowanych innych dniach oznacza zamknięcie.

## Podłączenie w kolejnym etapie

1. Dodać sterownik PostgreSQL, Flyway i Spring Data JPA, a następnie zaimplementować adapter `CatalogRepository` mapujący tabele na model katalogu.
2. Pobierać spójny katalog w transakcji tylko do odczytu i zwracać w pełni zmaterializowany model przed dowolnym wywołaniem usług zewnętrznych.
3. Stosować migracje Flyway i `spring.jpa.hibernate.ddl-auto=validate`. Parametry połączenia przekazywać przez `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` i `SPRING_DATASOURCE_PASSWORD`.
4. Przetestować migrację i adapter na PostgreSQL w Testcontainers. Przygotować osobną migrację V2 z uzgodnionymi danymi startowymi.

Bieżący obraz uruchamia się bez sterownika, poświadczeń i kontenera PostgreSQL. Plik V1 stanowi przygotowaną migrację do przyszłego adaptera.
