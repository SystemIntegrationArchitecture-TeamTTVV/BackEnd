package edu.iuh.fit.se.commonservice.controller;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import edu.iuh.fit.se.commonservice.service.CloudinaryStorageService;
import edu.iuh.fit.se.commonservice.service.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final S3StorageService s3StorageService;
    private final CloudinaryStorageService cloudinaryStorageService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @PostMapping
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "File is empty");
                return ResponseEntity.badRequest().body(error);
            }

            // Priority 1: AWS S3
            if (s3StorageService.isEnabled()) {
                String secureUrl = s3StorageService.upload(file, "ttvv/uploads");
                Map<String, Object> response = new HashMap<>();
                response.put("url", secureUrl);
                response.put("fileName", file.getOriginalFilename());
                response.put("fileSize", file.getSize());
                response.put("fileType", file.getContentType());
                response.put("path", secureUrl);
                log.info("File uploaded to S3: {}", file.getOriginalFilename());
                return ResponseEntity.ok(response);
            }

            // Priority 2: Cloudinary
            if (cloudinaryStorageService.isEnabled()) {
                String secureUrl = cloudinaryStorageService.upload(file, "ttvv/uploads");
                Map<String, Object> response = new HashMap<>();
                response.put("url", secureUrl);
                response.put("fileName", file.getOriginalFilename());
                response.put("fileSize", file.getSize());
                response.put("fileType", file.getContentType());
                response.put("path", secureUrl);
                log.info("File uploaded to Cloudinary: {}", file.getOriginalFilename());
                return ResponseEntity.ok(response);
            }

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + extension;

            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/api/common/api/files/" + uniqueFilename;

            Map<String, Object> response = new HashMap<>();
            response.put("url", fileUrl);
            response.put("fileName", originalFilename);
            response.put("fileSize", file.getSize());
            response.put("fileType", file.getContentType());
            response.put("path", fileUrl);

            log.info("File uploaded locally: {} -> {}", originalFilename, uniqueFilename);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error uploading file", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to upload file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("Error serving file: {}", filename, e);
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("Error determining content type for file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
