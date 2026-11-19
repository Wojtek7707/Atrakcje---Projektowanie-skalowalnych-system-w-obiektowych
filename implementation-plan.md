# Plan implementacji mikroserwisu Atrakcje

Zakres demonstracyjny obejmuje katalog czterech atrakcji w pamięci, propozycje na podstawie przykładowych danych pogody i transportu, kontrakt PostgreSQL i uruchomienie w Dockerze. Planer wybiera atrakcje i układa plan dnia. Szczegółowe reguły opisuje [architektura](architecture.md).

| Etap | Zakres | Kryterium ukończenia |
|---|---|---|
| 1. Szkielet i Docker | Java 25, Spring Boot 4.1.x, Maven, pakiet `pl.psso.attractions`, obrazy budowania i uruchomienia, profile `local,mock`. | `docker compose up --build` publikuje aplikację na porcie 8000 hosta. Endpoint zdrowia zwraca `200`. |
| 2. Katalog i kontrakt bazy | Model i interfejs `CatalogRepository`, implementacja pamięciowa z czterema atrakcjami, migracja V1, lista, szczegóły i kategorie. | Katalog działa bez połączenia z bazą i obsługuje filtrowanie, paginację oraz błędy. |
| 3. Propozycje z mockami | Przypisanie startu metodą haversine, dostępność w dniu, limit kandydatów, dopasowanie do pogody, koszty, `maxCost` i sortowanie. Klienci mock zwracają przykładową pogodę i odcinki w obu kierunkach. | Planer może wywołać endpoint propozycji, a testy HTTP potwierdzają odpowiedzi i strukturę błędów. |
| 4. Integracja z PostgreSQL i usługami | Po uzgodnieniu kontraktów podłączyć adapter JPA/Flyway, przygotować migrację z danymi początkowymi. Klienci HTTP wywołują zależności równolegle, z timeoutem, circuit breakerem i degradacją odpowiedzi zgodnie z [OpenAPI](contracts/downstream.openapi.yaml). | Testcontainers weryfikuje schemat i odczyt katalogu. WireMock weryfikuje kontrakt, brak pojedynczego odcinka i niedostępność usług. |
| 5. Gotowość do wdrożenia | Zweryfikować JWT i zakres w integracji, uzupełnić health checki bazy i usług, cache, korelację i metryki. Dodać Maven Wrapper do pracy lokalnej. | Przechodzi pełne `verify` na JDK 25 oraz test uruchomienia kontenera z rzeczywistą konfiguracją integracji. |

## Decyzje przed podłączeniem integracji

- Z Transportem uzgodnić docelowe identyfikatory atrakcji i ich odległości lub dzielnice względem punktu `start`. Demonstracja używa tymczasowych `attr-01`–`attr-04` i tabeli z [kontraktu Transportu](contract_transport.md).
- Z Warunkami dnia potwierdzić adres i port. Ustawienia Discovery opisuje [architektura](architecture.md).
- Z Planerem potwierdzić godziny otwarcia, zakres `maxCost` obejmujący powrót oraz strefę `Europe/Warsaw` dla walidacji daty. Demonstracja korzysta z tych założeń.
- Uzgodnić wystawcę JWT i ewentualne poświadczenia między usługami.
