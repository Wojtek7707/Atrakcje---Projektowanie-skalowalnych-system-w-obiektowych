package pl.psso.attractions.service;

import static java.math.RoundingMode.HALF_UP;
import static pl.psso.attractions.client.TransportClient.START_LOCATION_ID;
import static pl.psso.attractions.service.ApiException.Code.START_NOT_RESOLVED;
import static pl.psso.attractions.service.ApiException.Code.VALIDATION_ERROR;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import pl.psso.attractions.client.TransportClient;
import pl.psso.attractions.client.TransportClient.Pair;
import pl.psso.attractions.client.TransportClient.Request;
import pl.psso.attractions.client.TransportClient.Response;
import pl.psso.attractions.client.TransportClient.Route;
import pl.psso.attractions.client.TransportClient.Status;
import pl.psso.attractions.client.WeatherClient;
import pl.psso.attractions.client.WeatherClient.Condition;
import pl.psso.attractions.config.ProposalProperties;
import pl.psso.attractions.domain.Catalog.Attraction;
import pl.psso.attractions.domain.Catalog.Hours;
import pl.psso.attractions.domain.Catalog.Location;
import pl.psso.attractions.repository.CatalogRepository;

@Service
public class ProposalService {
    public enum Mode {
        walk(TransportClient.Mode.WALK),
        transit(TransportClient.Mode.PUBLIC_TRANSPORT),
        car(TransportClient.Mode.CAR);

        private final TransportClient.Mode transportMode;

        Mode(TransportClient.Mode transportMode) {
            this.transportMode = transportMode;
        }
    }

    public enum Source { ok, unavailable }
    public enum WeatherFit { good, ok, poor }

    public record Query(LocalDate date, BigDecimal maxCost, Mode mode, int people, String category,
                        double startLat, double startLng, int limit) {}

    public record Start(double lat, double lng, String resolvedLocationId) {}
    public record Weather(Condition condition, Integer tempC, Source source) {}
    public record Transport(Source source) {}
    public record Ticket(BigDecimal perPerson, BigDecimal total) {}
    public record Leg(int timeMin, BigDecimal costPerPerson, BigDecimal costTotal) {}
    public record Estimate(int totalTimeMin, BigDecimal totalCost, boolean complete) {}
    public record Proposal(String id, String name, String category, Location location, boolean indoor,
                           WeatherFit weatherFit, int visitDurationMin, Hours openingHours, Ticket ticket,
                           Leg fromStart, Leg toStart, Estimate estimate) {}
    public record Result(LocalDate date, Start start, Mode mode, int people, Weather weather,
                         Transport transport, List<Proposal> proposals) {}

    private static final Comparator<Proposal> PROPOSAL_ORDER = Comparator
            .comparing((Proposal proposal) -> proposal.estimate().complete()).reversed()
            .thenComparing(Proposal::weatherFit, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(proposal -> proposal.fromStart() == null ? null : proposal.fromStart().timeMin(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Proposal::id);

    private final CatalogRepository repository;
    private final WeatherClient weatherClient;
    private final TransportClient transportClient;
    private final ProposalProperties properties;
    private final Clock clock;
    private final ExecutorService proposalExecutor;

    public ProposalService(CatalogRepository repository, WeatherClient weatherClient, TransportClient transportClient,
                           ProposalProperties properties, Clock clock, ExecutorService proposalExecutor) {
        this.repository = repository;
        this.weatherClient = weatherClient;
        this.transportClient = transportClient;
        this.properties = properties;
        this.clock = clock;
        this.proposalExecutor = proposalExecutor;
    }

    public Result propose(Query query) {
        if (query.date().isBefore(LocalDate.now(clock))) {
            throw new ApiException(VALIDATION_ERROR, "Date must be today or later", "date");
        }

        var catalog = repository.getCatalog();
        var categories = AttractionService.categoryFilter(query.category(), catalog.categories());
        var byDistance = Comparator.comparingDouble(
                (Location location) -> location.distanceFrom(query.startLat(), query.startLng()));
        var start = catalog.locations().stream()
                .filter(location -> START_LOCATION_ID.equals(location.id()))
                .findFirst()
                .filter(location -> location.distanceFrom(query.startLat(), query.startLng())
                        <= properties.startMaxDistanceM())
                .orElseThrow(() -> new ApiException(START_NOT_RESOLVED, "Start location could not be resolved", null));

        var weekday = query.date().getDayOfWeek();
        var candidates = catalog.attractions().stream()
                .filter(Attraction::active)
                .filter(item -> categories.isEmpty() || categories.contains(item.category().code()))
                .filter(item -> item.openingHours().isEmpty() || item.openingHours().containsKey(weekday))
                .sorted(Comparator.comparing(Attraction::location, byDistance).thenComparing(Attraction::id))
                .limit(properties.maxCandidates())
                .toList();

        var pairs = candidates.stream()
                .map(item -> new Pair(start.id(), item.location().id()))
                .distinct()
                .toList();
        var mode = query.mode().transportMode;
        var weatherRequest = CompletableFuture.supplyAsync(
                () -> weatherClient.forecast(query.date(), query.startLat(), query.startLng()), proposalExecutor);
        var transportRequest = pairs.isEmpty()
                ? CompletableFuture.completedFuture(Optional.of(new Response(mode, List.of())))
                : CompletableFuture.supplyAsync(() -> transportClient.routes(new Request(mode, pairs)), proposalExecutor);
        var weather = weatherRequest.join()
                .map(forecast -> new Weather(forecast.condition(), forecast.tempC(), Source.ok))
                .orElse(new Weather(null, null, Source.unavailable));
        var transport = transportRequest.join();
        var routes = transport.map(Response::results).orElseGet(List::of).stream()
                .filter(route -> route.status() == Status.OK)
                .collect(Collectors.toMap(Route::toLocationId, Function.identity()));

        var proposals = candidates.stream()
                .filter(item -> transport.isEmpty() || routes.containsKey(item.location().id()))
                .map(item -> proposal(item, query, weather.condition(), routes.get(item.location().id())))
                .filter(item -> query.maxCost() == null || item.estimate().totalCost().compareTo(query.maxCost()) <= 0)
                .sorted(PROPOSAL_ORDER)
                .limit(query.limit())
                .toList();

        return new Result(query.date(), new Start(query.startLat(), query.startLng(), start.id()), query.mode(),
                query.people(), weather, new Transport(transport.isPresent() ? Source.ok : Source.unavailable), proposals);
    }

    private static Proposal proposal(Attraction item, Query query, Condition condition, Route route) {
        var people = BigDecimal.valueOf(query.people());
        var ticket = new Ticket(money(item.pricePerPerson()), money(item.pricePerPerson().multiply(people)));
        var leg = leg(route, people);
        var estimate = leg == null
                ? new Estimate(item.visitDurationMin(), ticket.total(), false)
                : new Estimate(item.visitDurationMin() + 2 * leg.timeMin(),
                        ticket.total().add(leg.costTotal().multiply(BigDecimal.valueOf(2))), true);
        return new Proposal(item.id(), item.name(), item.category().code(), item.location(), item.indoor(),
                fit(condition, item.indoor()), item.visitDurationMin(),
                item.openingHours().get(query.date().getDayOfWeek()), ticket, leg, leg, estimate);
    }

    private static WeatherFit fit(Condition condition, boolean indoor) {
        if (condition == null) {
            return null;
        }
        return switch (condition) {
            case sun -> indoor ? WeatherFit.ok : WeatherFit.good;
            case cloudy -> WeatherFit.good;
            case rain, snow, storm -> indoor ? WeatherFit.good : WeatherFit.poor;
        };
    }

    private static Leg leg(Route route, BigDecimal people) {
        if (route == null) {
            return null;
        }
        var costPerPerson = BigDecimal.valueOf(route.costGrosze(), 2);
        return new Leg(route.durationMin(), costPerPerson, costPerPerson.multiply(people));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, HALF_UP);
    }
}
