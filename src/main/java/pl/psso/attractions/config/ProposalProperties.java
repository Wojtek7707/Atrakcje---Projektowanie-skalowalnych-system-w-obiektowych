package pl.psso.attractions.config;

import static pl.psso.attractions.client.TransportClient.MAX_PAIRS;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app")
public record ProposalProperties(@Min(0) int startMaxDistanceM, @Min(50) @Max(MAX_PAIRS) int maxCandidates) {}
