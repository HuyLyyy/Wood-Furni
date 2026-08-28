package com.woodfurni.catalog.material.service;

import com.woodfurni.catalog.material.dto.MaterialRequest;
import com.woodfurni.catalog.material.dto.MaterialResponse;
import com.woodfurni.catalog.material.model.Material;
import com.woodfurni.catalog.material.repository.MaterialRepository;
import com.woodfurni.common.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;

    public List<MaterialResponse> getAll() {
        return materialRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public MaterialResponse getById(String id) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Material not found with id: " + id));
        return toResponse(material);
    }

    public List<MaterialResponse> search(String keyword) {
        return materialRepository.findByNameContainingIgnoreCase(keyword).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public MaterialResponse create(MaterialRequest request) {
        if (materialRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Material name already exists: " + request.getName());
        }
        if (materialRepository.existsByCode(request.getCode().toUpperCase())) {
            throw new IllegalArgumentException("Material code already exists: " + request.getCode());
        }

        Material material = Material.builder()
                .name(request.getName())
                .code(request.getCode().toUpperCase())
                .description(request.getDescription())
                .build();

        Material saved = materialRepository.save(material);
        return toResponse(saved);
    }

    public MaterialResponse update(String id, MaterialRequest request) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Material not found with id: " + id));

        if (request.getName() != null && !request.getName().equals(material.getName())) {
            if (materialRepository.existsByName(request.getName())) {
                throw new IllegalArgumentException("Material name already exists: " + request.getName());
            }
            material.setName(request.getName());
        }

        if (request.getCode() != null && !request.getCode().equalsIgnoreCase(material.getCode())) {
            if (materialRepository.existsByCode(request.getCode().toUpperCase())) {
                throw new IllegalArgumentException("Material code already exists: " + request.getCode());
            }
            material.setCode(request.getCode().toUpperCase());
        }

        if (request.getDescription() != null) {
            material.setDescription(request.getDescription());
        }

        Material saved = materialRepository.save(material);
        return toResponse(saved);
    }

    public void delete(String id) {
        if (!materialRepository.existsById(id)) {
            throw new EntityNotFoundException("Material not found with id: " + id);
        }
        materialRepository.deleteById(id);
    }

    private MaterialResponse toResponse(Material material) {
        return MaterialResponse.builder()
                .id(material.getId())
                .name(material.getName())
                .code(material.getCode())
                .description(material.getDescription())
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .build();
    }
}
