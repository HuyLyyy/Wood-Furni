package com.woodfurni.storage.controller;

import com.woodfurni.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles file uploads for product images and other static assets.
 * Files are stored locally on the server filesystem (not in MongoDB).
 *
 * Configuration:
 *   storage.upload-dir    — absolute path on the host machine (default: /app/uploads)
 *   storage.max-file-mb  — max file size in MB per request (default: 5)
 */
@Slf4j
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
@Tag(name = "Storage", description = "File upload and retrieval")
public class StorageController {

    @Value("${storage.upload-dir:#{T(java.lang.System).getProperty(\"user.dir\")}/uploads}")
    private String uploadDir;

    @Value("${storage.max-file-mb:5}")
    private int maxFileMb;

    private Path uploadPath;

    @PostConstruct
    void init() throws IOException {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        log.info("[StorageController] Upload directory: {} (max file size: {} MB)", uploadPath, maxFileMb);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload one or more image files",
               description = "Accepts JPEG, PNG, GIF, WEBP. Returns a list of accessible URLs.")
    public ResponseEntity<ApiResponse<List<String>>> uploadImages(
            @RequestParam("files") MultipartFile[] files) {

        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Không có file nào được gửi lên"));
        }

        List<String> urls = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        long maxBytes = (long) maxFileMb * 1024 * 1024;

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                errors.add("File rỗng: " + file.getOriginalFilename());
                continue;
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                errors.add("Không phải file ảnh: " + file.getOriginalFilename());
                continue;
            }

            if (file.getSize() > maxBytes) {
                errors.add("File quá lớn (> " + maxFileMb + " MB): " + file.getOriginalFilename());
                continue;
            }

            try {
                String originalName = file.getOriginalFilename();
                String ext = "";
                if (originalName != null && originalName.contains(".")) {
                    ext = originalName.substring(originalName.lastIndexOf('.'));
                }
                // Normalise extension to lowercase
                ext = ext.isEmpty() ? ".jpg" : ext.toLowerCase();

                String savedName = UUID.randomUUID() + ext;
                Path target = uploadPath.resolve(savedName);

                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

                // URL path accessible through the gateway proxy.
                // gateway-nginx.conf proxies /uploads/ → backend:8080/api/v1/storage/uploads/
                String url = "/uploads/" + savedName;
                urls.add(url);
                log.info("[StorageController] Saved: {} ({} bytes) → {}", savedName, file.getSize(), url);

            } catch (IOException e) {
                log.error("[StorageController] Failed to save {}: {}", file.getOriginalFilename(), e.getMessage());
                errors.add("Lỗi ghi file: " + file.getOriginalFilename());
            }
        }

        if (urls.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Không thể lưu file: " + String.join("; ", errors)));
        }

        String msg = errors.isEmpty()
                ? "Tải lên thành công " + urls.size() + " file"
                : "Tải lên " + urls.size() + " file thành công, " + errors.size() + " file thất bại: " + String.join("; ", errors);

        return ResponseEntity.ok(ApiResponse.success(msg, urls));
    }

    @GetMapping("/uploads/**")
    @Operation(summary = "Serve uploaded files",
               description = "Direct file access — used by <img src> tags after upload.")
    public ResponseEntity<byte[]> serveFile(HttpServletRequest request) throws IOException {
        String uri = request.getRequestURI(); // e.g. /api/v1/storage/uploads/abc.jpg
        String filename = uri.substring(uri.lastIndexOf("/uploads/") + "/uploads/".length());
        if (filename.isEmpty() || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        Path file = uploadPath.resolve(filename).normalize();
        if (!file.startsWith(uploadPath)) {
            return ResponseEntity.notFound().build();
        }
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(file);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        byte[] data = Files.readAllBytes(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(data);
    }
}
