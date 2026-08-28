package com.woodfurni.catalog.product.dto;

import com.woodfurni.catalog.product.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStatusRequest {

    private ProductStatus status;
}
