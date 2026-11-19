package pl.psso.attractions.client;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

public interface TransportClient {
    String START_LOCATION_ID = "start";
    int MAX_PAIRS = 500;

    enum Mode { WALK, PUBLIC_TRANSPORT, CAR }
    enum Status { OK, NO_CONNECTION }

    record Pair(String fromLocationId, String toLocationId) {}
    record Request(Mode mode, List<Pair> pairs) {}
    record Response(Mode mode, List<Route> results) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Route(String fromLocationId, String toLocationId, Status status, Integer durationMin, Integer costGrosze) {}

    Optional<Response> routes(Request request);
}
