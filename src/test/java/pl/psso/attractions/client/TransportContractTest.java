package pl.psso.attractions.client;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.psso.attractions.client.TransportClient.Mode.WALK;
import static pl.psso.attractions.client.TransportClient.Status.NO_CONNECTION;
import static pl.psso.attractions.client.TransportClient.Status.OK;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import pl.psso.attractions.client.TransportClient.Pair;
import pl.psso.attractions.client.TransportClient.Request;
import pl.psso.attractions.client.TransportClient.Response;
import pl.psso.attractions.client.TransportClient.Route;
import pl.psso.attractions.config.ProposalProperties;
import pl.psso.attractions.repository.InMemoryCatalogRepository;
import pl.psso.attractions.service.ProposalService;
import pl.psso.attractions.service.ProposalService.Query;

class TransportContractTest {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void mockReturnsOrderedResultsForSymmetricAndUnsupportedPairs() {
        var pairs = List.of(new Pair("start", "attr-01"), new Pair("attr-01", "start"),
                new Pair("start", "attr-06"), new Pair("attr-01", "rest-01"), new Pair("start", "start"));

        var response = new MockClients().transportClient().routes(new Request(WALK, pairs)).orElseThrow();

        assertThat(response.mode()).isEqualTo(WALK);
        assertThat(response.results()).containsExactly(
                new Route("start", "attr-01", OK, 15, 0),
                new Route("attr-01", "start", OK, 15, 0),
                new Route("start", "attr-06", NO_CONNECTION, null, null),
                new Route("attr-01", "rest-01", NO_CONNECTION, null, null),
                new Route("start", "start", OK, 0, 0));
    }

    @Test
    void noConnectionExcludesCandidateWhileUnavailableTransportKeepsIt() {
        var query = new Query(LocalDate.of(2027, 6, 13), null, ProposalService.Mode.walk,
                1, "museum", 52.2297, 21.0122, 10);

        TransportClient noConnection = request -> {
            assertThat(request).isEqualTo(new Request(WALK, List.of(new Pair("start", "attr-01"))));
            return Optional.of(new Response(WALK,
                    List.of(new Route("start", "attr-01", NO_CONNECTION, null, null))));
        };

        var excluded = service(noConnection).propose(query);
        assertThat(excluded.transport().source()).isEqualTo(ProposalService.Source.ok);
        assertThat(excluded.proposals()).isEmpty();

        var degraded = service(request -> Optional.empty()).propose(query);
        assertThat(degraded.transport().source()).isEqualTo(ProposalService.Source.unavailable);
        assertThat(degraded.proposals()).singleElement().satisfies(proposal -> {
            assertThat(proposal.id()).isEqualTo("ATR01");
            assertThat(proposal.estimate().complete()).isFalse();
            assertThat(proposal.fromStart()).isNull();
            assertThat(proposal.toStart()).isNull();
        });
    }

    @Test
    void skipsTransportRequestWhenCatalogFiltersExcludeAllCandidates() {
        var query = new Query(LocalDate.of(2027, 6, 14), null, ProposalService.Mode.walk,
                1, "museum", 52.2297, 21.0122, 10);
        var result = service(request -> {
            throw new AssertionError("Transport must not receive an empty batch");
        }).propose(query);
        assertThat(result.proposals()).isEmpty();
        assertThat(result.transport().source()).isEqualTo(ProposalService.Source.ok);
    }

    private ProposalService service(TransportClient transportClient) {
        var clock = Clock.fixed(Instant.parse("2027-06-10T12:00:00Z"), ZoneOffset.UTC);
        return new ProposalService(new InMemoryCatalogRepository(), new MockClients().weatherClient(),
                transportClient, new ProposalProperties(1000, 50), clock, executor);
    }
}
