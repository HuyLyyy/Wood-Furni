package com.woodfurni.shipping.repository;

import com.woodfurni.shipping.model.ShippingDistance;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link ShippingDistance} documents.
 *
 * Lookup is by (city, district) — the compound unique index guarantees at most
 * one result for any pair.
 */
@Repository
public interface ShippingDistanceRepository extends MongoRepository<ShippingDistance, String> {

    /**
     * Find the distance record for an exact city + district pair.
     *
     * @param city    exact city name (case-sensitive)
     * @param district exact district name (case-sensitive)
     * @return the matching record, or empty if not found
     */
    Optional<ShippingDistance> findByCityAndDistrict(String city, String district);

    /**
     * Delete all distance records for a given city.
     */
    void deleteByCity(String city);

    /**
     * Check whether a city already has any distance records.
     */
    boolean existsByCity(String city);
}
