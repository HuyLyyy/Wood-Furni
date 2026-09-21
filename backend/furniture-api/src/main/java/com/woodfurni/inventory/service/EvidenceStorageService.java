package com.woodfurni.inventory.service;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
 * resolves the file from the id and streams it back.
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

    /**
     * Result of resolving a GridFS file by id.
     * @param content     raw bytes of the file (loaded into memory).
     * @param originalName filename as originally uploaded by the user.
     */
    public record DownloadedFile(
            byte[] content,
            String originalName
    ) {}

    private final GridFsTemplate gridFsTemplate;
    private final MongoTemplate mongoTemplate;

    public EvidenceStorageService(GridFsTemplate gridFsTemplate, MongoTemplate mongoTemplate) {
        this.gridFsTemplate = gridFsTemplate;
        this.mongoTemplate = mongoTemplate;
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
                    new Document()
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
     * Resolve a GridFS file by id and stream its bytes into memory.
     * Returns null if the file is not found.
     *
     * @param publicPath  URL like "/api/v1/inventory/evidence/{gridFsId}"
     * @return DownloadedFile or null if not found.
     */
    public DownloadedFile resolve(String publicPath) {
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

        return downloadById(fileId);
    }

    /**
     * Low-level download using MongoDB GridFSBucket (direct driver API, bypassing
     * Spring Data's broken getResource overload in Spring Data MongoDB 3.2.x).
     */
    private DownloadedFile downloadById(ObjectId fileId) {
        try {
            // Find the GridFS file metadata first to get originalName.
            GridFSFile gridFsFile = gridFsTemplate.findOne(
                    new Query(Criteria.where("_id").is(fileId))
            );
            if (gridFsFile == null) {
                log.warn("[EvidenceStorageService] GridFS file not found for id={}", fileId);
                return null;
            }

            String originalName = null;
            Document meta = gridFsFile.getMetadata();
            if (meta != null && meta.getString("originalName") != null) {
                originalName = meta.getString("originalName");
            }
            if (originalName == null || originalName.isBlank()) {
                originalName = gridFsFile.getFilename(); // fallback
            }

            // Use GridFSBucket for streaming — this is the official MongoDB driver API,
            // unaffected by Spring Data MongoDB wrapper changes.
            String bucketName = gridFsTemplate.getBucketName();
            var db = mongoTemplate.getDb();
            GridFSBucket bucket = GridFSBuckets.create(db, bucketName);

            try (GridFSDownloadStream stream = bucket.openDownloadStream(fileId)) {
                long length = stream.getGridFSFile().getLength();
                if (length > MAX_BYTES) {
                    log.warn("[EvidenceStorageService] File too large ({} bytes) for id={}", length, fileId);
                    return null;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int read;
                while ((read = stream.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                }
                byte[] bytes = baos.toByteArray();
                log.info("[EvidenceStorageService] Downloaded id={} ({} bytes) as '{}'",
                        fileId, bytes.length, originalName);
                return new DownloadedFile(bytes, originalName);
            }
        } catch (Exception ex) {
            log.error("[EvidenceStorageService] Unexpected error downloading id={}: {}",
                    fileId, ex.getMessage(), ex);
            return null;
        }
    }

    private static String extractExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot);
    }
}
