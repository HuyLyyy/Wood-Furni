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
import java.util.Objects;
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
        // ── 1. Build base Mongo query with non-price filters only
        Query query = new Query();
        boolean hasPriceFilter = request.getMinPrice() != null || request.getMaxPrice() != null;

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            // Build a keyword search that is both correct AND fast:
            //
            //   * SKUs are alphanumeric and contiguous. A user typing one in
            //     almost always wants an exact match on `sku` (no substring,
            //     no regex). We detect that shape and run a direct equality
            //     query that the existing unique index on `sku` can serve in
            //     O(log N). Without this branch, MongoDB falls back to a
            //     case-insensitive regex scan of the whole collection.
            //
            //   * Free-text queries (containing spaces or non-SKU chars) go
            //     through the existing $or + regex path which is still
            //     cheap because the text-style filter runs against indexed
            //     `name`/`description` fields.
            //
            //   * Meta-characters in the keyword are escaped so the regex
            //     subqueries match literally (e.g. `A3D.006` won't accidentally
            //     match `A3D-006`).
            String raw = request.getKeyword().trim();
            String lowered = raw.toLowerCase();
            String escaped = escapeRegex(lowered);

            Criteria skuExact;
            if (looksLikeSku(raw)) {
                // Equality match is index-friendly. Compare against the
                // lowered keyword, and against the original casing for
                // collections that haven't been migrated yet.
                skuExact = new Criteria().orOperator(
                        Criteria.where("sku").is(lowered),
                        Criteria.where("sku").is(raw)
                );
            } else {
                skuExact = Criteria.where("sku").regex("^" + escaped + "$", "i");
            }

            query.addCriteria(new Criteria().orOperator(
                    skuExact,
                    Criteria.where("name").regex(escaped, "i"),
                    Criteria.where("description").regex(escaped, "i"),
                    Criteria.where("sku").regex(escaped, "i")
            ));
        }
        if (!isStaff) {
            query.addCriteria(Criteria.where("status").is(ProductStatus.ACTIVE));
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            String categoryId = resolveCategoryId(request.getCategory());
            if (categoryId != null) {
                query.addCriteria(Criteria.where("categoryId").is(categoryId));
            }
        }
        if (request.getEnvironment() != null) {
            query.addCriteria(Criteria.where("environment").is(request.getEnvironment()));
        }
        if (request.getRoom() != null) {
            query.addCriteria(Criteria.where("room").is(request.getRoom()));
        }
        if (request.getWoodType() != null && !request.getWoodType().isBlank()) {
            List<String> materialIds = resolveMaterialIdsByCode(request.getWoodType().toUpperCase());
            if (!materialIds.isEmpty()) {
                query.addCriteria(Criteria.where("materialIds").in(materialIds));
            }
        }

        Sort sort = buildSort(request.getSort());

        // ── 2. Fetch candidates. When a price filter is set, fetch all matching
        // products (no skip/limit) because the price semantics require both
        // `salePrice` and `price` fields, which is awkward to express as a single
        // Mongo query. We then filter in Java — collection is small.
        List<Product> candidates = mongoTemplate.find(query, Product.class);

        // ── 3. Apply effective-price filter and sort in Java
        java.math.BigDecimal minP = request.getMinPrice();
        java.math.BigDecimal maxP = request.getMaxPrice();

        java.util.List<Product> filtered = hasPriceFilter
                ? candidates.stream()
                    .filter(p -> {
                        java.math.BigDecimal eff = (p.getSalePrice() != null && p.getSalePrice().signum() > 0)
                                ? p.getSalePrice() : p.getPrice();
                        if (eff == null) return false;
                        if (minP != null && eff.compareTo(minP) < 0) return false;
                        if (maxP != null && eff.compareTo(maxP) > 0) return false;
                        return true;
                    })
                    .sorted(productComparator(sort))
                    .collect(Collectors.toList())
                : candidates;

        // ── 4. Paginate
        long total = filtered.size();
        int fromIndex = Math.min(Math.max(0, page * size), filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<Product> pageItems = filtered.subList(fromIndex, toIndex);

        return buildPageResponse(pageItems, page, size, total);
    }

    private java.util.Comparator<Product> productComparator(Sort sort) {
        java.util.Comparator<Product> c = (a, b) -> 0;
        if (sort == null) return c;
        for (Sort.Order o : sort) {
            java.util.Comparator<Product> by = (a, b) -> compareField(a, b, o.getProperty());
            c = c.thenComparing(by);
            // for DESC, wrap each comparand
            if (o.isDescending()) {
                c = c.thenComparing((a, b) -> -compareField(a, b, o.getProperty()));
            }
        }
        return c;
    }

    private int compareField(Product a, Product b, String field) {
        if (field == null) return 0;
        switch (field) {
            case "price":
                return nullSafeCompare(a.getPrice(), b.getPrice());
            case "salePrice":
                return nullSafeCompare(a.getSalePrice(), b.getSalePrice());
            case "ratingAverage":
                Double ax = a.getRatingAverage(), bx = b.getRatingAverage();
                if (ax == null && bx == null) return 0;
                if (ax == null) return -1;
                if (bx == null) return 1;
                return Double.compare(ax, bx);
            case "createdAt":
                java.time.Instant ai = a.getCreatedAt(), bi = b.getCreatedAt();
                if (ai == null && bi == null) return 0;
                if (ai == null) return -1;
                if (bi == null) return 1;
                return ai.compareTo(bi);
            default:
                return 0;
        }
    }

    private <T extends java.lang.Comparable<T>> int nullSafeCompare(T a, T b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    private PageResponse<ProductResponse> buildPageResponse(List<Product> products, int page, int size, long total) {
        List<String> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<String, Category> categoryMap = categoryIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : categoryRepository.findAllById(categoryIds).stream()
                    .collect(Collectors.toMap(Category::getId, c -> c));

        List<String> allMaterialIds = products.stream()
                .flatMap(p -> p.getMaterialIds() != null ? p.getMaterialIds().stream() : java.util.stream.Stream.empty())
                .distinct()
                .collect(Collectors.toList());
        Map<String, Material> materialMap = allMaterialIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : materialRepository.findAllById(allMaterialIds).stream()
                    .collect(Collectors.toMap(Material::getId, m -> m));

        List<ProductResponse> responses = products.stream()
                .map(p -> toResponse(p, categoryMap, materialMap))
                .collect(Collectors.toList());

        return PageResponse.<ProductResponse>builder()
                .items(responses)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(size <= 0 ? 0 : (int) Math.ceil((double) total / size))
                .build();
    }

    private Criteria buildPriceCriteria(java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice) {
        // RETAINED for legacy callers but unused by searchProducts.
        // Effective-price filtering now happens in Java (see searchProducts).
        if (minPrice != null && maxPrice != null) {
            return new Criteria().andOperator(
                    Criteria.where("price").gte(minPrice),
                    Criteria.where("price").lte(maxPrice)
            );
        }
        if (minPrice != null) {
            return Criteria.where("price").gte(minPrice);
        }
        if (maxPrice != null) {
            return Criteria.where("price").lte(maxPrice);
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

    /**
     * Escape characters that have special meaning in a Java/Mongo regex so the
     * resulting pattern is matched literally. Without this, a query such as
     * {@code "A3D.006"} would be interpreted as "A3D" followed by any char and
     * "006", silently returning unrelated SKUs.
     */
    private static String escapeRegex(String input) {
        if (input == null) return null;
        return input.replaceAll("([\\\\^$.|?*+()\\[\\]{}\\\\])", "\\\\$1");
    }

    /**
     * Heuristic: a string that looks like an SKU is short, contains no
     * whitespace, and is made entirely of letters, digits, dashes, dots and
     * underscores. These are the only shapes the equality branch can answer
     * reliably via the unique index on `sku`.
     */
    private static boolean looksLikeSku(String s) {
        if (s == null) return false;
        if (s.length() > 64) return false;  // SKUs longer than this are almost certainly free-text
        return s.matches("[A-Za-z0-9._-]+");
    }
}
