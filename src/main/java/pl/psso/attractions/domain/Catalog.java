package pl.psso.attractions.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

public record Catalog(List<Category> categories, List<Location> locations, List<Attraction> attractions) {
    public Catalog {
        categories = List.copyOf(categories);
        locations = List.copyOf(locations);
        attractions = List.copyOf(attractions);
    }

    public record Category(String code, String name) {}

    public record Location(String id, double lat, double lng) {
        public double distanceFrom(double latitude, double longitude) {
            double halfLatitude = Math.toRadians(lat - latitude) / 2;
            double halfLongitude = Math.toRadians(lng - longitude) / 2;
            double value = Math.pow(Math.sin(halfLatitude), 2)
                    + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(lat))
                    * Math.pow(Math.sin(halfLongitude), 2);
            return 2 * 6_371_000 * Math.asin(Math.sqrt(Math.min(1, value)));
        }
    }

    public record Hours(@JsonFormat(pattern = "HH:mm") LocalTime open,
                        @JsonFormat(pattern = "HH:mm") LocalTime close) {}

    public record Attraction(String id, String name, Category category, Location location, boolean indoor,
                             int visitDurationMin, BigDecimal pricePerPerson, String description,
                             boolean active, Map<DayOfWeek, Hours> openingHours) {
        public Attraction {
            openingHours = Map.copyOf(openingHours);
        }
    }
}
