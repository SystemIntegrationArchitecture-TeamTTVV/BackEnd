package edu.iuh.fit.se.commonservice.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final S3StorageService s3StorageService;
    private final CloudinaryStorageService cloudinaryStorageService;

    private static final String STORY_DIR = "uploads/stories";
    private static final String VIDEO_DIR = "uploads/videos";

    public String uploadVideoFile(MultipartFile file) {
        // Priority 1: AWS S3
        if (s3StorageService.isEnabled()) {
            try {
                return s3StorageService.upload(file, "ttvv/videos");
            } catch (Exception e) {
                throw new RuntimeException("S3 upload failed", e);
            }
        }

        // Priority 2: Cloudinary
        if (cloudinaryStorageService.isEnabled()) {
            try {
                return cloudinaryStorageService.upload(file, "ttvv/videos");
            } catch (Exception e) {
                throw new RuntimeException("Cloudinary upload failed", e);
            }
        }

        // Priority 3: Local filesystem
        try {
            File dir = new File(VIDEO_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String original = file.getOriginalFilename();
            String ext = original != null && original.contains(".")
                    ? original.substring(original.lastIndexOf("."))
                    : ".mp4";

            String fileName = UUID.randomUUID() + ext;
            Path path = Paths.get(VIDEO_DIR, fileName);
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/videos/" + fileName;
        } catch (Exception e) {
            throw new RuntimeException("Upload video file failed", e);
        }
    }

    public String uploadStoryFile(MultipartFile file) {
        // Priority 1: AWS S3
        if (s3StorageService.isEnabled()) {
            try {
                return s3StorageService.upload(file, "ttvv/stories");
            } catch (Exception e) {
                throw new RuntimeException("S3 upload failed", e);
            }
        }

        // Priority 2: Cloudinary
        if (cloudinaryStorageService.isEnabled()) {
            try {
                return cloudinaryStorageService.upload(file, "ttvv/stories");
            } catch (Exception e) {
                throw new RuntimeException("Cloudinary upload failed", e);
            }
        }

        // Priority 3: Local filesystem
        try {
            File dir = new File(STORY_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String original = file.getOriginalFilename();
            String ext = original != null && original.contains(".")
                    ? original.substring(original.lastIndexOf("."))
                    : "";

            String fileName = UUID.randomUUID() + ext;
            Path path = Paths.get(STORY_DIR, fileName);

            Files.copy(
                    file.getInputStream(),
                    path,
                    StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/stories/" + fileName;

        } catch (Exception e) {
            throw new RuntimeException("Upload file failed", e);
        }
    }
}
