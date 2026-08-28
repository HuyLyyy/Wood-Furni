package com.woodfurni.promotion.repository;

import com.woodfurni.promotion.enums.PromotionStatus;
import com.woodfurni.promotion.model.Promotion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends MongoRepository<Promotion, String> {

    Optional<Promotion> findByCode(String code);

    Optional<Promotion> findByCodeIgnoreCase(String code);

    boolean existsByCode(String code);

    List<Promotion> findByStatus(PromotionStatus status);

    List<Promotion> findByEndDateBeforeAndStatus(Instant now, PromotionStatus status);
}
