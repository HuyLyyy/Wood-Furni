package com.woodfurni.auth.repository;

import com.woodfurni.auth.model.EmailOtp;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MongoDB repository for {@link EmailOtp}.
 */
@Repository
public interface EmailOtpRepository extends MongoRepository<EmailOtp, String> {

    /**
     * Most recent OTP for the given email + purpose. We expect at most one
     * active OTP per (email, purpose) at any time, but callers send again
     * after the previous one expires.
     */
    Optional<EmailOtp> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, String purpose);

    void deleteByEmailAndPurpose(String email, String purpose);
}
