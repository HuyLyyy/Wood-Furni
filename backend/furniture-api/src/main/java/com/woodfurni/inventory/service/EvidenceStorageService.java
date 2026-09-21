package com.woodfurni.inventory.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Saves inventory-adjustment evidence files to local disk under
 * {@code {storageRoot}/inventory-evidence/{yyyy-MM}/{uuid}{.ext}}.
 *
 * Why local disk?
 *   - The project already runs on Render persistent disk for uploads in
 *     other modules; keeps the deployment topology simple.
 *   - Mongo GridFS adds a download endpoint + stream overhead that this
 *     single feature does not need.
 *
 * Storage root resolution (highest priority first):
 *   1. {@code EVIDENCE_STORAGE_DIR} env var (recommended in production)
 *   2. {@code java.io.tmpdir}/woodfurni-evidence (fallback for dev /
 *      ephemeral environments; survives single deploy, but gone after restart)
 *
 * For production durability, mount a persistent volume and point the env var
 * at it. Without it, evidence files will vanish on every container restart.
 */
@Slf4j
@Service
public class EvidenceStorageService {

    /** File types the system accepts for inventory adjustments. */
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
            "application/vnd.ms-excel",                                            // .xls
            "application/octet-stream"                                             // some browsers
    );

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".xlsx", ".xls");

    private static final long MAX_BYTES = 10L * 1024L * 1024L; // 10 MB

    public record StoredFile(String originalName, String storedFileName,
                             String publicUrl, long size) {}

    private final Path baseDir;

    public EvidenceStorageService() {
        this.baseDir = resolveBaseDir();
        try {
            Files.createDirectories(baseDir);
            log.info("[EvidenceStorageService] Storage directory ready: {}", baseDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("[EvidenceStorageService] Cannot create storage directory {}", baseDir, e);
            throw new IllegalStateException("Cannot initialise evidence storage directory", e);
        }
    }

    private Path resolveBaseDir() {
        String env = System.getenv("EVIDENCE_STORAGE_DIR");
        if (env != null && !env.isBlank()) {
            return Paths.get(env, "inventory-evidence");
        }
        // Fallback to system temp. Good enough for dev; configure env var for prod.
        return Paths.get(System.getProperty("java.io.tmpdir"), "woodfurni-evidence", "inventory-evidence");
    }

    /**
     * Validate + persist the uploaded file. Throws IllegalArgumentException
     * with a user-friendly message when validation fails.
     *
     * Returns metadata; the caller decides what to do with it (persist URL in DB, etc).
     */
    public StoredFile save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File minh chứng là bắt buộc");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File vượt quá dung lượng cho phép (10MB)");
        }

        String original = file.getOriginalFilename();
        if (original == null) original = "evidence.xlsx";
        String ext = extractExtension(original).toLowerCase();

        // Be defensive: validate by extension too, since some browsers/clients
        // ship the wrong Content-Type.
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException(
                    "Chỉ chấp nhận file Excel (.xlsx, .xls). File gửi lên: " + original);
        }
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType)
                && !"application/octet-stream".equals(contentType)) {
            // We don't hard-fail on Content-Type to be tolerant of edge browsers,
            // but log it for visibility.
            log.warn("[EvidenceStorageService] Unexpected Content-Type={} for file={}", contentType, original);
        }

        String monthDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Path targetDir = baseDir.resolve(monthDir);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new IllegalArgumentException("Không thể tạo thư mục lưu trữ: " + e.getMessage(), e);
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = targetDir.resolve(storedName);
        try {
            // Use REPLACE_EXISTING defensively in case of retry — should never
            // collide because of UUID, but safer.
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalArgumentException("Không thể lưu file minh chứng: " + e.getMessage(), e);
        }

        String publicUrl = "/api/inventory/evidence/" + monthDir + "/" + storedName;
        log.info("[EvidenceStorageService] Stored {} as {} ({} bytes)", original, target, file.getSize());

        return new StoredFile(original, storedName, publicUrl, file.getSize());
    }

    /**
     * Lookup absolute path for serving a previously stored file.
     * Returns null if {@code publicPath} does not live under baseDir (defensive
     * against path traversal in the URL).
     */
    public Path resolve(String publicPath) {
        if (publicPath == null) return null;
        // publicPath looks like "/api/inventory/evidence/yyyy-MM/<file>"
        // Strip the API prefix and resolve relative to baseDir.
        String suffix = publicPath;
        int idx = publicPath.indexOf("/inventory/evidence/");
        if (idx >= 0) {
            suffix = publicPath.substring(idx + "/inventory/evidence/".length());
        }
        Path candidate = baseDir.resolve(suffix).normalize();
        if (!candidate.startsWith(baseDir)) {
            log.warn("[EvidenceStorageService] Path traversal blocked for {}", publicPath);
            return null;
        }
        return Files.exists(candidate) ? candidate : null;
    }

    private static String extractExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot);
    }
}
