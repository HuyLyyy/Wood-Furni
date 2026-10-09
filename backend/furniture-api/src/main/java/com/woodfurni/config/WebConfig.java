package com.woodfurni.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Force UTF-8 encoding for every HTTP response.
 *
 * Why this exists:
 * The dashboard chart "Top 10 sản phẩm bán chạy" was rendering Vietnamese
 * names as mojibake ("Bá»" instead of "Bộ", "Gá»" instead of "Gỗ").
 * Root cause: Spring's default response charset comes from the servlet
 * container (Tomcat) and, depending on the platform locale, can be sent
 * without an explicit `charset=utf-8`. Clients then fall back to
 * Windows-1252 / Latin-1, mis-decoding the multi-byte UTF-8 bytes.
 *
 * Fix: pin every text/json response to UTF-8 by:
 *   1. Overriding the default string converter to write UTF-8.
 *   2. Overriding the default Jackson converter to produce
 *      `application/json;charset=UTF-8` instead of `application/json`.
 *
 * Also installs a filter that sets the response Content-Type charset so
 * even error pages (which bypass the converters) emit `;charset=UTF-8`.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
        // String converter — used for plain text responses
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setSupportedMediaTypes(List.of(
                new MediaType("text", "plain", StandardCharsets.UTF_8),
                new MediaType("text", "html", StandardCharsets.UTF_8),
                new MediaType("application", "json", StandardCharsets.UTF_8)
        ));
        converters.add(0, stringConverter);

        // Jackson converter — used for every JSON response
        MappingJackson2HttpMessageConverter jacksonConverter =
                new MappingJackson2HttpMessageConverter();
        jacksonConverter.setSupportedMediaTypes(List.of(
                new MediaType("application", "json", StandardCharsets.UTF_8),
                new MediaType("application", "*+json", StandardCharsets.UTF_8)
        ));
        jacksonConverter.setDefaultCharset(StandardCharsets.UTF_8);
        converters.add(1, jacksonConverter);
    }

    /**
     * Servlet filter that stamps `;charset=UTF-8` on every response,
     * including error pages that bypass the message converters above.
     */
    @Bean
    public jakarta.servlet.Filter characterEncodingFilter() {
        return (servletRequest, servletResponse, filterChain) -> {
            jakarta.servlet.http.HttpServletResponse response =
                    (jakarta.servlet.http.HttpServletResponse) servletResponse;
            response.setCharacterEncoding("UTF-8");
            filterChain.doFilter(servletRequest, response);
        };
    }
}
