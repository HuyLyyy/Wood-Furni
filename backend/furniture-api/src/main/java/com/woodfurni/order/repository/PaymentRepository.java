package com.woodfurni.order.repository;

import com.woodfurni.order.model.Payment;
import com.woodfurni.order.enums.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findByStatus(PaymentStatus status);

    boolean existsByOrderId(String orderId);
}
