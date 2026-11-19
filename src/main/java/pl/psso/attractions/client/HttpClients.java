package pl.psso.attractions.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import pl.psso.attractions.client.TransportClient.Request;
import pl.psso.attractions.client.TransportClient.Response;
import pl.psso.attractions.client.TransportClient.Status;
import pl.psso.attractions.client.WeatherClient.Condition;
import pl.psso.attractions.client.WeatherClient.Forecast;
import pl.psso.attractions.config.DownstreamProperties;

@Configuration(proxyBeanMethods = false)
@Profile("!mock")
@EnableScheduling
class HttpClients {
    private static final Logger log = LoggerFactory.getLogger(HttpClients.class);
    private static final int WEATHER_FORECAST_DAYS = 15;

    @Bean
    DiscoveryClient discoveryClient(RestClient.Builder builder, DownstreamProperties properties) {
        return new DiscoveryClient(client(builder, Duration.ofSeconds(2)), properties,
                circuitBreaker("discovery", properties));
    }

    @Bean
    WeatherClient weatherClient(RestClient.Builder builder, DownstreamProperties properties,
                                DiscoveryClient discovery, Clock clock) {
        var client = client(builder, Duration.ofMillis(properties.dependencyTimeoutMs()));
        var breaker = circuitBreaker("weather", properties);
        return (date, lat, lon) -> {
            if (properties.weatherUrl() == null || date.isAfter(LocalDate.now(clock).plusDays(WEATHER_FORECAST_DAYS))) {
                return Optional.empty();
            }
            return guarded(breaker, () -> {
                var uri = UriComponentsBuilder.fromUri(discovery.resolve("weather", properties.weatherUrl()))
                        .pathSegment("api", "weather", "day")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("date", date)
                        .build(true).toUri();
                return forecast(client.get().uri(uri).retrieve().body(WeatherResponse.class), date);
            });
        };
    }

    @Bean
    TransportClient transportClient(RestClient.Builder builder, DownstreamProperties properties,
                                    DiscoveryClient discovery) {
        var client = client(builder, Duration.ofMillis(properties.dependencyTimeoutMs()));
        var breaker = circuitBreaker("transport", properties);
        return request -> guarded(breaker, () -> {
            var uri = UriComponentsBuilder.fromUri(discovery.resolve("transport", properties.transportUrl()))
                    .pathSegment("api", "v1", "routes").build(true).toUri();
            var response = client.post().uri(uri).body(request).retrieve().body(Response.class);
            validateRoutes(request, response);
            return response;
        });
    }

    private static RestClient client(RestClient.Builder builder, Duration timeout) {
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return builder.requestFactory(factory).build();
    }

    private static CircuitBreaker circuitBreaker(String name, DownstreamProperties properties) {
        return CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofMillis(properties.circuitOpenMs()))
                .build());
    }

    private static <T> Optional<T> guarded(CircuitBreaker breaker, Supplier<T> call) {
        try {
            return Optional.of(breaker.executeSupplier(call));
        } catch (CallNotPermittedException e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("{} request failed: {}", breaker.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    private static Forecast forecast(WeatherResponse response, LocalDate date) {
        if (response == null || response.days() == null || response.days().size() != 1) {
            throw new RestClientException("Expected one forecast day");
        }
        var day = response.days().getFirst();
        if (day == null || !date.equals(day.date()) || day.category() == null
                || day.tempMin() == null || day.tempMax() == null) {
            throw new RestClientException("Incomplete forecast for the requested day");
        }
        var condition = switch (day.category()) {
            case "SUNNY" -> Condition.sun;
            case "CLOUDY" -> Condition.cloudy;
            case "RAIN" -> Condition.rain;
            case "SNOW" -> Condition.snow;
            case "STORM" -> Condition.storm;
            default -> throw new RestClientException("Unknown weather category: " + day.category());
        };
        var tempC = day.tempMin().add(day.tempMax()).divide(BigDecimal.TWO, 0, RoundingMode.HALF_UP);
        return new Forecast(condition, tempC.intValue());
    }

    private static void validateRoutes(Request request, Response response) {
        if (response == null || response.mode() != request.mode() || response.results() == null
                || response.results().size() != request.pairs().size()) {
            throw new RestClientException("Transport response does not match the request");
        }
        for (int i = 0; i < request.pairs().size(); i++) {
            var pair = request.pairs().get(i);
            var route = response.results().get(i);
            if (route == null || !pair.fromLocationId().equals(route.fromLocationId())
                    || !pair.toLocationId().equals(route.toLocationId()) || route.status() == null
                    || (route.status() == Status.OK && (route.durationMin() == null || route.durationMin() < 0
                            || route.costGrosze() == null || route.costGrosze() < 0))) {
                throw new RestClientException("Invalid result for transport pair " + pair);
            }
        }
    }

    private record WeatherResponse(List<Day> days) {}
    private record Day(LocalDate date, BigDecimal tempMin, BigDecimal tempMax, String category) {}
}
