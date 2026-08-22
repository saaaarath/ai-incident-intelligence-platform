package com.aiincident.failure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication
public class FailureAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FailureInjectionService.class)
    public FailureInjectionService failureInjectionService(
            @Value("${failure.injection.enabled:true}") boolean globalEnabled,
            @Value("${failure.injection.security-token:}") String securityToken,
            @Value("${failure.injection.type:NONE}") String initialType,
            @Value("${failure.injection.latency-ms:3000}") long initialLatencyMs,
            @Value("${spring.application.name:unknown-service}") String serviceName,
            ObjectMapper objectMapper) {
        return new FailureInjectionService(
                globalEnabled,
                securityToken,
                initialType,
                initialLatencyMs,
                serviceName,
                objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(InternalFailureController.class)
    public InternalFailureController internalFailureController(FailureInjectionService failureInjectionService) {
        return new InternalFailureController(failureInjectionService);
    }

    @Bean
    @ConditionalOnMissingBean(FailureInjectionFilter.class)
    public FailureInjectionFilter failureInjectionFilter(FailureInjectionService failureInjectionService) {
        return new FailureInjectionFilter(failureInjectionService);
    }
}
