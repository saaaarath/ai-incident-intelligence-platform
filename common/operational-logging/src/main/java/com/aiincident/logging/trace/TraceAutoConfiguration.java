package com.aiincident.logging.trace;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication
public class TraceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TraceFilter.class)
    public TraceFilter traceFilter() {
        return new TraceFilter();
    }

    @Bean
    @ConditionalOnMissingBean(TraceClientHttpRequestInterceptor.class)
    public TraceClientHttpRequestInterceptor traceClientHttpRequestInterceptor() {
        return new TraceClientHttpRequestInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "traceRestClientCustomizer")
    public RestClientCustomizer traceRestClientCustomizer(TraceClientHttpRequestInterceptor traceInterceptor) {
        return restClientBuilder -> restClientBuilder.requestInterceptor(traceInterceptor);
    }
}
