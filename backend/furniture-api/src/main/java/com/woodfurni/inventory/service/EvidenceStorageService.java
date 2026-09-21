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
 * Saves inventory-adjustment evidence files to local disk.
 *
 * File layout on disk:
 * <pre>
 * {baseDir}/
 *   {yyyy-MM}/
 *     {uuid}.xlsx
 *     {uuid}.xls
 * </pre>
 *
 * ── Configuration ──────────────────────────────────────────────────────────
 *
 * Option A — Dedicated persistent disk (recommended for production):
 *   Mount a persistent volume at any path, e.g. "/var/data/woodfurni/uploads"
 *   and set:
 *     EVIDENCE_STORAGE_DIR=/var/data/woodfurni/uploads
 *
 *   Make sure the path EXISTS and is writable BEFORE the app starts
 *   (Render's persistent disk mounts at the specified path automatically;
 *    create the folder via the Render dashboard or a one-off shell command).
 *
 * Option B — Render ephemeral disk (dev / preview environments):
 *   Leave EVIDENCE_STORAGE_DIR unset.
 *   Files go to /tmp/woodfurni/uploads/ (survives single deploy but lost on restart).
 *
 * ⚠️  NEVER set EVIDENCE_STORAGE_DIR to a path that requires creating parent
 *     directories (e.g. /var/lib/… or /home/… unless you are sure the
 *     container runs as root and those parents exist). Always pre-create
 *     the target directory through the hosting dashboard before deploying.
 *
 * ── Security ──────────────────────────────────────────────────────────────
 *
 * Only .xlsx / .xls are accepted (validated by extension AND content-type).
 * Max file size: 10 MB.
 * Path traversal is blocked: the resolved path is normalised and checked
 * against baseDir before any file operation.
 */
@Slf4j
@Service
public class EvidenceStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/octet-stream"
    );

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".xlsx", ".xls");
    private static final long MAX_BYTES = 10L * 1024L * 1024L; // 10 MB

    public record StoredFile(
            String originalName,
            String storedFileName,
            String publicUrl,
            long size
    ) {}

    private final Path baseDir;

    public EvidenceStorageService() {
        this.baseDir = resolveBaseDir();
        String envValue = System.getenv("EVIDENCE_STORAGE_DIR");
        log.info("[EvidenceStorageService] baseDir={}  EVIDENCE_STORAGE_DIR={}",
                baseDir.toAbsolutePath(), envValue != null ? envValue : "(not set — using tmpdir fallback)");
    }

    /**
     * Resolve the storage root directory.
     *
     * Uses EVIDENCE_STORAGE_DIR directly if set.
     * Falls back to {java.io.tmpdir}/woodfurni/uploads (parent dirs created
     * automatically by save() on first write).
     *
     * ⚠️  The path is NOT created here. Creation happens lazily in save() so
     *     that startup never crashes — a missing directory just means nobody
     *     has uploaded a file yet.
     */
    private Path resolveBaseDir() {
        String env = System.getenv("EVIDENCE_STORAGE_DIR");
        if (env != null && !env.isBlank()) {
            return Paths.get(env.trim());
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "woodfurni", "uploads");
    }

    /**
     * Validate and persist the uploaded file.
     *
     * @throws IllegalArgumentException with a user-friendly message on any
     *         validation or I/O failure (caught by the controller → 400 response).
     */
    public StoredFile save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File minh chứng là bắt buộc");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File vượt quá dung lượng cho phép (10MB)");
        }

        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) original = "minh-chung.xlsx";

        String ext = extractExtension(original).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException(
                    "Chỉ chấp nhận file Excel (.xlsx, .xls). File gửi lên: " + original);
        }

        // Warn but do not reject on unexpected Content-Type (some browsers lie).
        String ct = file.getContentType();
        if (ct != null && !ALLOWED_CONTENT_TYPES.contains(ct)
                && !"application/octet-stream".equals(ct)) {
            log.warn("[EvidenceStorageService] Suspicious Content-Type '{}' for file '{}' — proceeding anyway",
                    ct, original);
        }

        // Subdirectory: /yyyy-MM/  (e.g. /2026-09/)
        String monthDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Path targetDir = baseDir.resolve(monthDir);

        // Try to create the directory. If it fails, surface a clear error.
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            String hint = System.getenv("EVIDENCE_STORAGE_DIR") != null
                    ? " Kiểm tra EVIDENCE_STORAGE_DIR đã được mount đúng chưa và thư mục có quyền ghi."
                    : "";
            throw new IllegalArgumentException(
                    "Không thể tạo thư mục lưu trữ '" + targetDir + "'." + hint
                    + " Lỗi: " + e.getMessage(), e);
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = targetDir.resolve(storedName);

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Không thể lưu file minh chứng '" + original + "': " + e.getMessage(), e);
        }

        String publicUrl = "/api/inventory/evidence/" + monthDir + "/" + storedName;
        log.info("[EvidenceStorageService] Saved '{}' → {} ({} bytes) as {}",
                original, target, file.getSize(), storedName);

        return new StoredFile(original, storedName, publicUrl, file.getSize());
    }

    /**
     * Resolve a public URL back to an absolute Path, or null if the file
     * does not exist or the URL tries to escape baseDir.
     */
    public Path resolve(String publicPath) {
        if (publicPath == null) return null;

        // Strip the API prefix to get the relative path within baseDir.
        int idx = publicPath.indexOf("/inventory/evidence/");
        if (idx < 0) {
            log.warn("[EvidenceStorageService] Unrecognised evidence publicPath format: {}", publicPath);
            return null;
        }
        String suffix = publicPath.substring(idx + "/inventory/evidence/".length());
        Path candidate = baseDir.resolve(suffix).normalize();

        // Defend against path traversal: ensure the resolved path is still
        // under baseDir after normalisation.
        if (!candidate.startsWith(baseDir)) {
            log.warn("[EvidenceStorageService] Path traversal attempt blocked: {}", publicPath);
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
