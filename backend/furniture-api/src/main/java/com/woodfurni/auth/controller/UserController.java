package com.woodfurni.auth.controller;

import com.woodfurni.auth.dto.AddressRequest;
import com.woodfurni.auth.enums.Role;
import com.woodfurni.auth.enums.UserStatus;
import com.woodfurni.auth.model.Address;
import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.common.ApiResponse;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.delivery.dto.AssemblerResponse;
import com.woodfurni.delivery.dto.DriverResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User profile controller.
 * Base path: /users
 *
 * Handles user address management (CRUD on embedded addresses inside User).
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me/addresses")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List current user's addresses")
    public ResponseEntity<ApiResponse<List<Address>>> listAddresses(
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        return ResponseEntity.ok(ApiResponse.success(user.getAddresses()));
    }

    @PostMapping("/me/addresses")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add a new address to user profile")
    public ResponseEntity<ApiResponse<Address>> addAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddressRequest request) {
        String userId = userDetails.getUsername();
        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new EntityNotFoundException("User not found: " + userId);
        }

        var user = userOpt.get();
        if (user.getAddresses() == null) {
            user.setAddresses(new ArrayList<>());
        }

        String addressId = request.getId() != null && !request.getId().isBlank()
                ? request.getId()
                : "addr_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);

        Address address = Address.builder()
                .id(addressId)
                .label(request.getLabel())
                .line1(request.getLine1())
                .ward(request.getWard())
                .district(request.getDistrict())
                .city(request.getCity())
                .phone(request.getPhone())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();

        // If this is default, clear existing defaults
        if (address.isDefault()) {
            user.getAddresses().forEach(a -> a.setDefault(false));
        }

        user.getAddresses().add(address);
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success(address));
    }

    @PutMapping("/me/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update an existing address")
    public ResponseEntity<ApiResponse<Address>> updateAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String addressId,
            @Valid @RequestBody AddressRequest request) {
        String userId = userDetails.getUsername();
        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new EntityNotFoundException("User not found: " + userId);
        }

        var user = userOpt.get();
        Address target = null;
        for (Address a : user.getAddresses()) {
            if (addressId.equals(a.getId())) {
                target = a;
                break;
            }
        }

        if (target == null) {
            throw new EntityNotFoundException("Address not found: " + addressId);
        }

        // If this becomes default, clear existing defaults
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            user.getAddresses().forEach(a -> a.setDefault(false));
        }

        target.setLabel(request.getLabel());
        target.setLine1(request.getLine1());
        target.setWard(request.getWard());
        target.setDistrict(request.getDistrict());
        target.setCity(request.getCity());
        target.setPhone(request.getPhone());
        target.setDefault(Boolean.TRUE.equals(request.getIsDefault()));

        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(target));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete an address")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String addressId) {
        String userId = userDetails.getUsername();
        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new EntityNotFoundException("User not found: " + userId);
        }

        var user = userOpt.get();
        boolean removed = user.getAddresses().removeIf(a -> addressId.equals(a.getId()));

        if (!removed) {
            throw new EntityNotFoundException("Address not found: " + addressId);
        }

        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success("Address deleted", null));
    }

    /**
     * Danh sách tài xế (users có role DRIVER và status ACTIVE).
     * Dùng cho dropdown chọn tài xế khi tạo chuyến xe.
     */
    @GetMapping("/drivers")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'SALES')")
    @Operation(summary = "List drivers (users with role DRIVER)",
               description = "Returns ACTIVE users whose role is DRIVER. Used by delivery module.")
    public ResponseEntity<ApiResponse<List<DriverResponse>>> listDrivers() {
        List<User> drivers = userRepository.findByRole(Role.DRIVER);
        List<DriverResponse> result = drivers.stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(u -> DriverResponse.builder()
                        .id(u.getId())
                        .fullName(u.getFullName())
                        .email(u.getEmail())
                        .phone(u.getPhone())
                        .active(u.getStatus() == UserStatus.ACTIVE)
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Danh sách nhân viên lắp ráp phụ trợ (role ASSEMBLER, status ACTIVE).
     * Dùng cho phần chọn lắp ráp kèm theo chuyến xe — tối đa 2 người/chuyến.
     */
    @GetMapping("/assemblers")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'SALES')")
    @Operation(summary = "List assemblers (users with role ASSEMBLER)",
               description = "Returns ACTIVE users whose role is ASSEMBLER. Used by delivery module " +
                       "as auxiliary staff that may accompany a trip.")
    public ResponseEntity<ApiResponse<List<AssemblerResponse>>> listAssemblers() {
        List<User> assemblers = userRepository.findByRole(Role.ASSEMBLER);
        List<AssemblerResponse> result = assemblers.stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(u -> AssemblerResponse.builder()
                        .id(u.getId())
                        .fullName(u.getFullName())
                        .email(u.getEmail())
                        .phone(u.getPhone())
                        .active(u.getStatus() == UserStatus.ACTIVE)
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
