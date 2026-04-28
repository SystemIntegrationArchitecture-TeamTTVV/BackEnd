package edu.iuh.fit.se.commonservice.service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final boolean enabled;
    private final String bucketName;
    private final String region;

    public S3StorageService(
            @Value("${aws.s3.bucket-name:demo-thach-deptrai}") String bucketName,
            @Value("${aws.s3.region:ap-southeast-1}") String region,
            @Value("${aws.s3.access-key:}") String accessKey,
            @Value("${aws.s3.secret-key:}") String secretKey,
            @Value("${aws.s3.enabled:true}") boolean configuredEnabled) {
        this.bucketName = bucketName == null ? "" : bucketName.trim();
        this.region = region == null ? "ap-southeast-1" : region.trim();

        S3Client builtClient = null;
        boolean storageEnabled = false;

        if (!configuredEnabled || !StringUtils.hasText(this.bucketName)) {
            log.info("S3 storage disabled");
        } else {
            try {
                S3ClientBuilder builder = S3Client.builder()
                    .region(Region.of(this.region))
                    .httpClientBuilder(UrlConnectionHttpClient.builder());
                if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
                    builder.credentialsProvider(
                            StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())
                            )
                    );
                    log.info("S3 storage enabled with static credentials (bucket={}, region={})", this.bucketName, this.region);
                } else {
                    builder.credentialsProvider(DefaultCredentialsProvider.create());
                    log.info("S3 storage enabled with default AWS credentials chain (bucket={}, region={})", this.bucketName, this.region);
                }
                builtClient = builder.build();
                storageEnabled = true;
            } catch (Exception e) {
                log.warn("S3 storage initialization failed, disabled: {}", e.getMessage());
            }
        }

        this.s3Client = builtClient;
        this.enabled = storageEnabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Upload file to S3; returns the public HTTPS URL.
     * Folder structure: {folder}/{year}/{month}/{day}/{uuid}.{ext}
     */
    public String upload(MultipartFile file, String folder) throws IOException {
        if (!enabled || s3Client == null) {
            throw new IllegalStateException("S3 is not configured");
        }

        String originalName = file.getOriginalFilename();
        String extension = "";
        if (StringUtils.hasText(originalName) && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.'));
        }

        LocalDate now = LocalDate.now();
        String safeFolder = (folder == null || folder.isBlank()) ? "ttvv/uploads" : folder.trim();
        String key = String.format(
                "%s/%d/%02d/%02d/%s%s",
                safeFolder,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                UUID.randomUUID(),
                extension
        );

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

            String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, encodedKey);
        } catch (Exception e) {
            throw new IOException("S3 upload failed: " + e.getMessage(), e);
        }
    }
}
