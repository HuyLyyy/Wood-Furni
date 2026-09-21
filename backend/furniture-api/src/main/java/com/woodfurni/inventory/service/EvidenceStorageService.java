package com.woodfurni.inventory.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Saves inventory-adjustment evidence files to MongoDB GridFS.
 *
 * Why GridFS instead of local disk?
 *   - Render free plan has NO persistent disk → /tmp is wiped on every redeploy.
 *   - MongoDB Atlas (also free) gives us 512MB and survives deploys forever.
 *   - File lives next to the history record that references it; no orphan risk.
 *
 * Files are stored with metadata = { monthDir: "yyyy-MM", originalName, storedName }.
 * The public URL looks like /api/v1/inventory/evidence/{gridFsId} — the controller
 * resolves the GridFsResource from the id and streams it back.
 *
 * Validation rules (unchanged from local-disk version):
 *   - Only .xlsx / .xls accepted (extension check).
 *   - Max 10 MB.
 *   - Suspicious Content-Types logged but not rejected.
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

    public record StoredFileWithId(
            String gridFsId,
            StoredFile storedFile
    ) {}

    private final GridFsTemplate gridFsTemplate;

    public EvidenceStorageService(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
        log.info("[EvidenceStorageService] Initialised — using MongoDB GridFS for evidence storage");
    }

    /**
     * Validate and persist the uploaded file to GridFS.
     *
     * @throws IllegalArgumentException with a user-friendly message on any
     *         validation or I/O failure.
     */
    public StoredFileWithId save(MultipartFile file) {
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

        String monthDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;

        ObjectId fileId;
        try {
            fileId = gridFsTemplate.store(
                    file.getInputStream(),
                    storedName,
                    ct != null ? ct : "application/octet-stream",
                    new org.bson.Document()
                            .append("monthDir", monthDir)
                            .append("originalName", original)
                            .append("storedName", storedName)
            );
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Không thể lưu file minh chứng '" + original + "': " + e.getMessage(), e);
        }

        // Public URL = context-path + /inventory/evidence/{gridFsId}
        // Frontend never has to guess — id is opaque so a future move to S3/Cloudinary
        // can keep the same URL shape with a different resolver.
        String publicUrl = "/api/v1/inventory/evidence/" + fileId.toHexString();
        log.info("[EvidenceStorageService] Saved '{}' → GridFS id={} ({} bytes) as {}",
                original, fileId.toHexString(), file.getSize(), storedName);

        StoredFile sf = new StoredFile(original, storedName, publicUrl, file.getSize());
        return new StoredFileWithId(fileId.toHexString(), sf);
    }

    /**
     * Resolve a public URL back to a GridFsResource so the controller can stream it.
     *
     * @param publicPath  URL like "/api/v1/inventory/evidence/{gridFsId}"
     * @return GridFsResource or null if not found / URL malformed.
     */
    public GridFsResource resolve(String publicPath) {
        if (publicPath == null) return null;

        int idx = publicPath.indexOf("/inventory/evidence/");
        if (idx < 0) {
            log.warn("[EvidenceStorageService] Unrecognised evidence publicPath format: {}", publicPath);
            return null;
        }
        String idStr = publicPath.substring(idx + "/inventory/evidence/".length());
        if (idStr.isBlank()) return null;

        ObjectId fileId;
        try {
            fileId = new ObjectId(idStr);
        } catch (IllegalArgumentException ex) {
            log.warn("[EvidenceStorageService] Evidence URL has invalid GridFS id: {}", idStr);
            return null;
        }

        try {
            GridFSFile file = gridFsTemplate.findOne(
                    new org.springframework.data.mongodb.core.query.Query(
                            org.springframework.data.mongodb.core.query.Criteria.where("_id").is(fileId)
                    )
            );
            if (file == null) {
                log.warn("[EvidenceStorageService] Evidence file not found in GridFS for id={}", idStr);
                return null;
            }

            // Get stored name from GridFS metadata (files are stored with UUID names,
            // e.g. "a1b2c3d4e5f6.xlsx"). getResource(filename) is reliable; the
            // GridFSFile-based overload is broken / removed in Spring Data MongoDB 4.x.
            org.bson.Document meta = file.getMetadata();
            String storedName = (meta != null && meta.getString("storedName") != null)
                    ? meta.getString("storedName")
                    : file.getFilename(); // fallback

            GridFsResource resource = gridFsTemplate.getResource(storedName);
            if (!resource.exists()) {
                log.warn("[EvidenceStorageService] GridFS resource does not exist for id={}", idStr);
                return null;
            }
            return resource;
        } catch (Exception ex) {
            log.error("[EvidenceStorageService] Unexpected error resolving GridFS id={}: {}",
                    idStr, ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Look up the original (user-friendly) filename stored in GridFS metadata
     * for a given gridFsId. Falls back to null if not found so caller can use
     * the URL filename as a default.
     */
    public String findOriginalNameById(String gridFsId) {
        if (gridFsId == null || gridFsId.isBlank()) return null;
        ObjectId fileId;
        try {
            fileId = new ObjectId(gridFsId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        GridFSFile file = gridFsTemplate.findOne(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").is(fileId)
                )
        );
        if (file == null) return null;
        org.bson.Document meta = file.getMetadata();
        if (meta == null) return null;
        Object name = meta.get("originalName");
        return name instanceof String s && !s.isBlank() ? s : null;
    }

    private static String extractExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot);
    }
}
