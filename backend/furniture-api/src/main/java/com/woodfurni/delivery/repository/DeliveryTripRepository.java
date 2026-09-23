package com.woodfurni.delivery.repository;

import com.woodfurni.delivery.enums.DeliveryTripStatus;
import com.woodfurni.delivery.model.DeliveryTrip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryTripRepository extends MongoRepository<DeliveryTrip, String> {

    Page<DeliveryTrip> findByStatus(DeliveryTripStatus status, Pageable pageable);

    @Query("{ 'tripNumber': { $regex: ?0, $options: 'i' } }")
    Page<DeliveryTrip> findByTripNumberContaining(String fragment, Pageable pageable);

    boolean existsByTripNumber(String tripNumber);
}
