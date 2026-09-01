package com.woodfurni.catalog.product.service;

import com.woodfurni.catalog.category.model.Category;
import com.woodfurni.catalog.category.repository.CategoryRepository;
import com.woodfurni.catalog.material.model.Material;
import com.woodfurni.catalog.material.repository.MaterialRepository;
import com.woodfurni.catalog.product.dto.*;
import com.woodfurni.catalog.product.enums.ProductStatus;
import com.woodfurni.catalog.product.model.Product;
import com.woodfurni.catalog.product.repository.ProductRepository;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.common.PageResponse;
import com.woodfurni.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MaterialRepository materialRepository;
    private final MongoTemplate mongoTemplate;
    private final InventoryService inventoryService;

    /**
     * Search and filter products with pagination.
     *
     * Search logic:
     * - keyword: text search on name + description (requires text index)
     * - category: filter by categoryId (resolves slug to id if needed)
     * - environment: exact match on environment enum
     * - woodType: filter by material code (resolves material code to materialIds)
     * - minPrice/maxPrice: range on effective price (salePrice if set, else price)
     *
     * Access control:
     * - Public: only returns ACTIVE products
     * - CONTENT/ADMIN: returns all products including DRAFT
     *
     * Sorting:
     * - Default: -createdAt (newest first)
     * - Supported: price,asc / -price / ratingAverage
     */
    public PageResponse<ProductResponse> searchProducts(ProductSearchRequest request, int page, int size, boolean isStaff) {
        Query query = new Query();
        List<Criteria> allCriteria = new ArrayList<>();

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            String kw = request.getKeyword().toLowerCase().trim();
            Criteria keywordCriteria = new Criteria().orOperator(
                    Criteria.where("name").regex(kw, "i"),
                    Criteria.where("description").regex(kw, "i"),
                    Criteria.where("sku").regex(kw, "i")
            );
            allCriteria.add(keywordCriteria);
        }

        if (!isStaff) {
            allCriteria.add(Criteria.where("status").is(ProductStatus.ACTIVE));
        }

        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            String categoryId = resolveCategoryId(request.getCategory());
            if (categoryId != null) {
                allCriteria.add(Criteria.where("categoryId").is(categoryId));
            }
        }

        if (request.getEnvironment() != null) {
            allCriteria.add(Criteria.where("environment").is(request.getEnvironment()));
        }

        if (request.getRoom() != null) {
            allCriteria.add(Criteria.where("room").is(request.getRoom()));
        }

        if (request.getWoodType() != null && !request.getWoodType().isBlank()) {
            List<String> materialIds = resolveMaterialIdsByCode(request.getWoodType().toUpperCase());
            if (!materialIds.isEmpty()) {
                allCriteria.add(Criteria.where("materialIds").in(materialIds));
            }
        }

        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            Criteria priceCriteria = buildPriceCriteria(request.getMinPrice(), request.getMaxPrice());
            allCriteria.add(priceCriteria);
        }

        if (!allCriteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(allCriteria.toArray(new Criteria[0])));
        }

        Sort sort = buildSort(request.getSort());
        query.with(sort);

        long total = mongoTemplate.count(query, Product.class);

        query.with(PageRequest.of(page, size, sort));
        List<Product> products = mongoTemplate.find(query, Product.class);

        List<String> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .distinct()
                .collect(Collectors.toList());
        Map<String, Category> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        List<String> allMaterialIds = products.stream()
                .flatMap(p -> p.getMaterialIds() != null ? p.getMaterialIds().stream() : java.util.stream.Stream.empty())
                .distinct()
                .collect(Collectors.toList());
        Map<String, Material> materialMap = materialRepository.findAllById(allMaterialIds).stream()
                .collect(Collectors.toMap(Material::getId, m -> m));

        List<ProductResponse> responses = products.stream()
                .map(p -> toResponse(p, categoryMap, materialMap))
                .collect(Collectors.toList());

        return PageResponse.<ProductResponse>builder()
                .items(responses)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / size))
                .build();
    }

    private Criteria buildPriceCriteria(java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice) {
        // The "effective" price of a product = salePrice if set & > 0, otherwise price.
        // We need to filter products whose effective price falls in [minPrice, maxPrice].
        //
        // MongoDB representation:
        //   effectivePrice = salePrice when salePrice != null
        //                   = price      when salePrice == null
        //
        // To express this we split into two arms per bound, joined by AND across
        // bounds and OR within each bound. Each arm is built with andOperator()
        // so the predicates are correctly grouped in the generated Mongo query.
        //
        // Example minPrice only → effective >= minPrice:
        //   { $or: [
        //       { salePrice: { $gte: minPrice } },
        //       { $and: [ { salePrice: null }, { price: { $gte: minPrice } } ] }
        //     ] }

        Criteria minArm = null;
        Criteria maxArm = null;

        if (minPrice != null) {
            Criteria minSale = Criteria.where("salePrice").gte(minPrice);
            Criteria minNoSale = new Criteria().andOperator(
                    Criteria.where("salePrice").is(null),
                    Criteria.where("price").gte(minPrice)
            );
            minArm = new Criteria().orOperator(minSale, minNoSale);
        }
        if (maxPrice != null) {
            Criteria maxSale = Criteria.where("salePrice").lte(maxPrice);
            Criteria maxNoSale = new Criteria().andOperator(
                    Criteria.where("salePrice").is(null),
                    Criteria.where("price").lte(maxPrice)
            );
            maxArm = new Criteria().orOperator(maxSale, maxNoSale);
        }

        if (minArm != null && maxArm != null) {
            return new Criteria().andOperator(minArm, maxArm);
        }
        if (minArm != null) {
            return minArm;
        }
        if (maxArm != null) {
            return maxArm;
        }
        return new Criteria();
    }

    private String resolveCategoryId(String category) {
        if (categoryRepository.findBySlug(category).isPresent()) {
            return categoryRepository.findBySlug(category).get().getId();
        }
        if (categoryRepository.existsById(category)) {
            return category;
        }
        return null;
    }

    private List<String> resolveMaterialIdsByCode(String code) {
        return materialRepository.findByCode(code)
                .map(m -> List.of(m.getId()))
                .orElse(List.of());
    }

    private Sort buildSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String field;
        Sort.Direction direction = Sort.Direction.ASC;

        if (sortParam.startsWith("-")) {
            direction = Sort.Direction.DESC;
            field = sortParam.substring(1);
        } else if (sortParam.contains(",")) {
            String[] parts = sortParam.split(",");
            field = parts[0].trim();
            direction = "desc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.DESC : Sort.Direction.ASC;
        } else {
            field = sortParam;
        }

        return Sort.by(direction, field);
    }

    public ProductResponse getById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
        return toResponse(product, null, null);
    }

    public ProductResponse getBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with slug: " + slug));
        return toResponse(product, null, null);
    }

    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("SKU already exists: " + request.getSku());
        }

        if (!categoryRepository.existsById(request.getCategoryId())) {
            throw new IllegalArgumentException("Category not found: " + request.getCategoryId());
        }

        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = generateSlug(request.getName());
        }
        if (productRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Slug already exists: " + slug);
        }

        Product product = Product.builder()
                .sku(request.getSku())
                .slug(slug)
                .name(request.getName())
                .categoryId(request.getCategoryId())
                .materialIds(request.getMaterialIds())
                .environment(request.getEnvironment())
                .room(request.getRoom())
                .dimensions(request.getDimensions())
                .weight(request.getWeight())
                .color(request.getColor())
                .finish(request.getFinish())
                .price(request.getPrice())
                .salePrice(request.getSalePrice())
                .images(request.getImages())
                .description(request.getDescription())
                .warranty(request.getWarranty())
                .status(ProductStatus.DRAFT)
                .ratingAverage(0.0)
                .ratingCount(0)
                .build();

        Product saved = productRepository.save(product);

        inventoryService.initForProduct(saved.getId());

        return toResponse(saved, null, null);
    }

    public ProductResponse update(String id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));

        if (request.getSku() != null && !request.getSku().equals(product.getSku())) {
            if (productRepository.existsBySku(request.getSku())) {
                throw new IllegalArgumentException("SKU already exists: " + request.getSku());
            }
            product.setSku(request.getSku());
        }

        if (request.getName() != null) {
            product.setName(request.getName());
        }

        if (request.getSlug() != null && !request.getSlug().equals(product.getSlug())) {
            if (productRepository.existsBySlug(request.getSlug())) {
                throw new IllegalArgumentException("Slug already exists: " + request.getSlug());
            }
            product.setSlug(request.getSlug());
        }

        if (request.getCategoryId() != null) {
            if (!categoryRepository.existsById(request.getCategoryId())) {
                throw new IllegalArgumentException("Category not found: " + request.getCategoryId());
            }
            product.setCategoryId(request.getCategoryId());
        }

        if (request.getMaterialIds() != null) {
            product.setMaterialIds(request.getMaterialIds());
        }
        if (request.getEnvironment() != null) {
            product.setEnvironment(request.getEnvironment());
        }
        if (request.getRoom() != null) {
            product.setRoom(request.getRoom());
        }
        if (request.getDimensions() != null) {
            product.setDimensions(request.getDimensions());
        }
        if (request.getWeight() != null) {
            product.setWeight(request.getWeight());
        }
        if (request.getColor() != null) {
            product.setColor(request.getColor());
        }
        if (request.getFinish() != null) {
            product.setFinish(request.getFinish());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getSalePrice() != null) {
            product.setSalePrice(request.getSalePrice());
        }
        if (request.getImages() != null) {
            product.setImages(request.getImages());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getWarranty() != null) {
            product.setWarranty(request.getWarranty());
        }

        Product saved = productRepository.save(product);
        return toResponse(saved, null, null);
    }

    public ProductResponse changeStatus(String id, ProductStatus newStatus) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));

        if (newStatus == ProductStatus.ACTIVE) {
            if (product.getImages() == null || product.getImages().isEmpty()) {
                throw new IllegalArgumentException("Cannot publish product without at least one image");
            }
        }

        product.setStatus(newStatus);
        Product saved = productRepository.save(product);
        return toResponse(saved, null, null);
    }

    public void delete(String id) {
        if (!productRepository.existsById(id)) {
            throw new EntityNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    public void updateRating(String productId, Double newAverage, Integer newCount) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId));
        product.setRatingAverage(newAverage);
        product.setRatingCount(newCount);
        productRepository.save(product);
    }

    private ProductResponse toResponse(Product product, Map<String, Category> categoryMap, Map<String, Material> materialMap) {
        String categoryName = null;
        if (categoryMap != null && product.getCategoryId() != null) {
            Category cat = categoryMap.get(product.getCategoryId());
            if (cat != null) {
                categoryName = cat.getName();
            }
        }

        List<String> materialNames = null;
        if (materialMap != null && product.getMaterialIds() != null && !product.getMaterialIds().isEmpty()) {
            materialNames = product.getMaterialIds().stream()
                    .map(materialMap::get)
                    .filter(m -> m != null)
                    .map(Material::getName)
                    .collect(Collectors.toList());
        }

        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .slug(product.getSlug())
                .name(product.getName())
                .categoryId(product.getCategoryId())
                .categoryName(categoryName)
                .materialIds(product.getMaterialIds())
                .materialNames(materialNames)
                .environment(product.getEnvironment())
                .room(product.getRoom())
                .dimensions(product.getDimensions())
                .weight(product.getWeight())
                .color(product.getColor())
                .finish(product.getFinish())
                .price(product.getPrice())
                .salePrice(product.getSalePrice())
                .images(product.getImages())
                .description(product.getDescription())
                .warranty(product.getWarranty())
                .status(product.getStatus())
                .ratingAverage(product.getRatingAverage())
                .ratingCount(product.getRatingCount())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
                .replaceAll("[èéẹẻẽêềếệểễ]", "e")
                .replaceAll("[ìíịỉĩ]", "i")
                .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
                .replaceAll("[ùúụủũưừứựửữ]", "u")
                .replaceAll("[ỳýỵỷỹ]", "y")
                .replaceAll("đ", "d")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
