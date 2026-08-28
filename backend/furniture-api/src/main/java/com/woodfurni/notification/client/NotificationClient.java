package com.woodfurni.notification.client;

import com.woodfurni.notification.config.GatewayClientConfig.GatewayProperties;
import com.woodfurni.notification.dto.LowStockPayload;
import com.woodfurni.notification.dto.OrderCreatedPayload;
import com.woodfurni.notification.dto.OrderSentToWarehousePayload;
import com.woodfurni.notification.dto.OrderStatusPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client that forwards domain events to the Node.js gateway.
 *
 * Every method:
 *   1. No-ops when gateway.enabled=false (local dev / tests).
 *   2. Catches ALL exceptions and logs them at WARN — the realtime channel
 *      must NEVER abort the business flow that called it.
 *   3. Adds X-Internal-Secret header (shared secret with the gateway).
 *
 * Why RestTemplate (not WebClient)?
 *   - Spring Boot starter-web already pulls in RestTemplate support.
 *   - No need to add the spring-webflux dependency just for 3 fire-and-forget calls.
 *   - Synchronous + bounded timeout keeps the call site simple.
 */
@Slf4j
@Component
public class NotificationClient {

    private final RestTemplate restTemplate;
    private final GatewayProperties properties;

    public NotificationClient(
            @Qualifier("gatewayRestTemplate") RestTemplate restTemplate,
            GatewayProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * Notify SALES/ADMIN sockets that a new order was created.
     * Called from OrderService.checkout() after the Order is persisted.
     */
    public void notifyOrderCreated(String orderId, String orderNumber, java.math.BigDecimal totalAmount) {
        if (!isEnabled()) return;
        OrderCreatedPayload payload = OrderCreatedPayload.builder()
                .orderId(orderId)
                .orderNumber(orderNumber)
                .totalAmount(totalAmount)
                .build();
        post("/internal/notify/order-created", payload);
    }

    /**
     * Notify the customer's socket that their order status changed.
     * Called from OrderService.updateStatus().
     */
    public void notifyOrderStatus(String orderId, String orderNumber, String status, String customerId) {
        if (!isEnabled()) return;
        OrderStatusPayload payload = OrderStatusPayload.builder()
                .orderId(orderId)
                .orderNumber(orderNumber)
                .status(status)
                .customerId(customerId)
                .build();
        post("/internal/notify/order-status", payload);
    }

    /**
     * Notify WAREHOUSE/ADMIN sockets that a product crossed the low-stock threshold.
     * Called from InventoryService.commit() and adjust().
     */
    public void notifyLowStock(String productId, String productName,
                               int quantityOnHand, int threshold) {
        if (!isEnabled()) return;
        LowStockPayload payload = LowStockPayload.builder()
                .productId(productId)
                .productName(productName)
                .quantityOnHand(quantityOnHand)
                .threshold(threshold)
                .build();
        post("/internal/notify/low-stock", payload);
    }

    /**
     * Notify WAREHOUSE/ADMIN sockets that an order has been sent to the warehouse
     * (CONFIRMED → PROCESSING transition). Called from OrderService after a
     * successful send-to-warehouse transition.
     */
    public void notifyOrderSentToWarehouse(String orderId, String orderNumber, int itemCount) {
        if (!isEnabled()) return;
        OrderSentToWarehousePayload payload = OrderSentToWarehousePayload.builder()
                .orderId(orderId)
                .orderNumber(orderNumber)
                .itemCount(itemCount)
                .build();
        post("/internal/notify/order-sent-to-warehouse", payload);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void post(String path, Object payload) {
        try {
            String url = properties.getBaseUrl() + path;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Secret", properties.getInternalSecret());

            HttpEntity<Object> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("[notify] Gateway responded {} for {}: {}", response.getStatusCode(), path, response.getBody());
            } else {
                log.debug("[notify] Gateway accepted {} → {}", path, response.getBody());
            }
        } catch (Exception ex) {
            // Realtime channel is best-effort — never let it break the business flow.
            log.warn("[notify] Failed to call gateway {}: {}", path, ex.getMessage());
        }
    }

    private boolean isEnabled() {
        return properties != null && properties.isEnabled();
    }
}