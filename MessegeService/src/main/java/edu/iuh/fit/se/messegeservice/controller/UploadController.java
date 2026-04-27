package edu.iuh.fit.se.messegeservice.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import edu.iuh.fit.se.messegeservice.service.CloudinaryStorageService;
import edu.iuh.fit.se.messegeservice.service.S3StorageService;
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

            if (s3StorageService.isEnabled()) {
                String secureUrl = s3StorageService.upload(file, "ttvv/message-uploads");
                Map<String, Object> response = new HashMap<>();
                response.put("url", secureUrl);
                response.put("fileName", file.getOriginalFilename());
                response.put("fileSize", file.getSize());
                response.put("fileType", file.getContentType());
                response.put("path", secureUrl);
                log.info("File uploaded to S3: {}", file.getOriginalFilename());
                return ResponseEntity.ok(response);
            }

            if (cloudinaryStorageService.isEnabled()) {
                String secureUrl = cloudinaryStorageService.upload(file, "ttvv/message-uploads");
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

            String fileUrl = "/api/message/api/files/" + uniqueFilename;

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
}
