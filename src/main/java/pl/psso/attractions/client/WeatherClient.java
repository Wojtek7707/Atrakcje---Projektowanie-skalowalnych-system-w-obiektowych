package pl.psso.attractions.client;

import java.time.LocalDate;
import java.util.Optional;

public interface WeatherClient {
    enum Condition { sun, cloudy, rain, snow, storm }

    record Forecast(Condition condition, int tempC) {}

    Optional<Forecast> forecast(LocalDate date, double lat, double lon);
}
