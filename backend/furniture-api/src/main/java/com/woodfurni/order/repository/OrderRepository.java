package com.woodfurni.order.repository;

import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    /**
     * Search orders by orderNumber using case-insensitive partial match.
     * E.g., "ORD-20260826" matches "ORD-20260826-0001".
     */
    @Query("{ 'orderNumber': { $regex: ?0, $options: 'i' } }")
    Page<Order> findByOrderNumberContaining(String orderNumberFragment, Pageable pageable);

    /**
     * Find orders by multiple exact orderNumbers using $in operator.
     * Accepts a list of order numbers and returns all matching orders.
     */
    List<Order> findByOrderNumberIn(List<String> orderNumbers);

    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(String customerId, OrderStatus status, Pageable pageable);

    long countByCustomerId(String customerId);

    List<Order> findByCreatedAtBetween(Instant start, Instant end);

    long countByCreatedAtBetween(Instant start, Instant end);

    long countByCreatedAtBetweenAndStatus(Instant start, Instant end, OrderStatus status);
}
