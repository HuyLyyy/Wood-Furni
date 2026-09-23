package com.woodfurni.config;

import com.woodfurni.auth.enums.Role;
import com.woodfurni.auth.enums.UserStatus;
import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seed default delivery staff (drivers and assemblers) on application startup.
 * 
 * Creates:
 * - 3 DRIVER accounts
 * - 5 ASSEMBLER accounts
 * 
 * These accounts are pre-created for easy trip assignment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryStaffInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_PASSWORD = "woodfurni123";

    @Override
    public void run(String... args) {
        createDrivers();
        createAssemblers();
    }

    private void createDrivers() {
        List<User> drivers = List.of(
            createDriverUser("TX001", "Nguyễn Văn An", "nguyenvanan@woodfurni.com", "0901234567"),
            createDriverUser("TX002", "Trần Văn Bình", "tranvanbinh@woodfurni.com", "0901234568"),
            createDriverUser("TX003", "Lê Văn Cường", "levancuong@woodfurni.com", "0901234569")
        );

        for (User driver : drivers) {
            if (!userRepository.existsByEmail(driver.getEmail())) {
                userRepository.save(driver);
                log.info("[DeliveryStaffInit] Created driver: {} ({})", driver.getFullName(), driver.getEmail());
            } else {
                log.info("[DeliveryStaffInit] Driver already exists: {}", driver.getEmail());
            }
        }
    }

    private void createAssemblers() {
        List<User> assemblers = List.of(
            createAssemblerUser("LT001", "Phạm Thị Dung", "phamthidung@woodfurni.com", "0902345671"),
            createAssemblerUser("LT002", "Hoàng Văn Em", "hoangvanem@woodfurni.com", "0902345672"),
            createAssemblerUser("LT003", "Ngô Thị F", "ngothif@woodfurni.com", "0902345673"),
            createAssemblerUser("LT004", "Đặng Văn G", "dangvang@woodfurni.com", "0902345674"),
            createAssemblerUser("LT005", "Bùi Thị H", "buithih@woodfurni.com", "0902345675")
        );

        for (User assembler : assemblers) {
            if (!userRepository.existsByEmail(assembler.getEmail())) {
                userRepository.save(assembler);
                log.info("[DeliveryStaffInit] Created assembler: {} ({})", assembler.getFullName(), assembler.getEmail());
            } else {
                log.info("[DeliveryStaffInit] Assembler already exists: {}", assembler.getEmail());
            }
        }
    }

    private User createDriverUser(String code, String fullName, String email, String phone) {
        return User.builder()
                .email(email)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .fullName(fullName)
                .phone(phone)
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .addresses(new ArrayList<>())
                .build();
    }

    private User createAssemblerUser(String code, String fullName, String email, String phone) {
        return User.builder()
                .email(email)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .fullName(fullName)
                .phone(phone)
                .role(Role.ASSEMBLER)
                .status(UserStatus.ACTIVE)
                .addresses(new ArrayList<>())
                .build();
    }
}
