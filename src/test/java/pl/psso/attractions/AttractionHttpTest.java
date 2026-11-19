package pl.psso.attractions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local", "mock"})
class AttractionHttpTest {
    @Value("${local.server.port}")
    private int port;

    @Test
    void servesFourAttractionsAndSupportsCatalogFilters() throws Exception {
        var catalog = get("/api/v1/attractions");
        assertThat(catalog.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(catalog.body(), "$.totalItems")).isEqualTo(4);
        assertThat(JsonPath.<List<String>>read(catalog.body(), "$.items[*].id"))
                .containsExactly("ATR01", "ATR02", "ATR03", "ATR04");

        var filtered = get("/api/v1/attractions?category=park,museum&indoor=false&size=1");
        assertThat(filtered.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(filtered.body(), "$.items[0].id")).isEqualTo("ATR02");
        assertThat(JsonPath.<Integer>read(filtered.body(), "$.totalPages")).isEqualTo(1);

        var detail = get("/api/v1/attractions/ATR01");
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(detail.body(), "$.openingHours.SUNDAY.open")).isEqualTo("10:00");

        var categories = get("/api/v1/categories");
        assertThat(categories.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(categories.body(), "$[*].code"))
                .containsExactlyInAnyOrder("museum", "park", "science", "viewpoint");
    }

    @Test
    void returnsProposalsWithMockWeatherAndRoundTripCosts() throws Exception {
        var response = get(proposalPath() + "&startLat=52.2297&startLng=21.0122&people=3");
        assertThat(response.statusCode()).isEqualTo(200);
        var json = JsonPath.parse(response.body());
        assertThat(json.<String>read("$.weather.condition")).isEqualTo("rain");
        assertThat(json.<String>read("$.transport.source")).isEqualTo("ok");
        assertThat(json.<String>read("$.start.resolvedLocationId")).isEqualTo("start");
        assertThat(json.<List<String>>read("$.proposals[*].id"))
                .containsExactly("ATR01", "ATR03", "ATR04", "ATR02");
        assertThat(json.<Number>read("$.proposals[0].estimate.totalCost").doubleValue()).isEqualTo(103.80);
        assertThat(json.<Integer>read("$.proposals[0].estimate.totalTimeMin")).isEqualTo(108);
        assertThat(json.<Boolean>read("$.proposals[0].estimate.complete")).isTrue();
    }

    @Test
    void multipliesTaxiCostPerPersonByGroupSize() throws Exception {
        var response = get(proposalPath("car") + "&startLat=52.2297&startLng=21.0122&people=3&category=museum");
        assertThat(response.statusCode()).isEqualTo(200);
        var json = JsonPath.parse(response.body());
        assertThat(json.<Number>read("$.proposals[0].fromStart.costPerPerson").doubleValue()).isEqualTo(13.20);
        assertThat(json.<Number>read("$.proposals[0].toStart.costTotal").doubleValue()).isEqualTo(39.60);
        assertThat(json.<Number>read("$.proposals[0].estimate.totalCost").doubleValue()).isEqualTo(154.20);
    }

    @Test
    void returnsContractErrors() throws Exception {
        var missing = get("/api/v1/attractions/ATR99");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(JsonPath.<String>read(missing.body(), "$.error.code")).isEqualTo("NOT_FOUND");

        var invalid = get(proposalPath() + "&startLat=91&startLng=21.0122");
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(invalid.body(), "$.error.field")).isEqualTo("startLat");

        var unresolved = get(proposalPath() + "&startLat=0&startLng=0");
        assertThat(unresolved.statusCode()).isEqualTo(422);
        assertThat(JsonPath.<String>read(unresolved.body(), "$.error.code")).isEqualTo("START_NOT_RESOLVED");
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        var uri = URI.create("http://localhost:" + port + path);
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static String proposalPath() {
        return proposalPath("transit");
    }

    private static String proposalPath(String mode) {
        var sunday = LocalDate.now(ZoneId.of("Europe/Warsaw")).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return "/api/v1/attractions/proposals?date=" + sunday + "&mode=" + mode;
    }
}
