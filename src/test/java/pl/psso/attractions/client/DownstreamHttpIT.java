package pl.psso.attractions.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class DownstreamHttpIT {
    private static final WireMockServer STUBS = new WireMockServer(wireMockConfig().dynamicPort());
    private static final LocalDate DATE = LocalDate.now(ZoneId.of("Europe/Warsaw"))
            .with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
    private static final String ROUTES_PATH = "/api/v1/routes";
    private static final String WEATHER_PATH = "/api/weather/day";

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        STUBS.start();
        registry.add("app.downstream.discovery-url", STUBS::baseUrl);
        registry.add("app.downstream.transport-url", STUBS::baseUrl);
        registry.add("app.downstream.weather-url", () -> "http://192.0.2.1:" + STUBS.port());
    }

    @BeforeEach
    void prepareDiscovery() {
        STUBS.resetAll();
        STUBS.stubFor(get(urlEqualTo("/services/weather")).willReturn(okJson("""
                {"name":"weather","ip":"127.0.0.1","registeredAt":"2026-11-19T10:30:00Z"}
                """)));
        STUBS.stubFor(get(urlEqualTo("/services/transport")).willReturn(notFound()));
    }

    @AfterAll
    static void stopStubs() {
        STUBS.stop();
    }

    @Test
    void usesDiscoveredWeatherHostAndFallsBackToConfiguredTransportHost() throws Exception {
        STUBS.stubFor(get(urlPathEqualTo(WEATHER_PATH))
                .withQueryParam("date", equalTo(DATE.toString()))
                .withQueryParam("lat", equalTo("52.2297"))
                .withQueryParam("lon", equalTo("21.0122"))
                .willReturn(okJson("""
                        {"location":{"name":null,"lat":52.2297,"lon":21.0122},"days":[
                          {"date":"%s","tempMin":2.1,"tempMax":7.4,"precipitationMm":3.2,
                           "windKph":18.0,"category":"STORM"}],"stale":false}
                        """.formatted(DATE))));
        STUBS.stubFor(post(urlEqualTo(ROUTES_PATH)).withRequestBody(equalToJson("""
                {"mode":"WALK","pairs":[{"fromLocationId":"start","toLocationId":"attr-01"}]}
                """)).willReturn(okJson("""
                {"mode":"WALK","results":[{"fromLocationId":"start","toLocationId":"attr-01",
                 "status":"OK","durationMin":15,"costGrosze":0}]}
                """)));

        var response = proposals();
        assertThat(response.<String>read("$.weather.condition")).isEqualTo("storm");
        assertThat(response.<Integer>read("$.weather.tempC")).isEqualTo(5);
        assertThat(response.<String>read("$.proposals[0].id")).isEqualTo("ATR01");
        assertThat(response.<String>read("$.proposals[0].weatherFit")).isEqualTo("good");
        assertThat(response.<Integer>read("$.proposals[0].fromStart.timeMin")).isEqualTo(15);
    }

    @Test
    void returnsPartialProposalsWithoutRetryWhenDependenciesFail() throws Exception {
        STUBS.stubFor(get(urlPathEqualTo(WEATHER_PATH)).willReturn(serviceUnavailable()
                .withHeader("Retry-After", "60")));
        STUBS.stubFor(post(urlEqualTo(ROUTES_PATH)).willReturn(serverError()));

        var response = proposals();
        assertThat(response.<String>read("$.weather.source")).isEqualTo("unavailable");
        assertThat(response.<String>read("$.transport.source")).isEqualTo("unavailable");
        assertThat(response.<Boolean>read("$.proposals[0].estimate.complete")).isFalse();
        STUBS.verify(1, getRequestedFor(urlPathEqualTo(WEATHER_PATH)));
        STUBS.verify(1, postRequestedFor(urlEqualTo(ROUTES_PATH)));
    }

    private DocumentContext proposals() throws Exception {
        var uri = URI.create("http://localhost:" + port + "/api/v1/attractions/proposals?date=" + DATE
                + "&mode=walk&category=museum&startLat=52.2297&startLng=21.0122");
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return JsonPath.parse(response.body());
        }
    }
}
