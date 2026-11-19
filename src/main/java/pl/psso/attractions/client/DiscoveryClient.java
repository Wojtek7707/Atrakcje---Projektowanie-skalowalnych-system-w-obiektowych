package pl.psso.attractions.client;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import pl.psso.attractions.config.DownstreamProperties;

class DiscoveryClient {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryClient.class);

    private final RestClient client;
    private final DownstreamProperties properties;
    private final CircuitBreaker circuitBreaker;

    DiscoveryClient(RestClient client, DownstreamProperties properties, CircuitBreaker circuitBreaker) {
        this.client = client;
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
    }

    URI resolve(String service, URI fallback) {
        if (properties.discoveryUrl() == null) {
            return fallback;
        }
        try {
            var ip = circuitBreaker.executeSupplier(() -> lookup(service));
            return ip == null ? fallback : UriComponentsBuilder.fromUri(fallback)
                    .host(ip.contains(":") ? "[" + ip + "]" : ip).build(true).toUri();
        } catch (RestClientException | IllegalArgumentException | CallNotPermittedException e) {
            log.debug("Discovery lookup failed for {}", service, e);
            return fallback;
        }
    }

    private String lookup(String service) {
        try {
            var uri = UriComponentsBuilder.fromUri(properties.discoveryUrl())
                    .pathSegment("services", service).build(true).toUri();
            var response = client.get().uri(uri)
                    .retrieve().body(ServiceAddress.class);
            if (response == null || !service.equals(response.name()) || response.ip() == null) {
                throw new RestClientException("Invalid Discovery response");
            }
            return InetAddress.ofLiteral(response.ip()).getHostAddress();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    @Scheduled(fixedDelayString = "${APP_DISCOVERY_REGISTRATION_INTERVAL_MS:60000}")
    void register() {
        if (properties.discoveryUrl() == null || properties.advertiseIp() == null
                || properties.advertiseIp().isBlank()) {
            return;
        }
        try {
            var ip = InetAddress.ofLiteral(properties.advertiseIp()).getHostAddress();
            var uri = UriComponentsBuilder.fromUri(properties.discoveryUrl())
                    .pathSegment("services").build(true).toUri();
            client.post().uri(uri)
                    .body(Map.of("name", "attractions", "ip", ip))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Attractions registration in Discovery failed: {}", e.getMessage());
        }
    }

    private record ServiceAddress(String name, String ip) {}
}
