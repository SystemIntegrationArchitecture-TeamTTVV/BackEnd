package edu.iuh.fit.se.commonservice.service;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CloudinaryStorageService {

    private final Cloudinary cloudinary;
    private final boolean enabled;

    public CloudinaryStorageService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        if (StringUtils.hasText(cloudName) && StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret)) {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName.trim(),
                    "api_key", apiKey.trim(),
                    "api_secret", apiSecret.trim()));
            this.enabled = true;
            log.info("Cloudinary storage enabled (cloud_name={})", cloudName);
        } else {
            this.cloudinary = null;
            this.enabled = false;
            log.info("Cloudinary storage disabled — set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Upload file; returns secure HTTPS URL (ảnh/video/file — resource_type auto).
     */
    @SuppressWarnings("unchecked")
    public String upload(MultipartFile file, String folder) throws IOException {
        if (!enabled || cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured");
        }
        try {
            Map<String, Object> options = ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto",
                    "unique_filename", true);
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), options);
            Object secure = result.get("secure_url");
            if (secure != null) {
                return secure.toString();
            }
            Object url = result.get("url");
            return url != null ? url.toString() : null;
        } catch (Exception e) {
            throw new IOException("Cloudinary upload failed: " + e.getMessage(), e);
        }
    }
}
