package com.woodfurni.shipping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.woodfurni.shipping.dto.DistrictResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Loads the static HCM districts/wards reference from
 * {@code resources/shipping/hcm-districts.json} and serves lookup endpoints
 * to the customer-facing checkout page.
 *
 * The data is the canonical 22 inner districts of Hồ Chí Minh city with the
 * actual ward names from the 2021 administrative reorganisation. If the JSON
 * file is missing or malformed the service falls back to an empty list — the
 * endpoints still respond with 200 OK so the UI can render an empty state
 * instead of crashing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HcmAddressService {

    private final ObjectMapper objectMapper;

    private DistrictResponse cache;
    private boolean loaded = false;

    @PostConstruct
    public void init() {
        loadFromClasspath();
    }

    private synchronized void loadFromClasspath() {
        if (loaded) return;
        try (InputStream in = new ClassPathResource("shipping/hcm-districts.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            String city = root.path("city").asText("Hồ Chí Minh");
            List<DistrictResponse.DistrictItem> items = new ArrayList<>();
            for (JsonNode d : root.path("districts")) {
                String name = d.path("name").asText("");
                List<String> wards = new ArrayList<>();
                for (JsonNode w : d.path("wards")) wards.add(w.asText(""));
                items.add(DistrictResponse.DistrictItem.builder()
                        .name(name)
                        .wards(wards)
                        .build());
            }
            cache = DistrictResponse.builder()
                    .city(city)
                    .districts(items)
                    .build();
            loaded = true;
            log.info("[HcmAddress] Loaded {} districts / {} wards total",
                    items.size(),
                    items.stream().mapToInt(i -> i.getWards().size()).sum());
        } catch (Exception ex) {
            log.error("[HcmAddress] Failed to load hcm-districts.json — endpoints will return empty", ex);
            cache = DistrictResponse.builder()
                    .city("Hồ Chí Minh")
                    .districts(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Return the full districts + wards tree. Loaded once at startup, then
     * served from memory.
     */
    public DistrictResponse listAll() {
        if (!loaded) loadFromClasspath();
        return cache;
    }

    /**
     * Return just the wards for the requested district name (case-insensitive
     * match). Returns empty optional if the district is not in our reference
     * data.
     */
    public Optional<List<String>> findWards(String districtName) {
        if (!loaded) loadFromClasspath();
        if (districtName == null || districtName.isBlank()) return Optional.empty();
        String needle = normalize(districtName);
        return cache.getDistricts().stream()
                .filter(d -> normalize(d.getName()).equals(needle))
                .findFirst()
                .map(DistrictResponse.DistrictItem::getWards);
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).trim();
    }
}