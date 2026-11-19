package pl.psso.attractions.repository;

import static java.time.DayOfWeek.*;
import static pl.psso.attractions.client.TransportClient.START_LOCATION_ID;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import pl.psso.attractions.domain.Catalog;
import pl.psso.attractions.domain.Catalog.Attraction;
import pl.psso.attractions.domain.Catalog.Category;
import pl.psso.attractions.domain.Catalog.Hours;
import pl.psso.attractions.domain.Catalog.Location;

@Repository
public class InMemoryCatalogRepository implements CatalogRepository {
    private final Catalog catalog;

    public InMemoryCatalogRepository() {
        var museum = new Category("museum", "Muzeum");
        var park = new Category("park", "Park");
        var viewpoint = new Category("viewpoint", "Punkt widokowy");
        var science = new Category("science", "Centrum nauki");

        var start = new Location(START_LOCATION_ID, 52.2297, 21.0122);
        var nationalMuseum = new Location("attr-01", 52.2316, 21.0247);
        var royalPark = new Location("attr-02", 52.2150, 21.0347);
        var palace = new Location("attr-03", 52.2318, 21.0060);
        var copernicus = new Location("attr-04", 52.2413, 21.0278);

        catalog = new Catalog(List.of(museum, park, viewpoint, science),
                List.of(start, nationalMuseum, royalPark, palace, copernicus), List.of(
                new Attraction("ATR01", "Muzeum Narodowe", museum, nationalMuseum, true, 90,
                        new BigDecimal("25.00"), "Galerie sztuki i wystawy czasowe.", true,
                        hours(10, 18, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)),
                new Attraction("ATR02", "Łazienki Królewskie", park, royalPark, false, 75,
                        new BigDecimal("0.00"), "Spacer po parku królewskim.", true, Map.of()),
                new Attraction("ATR03", "Taras widokowy PKiN", viewpoint, palace, true, 45,
                        new BigDecimal("28.00"), "Panorama Warszawy z tarasu widokowego.", true,
                        hours(10, 20, DayOfWeek.values())),
                new Attraction("ATR04", "Centrum Nauki Kopernik", science, copernicus, true, 120,
                        new BigDecimal("45.00"), "Interaktywne wystawy naukowe.", true,
                        hours(9, 18, DayOfWeek.values()))));
    }

    private static Map<DayOfWeek, Hours> hours(int open, int close, DayOfWeek... days) {
        var hours = new Hours(LocalTime.of(open, 0), LocalTime.of(close, 0));
        return Arrays.stream(days).collect(Collectors.toUnmodifiableMap(Function.identity(), day -> hours));
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }
}
