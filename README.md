# Atrakcje – mikroserwis systemu planowania wycieczek

Projekt w ramach kursu *Projektowanie skalowalnych systemów obiektowych* (PSSO).

Serwis udostępnia katalog atrakcji i zwraca Planerowi propozycje na wybrany dzień z godzinami otwarcia, oceną dopasowania do pogody oraz szacunkiem czasu i kosztów. W demonstracji katalog zawiera cztery atrakcje z Warszawy. Pogoda i transport pochodzą z lokalnych mocków, a aplikacja działa bez PostgreSQL.

## Uruchomienie

```sh
docker compose up --build -d
```

Docker buduje projekt na JDK 25, uruchamia testy Maven i udostępnia aplikację na `http://localhost:8000` na wszystkich interfejsach hosta z profilami `local,mock`. Profil `local` pozwala korzystać z API bez tokenu.

```sh
curl -fsS 'http://localhost:8000/actuator/health'
curl -fsS 'http://localhost:8000/api/v1/attractions'
curl -fsS 'http://localhost:8000/api/v1/attractions/ATR01'
curl -fsS 'http://localhost:8000/api/v1/categories'
```

Na Linuksie poniższe polecenie użyje najbliższej przyszłej niedzieli, w którą w katalogu dostępne są wszystkie cztery atrakcje:

```sh
day=$(TZ=Europe/Warsaw date -d 'next Sunday' +%F)
curl -fsS "http://localhost:8000/api/v1/attractions/proposals?date=$day&mode=transit&people=3&startLat=52.2297&startLng=21.0122"
```

Aby włączyć rzeczywiste integracje, uruchom `SPRING_PROFILES_ACTIVE=local docker compose up --build -d`. Transport korzysta domyślnie z `http://172.20.147.17:8083`. Zmienne `APP_WEATHER_URL` i `APP_DISCOVERY_URL` włączają odpowiednio prognozę HTTP i wyszukiwanie adresów w rejestrze. Publikowanie własnego wpisu wymaga dodatkowo `APP_DISCOVERY_ADVERTISE_IP`. Bezpośrednie uruchomienie na JDK 25 i Mavenie używa wewnętrznego portu 8001.

Lokalnie, z JDK 25 i Mavenem:

```sh
mvn verify
mvn spring-boot:run -Dspring-boot.run.profiles=local,mock
```

## Dokumentacja

- [Kontrakt HTTP Atrakcje dla Planera](contracts/attractions-api.md)
- [Plan implementacji](implementation-plan.md)
- [Architektura, API i reguły biznesowe](architecture.md)
- [Kontrakt katalogu PostgreSQL](contracts/database.md)
- [OpenAPI wywołań Pogody, Transportu i Discovery](contracts/downstream.openapi.yaml)
- [Kontrakt Pogody](kontrakt-pogoda.md)
- [Kontrakt Discovery](KONTRAKT_discovery.txt)
- [Kontrakt zespołu Transport](contract_transport.md)
- [Konwencje projektu](AGENTS.md)
