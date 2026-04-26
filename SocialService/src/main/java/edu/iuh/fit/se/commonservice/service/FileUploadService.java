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

    private final CloudinaryStorageService cloudinaryStorageService;

    private static final String STORY_DIR = "uploads/stories";

    public String uploadStoryFile(MultipartFile file) {
        if (cloudinaryStorageService.isEnabled()) {
            try {
                return cloudinaryStorageService.upload(file, "ttvv/stories");
            } catch (Exception e) {
                throw new RuntimeException("Upload file failed", e);
            }
        }

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
