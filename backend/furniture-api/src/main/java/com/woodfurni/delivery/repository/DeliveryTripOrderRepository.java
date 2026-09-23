package com.woodfurni.delivery.repository;

import com.woodfurni.delivery.model.DeliveryTripOrder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryTripOrderRepository extends MongoRepository<DeliveryTripOrder, String> {

    List<DeliveryTripOrder> findByTripIdOrderBySequenceAsc(String tripId);

    Optional<DeliveryTripOrder> findByTripIdAndOrderId(String tripId, String orderId);

    List<DeliveryTripOrder> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);

    void deleteByTripId(String tripId);
}
