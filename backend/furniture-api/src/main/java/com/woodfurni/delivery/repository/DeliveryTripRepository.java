package com.woodfurni.delivery.repository;

import com.woodfurni.delivery.enums.DeliveryTripStatus;
import com.woodfurni.delivery.model.DeliveryTrip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DeliveryTripRepository extends MongoRepository<DeliveryTrip, String> {

    Page<DeliveryTrip> findByStatus(DeliveryTripStatus status, Pageable pageable);

    @Query("{ 'tripNumber': { $regex: ?0, $options: 'i' } }")
    Page<DeliveryTrip> findByTripNumberContaining(String fragment, Pageable pageable);

    /**
     * Lấy các chuyến xe có tripNumber nằm trong danh sách (case-insensitive exact match).
     * Dùng cho search theo nhiều mã chuyến cùng lúc (phân cách bởi khoảng trắng hoặc dấu phẩy).
     */
    List<DeliveryTrip> findByTripNumberInIgnoreCase(Collection<String> tripNumbers);

    boolean existsByTripNumber(String tripNumber);
}
