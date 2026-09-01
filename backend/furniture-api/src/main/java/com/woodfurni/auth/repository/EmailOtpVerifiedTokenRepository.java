package com.woodfurni.auth.repository;

import com.woodfurni.auth.model.EmailOtpVerifiedToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailOtpVerifiedTokenRepository
        extends MongoRepository<EmailOtpVerifiedToken, String> {

    Optional<EmailOtpVerifiedToken> findByToken(String token);

    void deleteByEmailAndPurpose(String email, String purpose);
}
