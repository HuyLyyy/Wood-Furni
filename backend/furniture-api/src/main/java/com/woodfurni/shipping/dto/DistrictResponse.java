package com.woodfurni.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for {@code GET /shipping/districts}.
 *
 * Returns the list of HCM districts. Each district carries the list of wards
 * the customer can pick from in the second step of the address picker.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistrictResponse {

    private String city;
    private List<DistrictItem> districts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistrictItem {
        private String name;
        private List<String> wards;
    }
}