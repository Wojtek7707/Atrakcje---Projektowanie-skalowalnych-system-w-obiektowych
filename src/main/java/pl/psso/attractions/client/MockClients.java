package pl.psso.attractions.client;

import static pl.psso.attractions.client.TransportClient.START_LOCATION_ID;

import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import pl.psso.attractions.client.TransportClient.Pair;
import pl.psso.attractions.client.TransportClient.Mode;
import pl.psso.attractions.client.TransportClient.Response;
import pl.psso.attractions.client.TransportClient.Route;
import pl.psso.attractions.client.TransportClient.Status;

@Configuration(proxyBeanMethods = false)
@Profile("mock")
class MockClients {
    private static final int PUBLIC_TRANSPORT_FARE_GROSZE = 480;
    private static final Map<String, Times> ROUTES = Map.of(
            "attr-01", new Times(15, 9, 6, 1320),
            "attr-02", new Times(21, 11, 7, 1495),
            "attr-03", new Times(28, 12, 8, 1705),
            "attr-04", new Times(29, 13, 8, 1740),
            "attr-05", new Times(null, 17, 11, 2230),
            "attr-06", new Times(null, 28, 19, 3560),
            "rest-01", new Times(9, 8, 5, 1145),
            "rest-02", new Times(22, 11, 7, 1530),
            "rest-03", new Times(34, 14, 9, 1880),
            "rest-04", new Times(null, 26, 17, 3245));

    private record Times(Integer walk, int publicTransport, int car, int carCostGrosze) {}

    @Bean
    WeatherClient weatherClient() {
        return (date, lat, lon) -> Optional.of(new WeatherClient.Forecast(WeatherClient.Condition.rain, 5));
    }

    @Bean
    TransportClient transportClient() {
        return request -> Optional.of(new Response(request.mode(),
                request.pairs().stream().map(pair -> route(pair, request.mode())).toList()));
    }

    private static Route route(Pair pair, Mode mode) {
        var from = pair.fromLocationId();
        var to = pair.toLocationId();
        if (from.equals(to)) {
            return new Route(from, to, Status.OK, 0, 0);
        }
        var times = START_LOCATION_ID.equals(from) ? ROUTES.get(to)
                : START_LOCATION_ID.equals(to) ? ROUTES.get(from) : null;
        if (times == null || (mode == Mode.WALK && times.walk() == null)) {
            return new Route(from, to, Status.NO_CONNECTION, null, null);
        }

        return switch (mode) {
            case WALK -> new Route(from, to, Status.OK, times.walk(), 0);
            case PUBLIC_TRANSPORT -> new Route(from, to, Status.OK, times.publicTransport(), PUBLIC_TRANSPORT_FARE_GROSZE);
            case CAR -> new Route(from, to, Status.OK, times.car(), times.carCostGrosze());
        };
    }
}
