package edu.iuh.fit.se.commonservice.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileUploadService {

    private static final String STORY_DIR = "uploads/stories";

    public String uploadStoryFile(MultipartFile file) {
        try {
            File dir = new File(STORY_DIR);
            if (!dir.exists()) dir.mkdirs();

            String original = file.getOriginalFilename();
            String ext = original.substring(original.lastIndexOf("."));

            String fileName = UUID.randomUUID() + ext;
            Path path = Paths.get(STORY_DIR, fileName);

            Files.copy(
                    file.getInputStream(),
                    path,
                    StandardCopyOption.REPLACE_EXISTING
            );

            // URL trả về cho FE
            return "/uploads/stories/" + fileName;

        } catch (Exception e) {
            throw new RuntimeException("Upload file failed", e);
        }
    }
}
