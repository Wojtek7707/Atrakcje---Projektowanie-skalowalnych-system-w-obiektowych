package pl.psso.attractions.config;

import java.net.URI;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.downstream")
public record DownstreamProperties(URI weatherUrl, URI transportUrl, URI discoveryUrl,
                                   String advertiseIp, @Min(1) int dependencyTimeoutMs,
                                   @Min(1) int circuitOpenMs) {}
