package pl.psso.attractions.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {
    @Bean(destroyMethod = "close")
    ExecutorService proposalExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    Clock planningClock() {
        return Clock.system(ZoneId.of("Europe/Warsaw"));
    }
}
