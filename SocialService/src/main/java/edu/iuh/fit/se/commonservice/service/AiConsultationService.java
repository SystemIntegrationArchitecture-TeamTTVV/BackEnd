package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import edu.iuh.fit.se.commonservice.dto.AiConsultationSummaryDTO;
import edu.iuh.fit.se.commonservice.dto.StartCallRequestDTO;
import edu.iuh.fit.se.commonservice.dto.StartCallResponseDTO;
import edu.iuh.fit.se.commonservice.dto.UpdateLogResultDTO;
import edu.iuh.fit.se.commonservice.model.AiConsultationLog;
import edu.iuh.fit.se.commonservice.model.AiConsultationSettings;
import edu.iuh.fit.se.commonservice.repository.AiConsultationLogRepository;
import edu.iuh.fit.se.commonservice.repository.AiConsultationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiConsultationService {

    private final AiConsultationLogRepository logRepository;
    private final AiConsultationSettingsRepository settingsRepository;
    private final UserIdentityService userIdentityService;
    private final EmailService emailService;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${app.baseUrl:http://localhost:8081}")
    private String appBaseUrl;

    public List<AiConsultationLog> getLogs() {
        return logRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public AiConsultationSummaryDTO getSummary() {
        List<AiConsultationLog> logs = logRepository.findAll();
        long total = logs.size();
        
        Map<String, Long> counts = logs.stream()
                .filter(l -> l.getResult() != null)
                .collect(Collectors.groupingBy(AiConsultationLog::getResult, Collectors.counting()));
        
        return AiConsultationSummaryDTO.builder()
                .total(total)
                .pending(counts.getOrDefault("pending", 0L))
                .interested(counts.getOrDefault("interested", 0L))
                .registered(counts.getOrDefault("registered", 0L))
                .notInterested(counts.getOrDefault("not_interested", 0L))
                .callback(counts.getOrDefault("callback", 0L))
                .build();
    }

    public AiConsultationSettings getSettings() {
        List<AiConsultationSettings> settingsList = settingsRepository.findAll();
        if (settingsList.isEmpty()) {
            AiConsultationSettings settings = new AiConsultationSettings();
            settings.setId("default-settings");
            settings.setPackages("");
            settings.setInstructions("");
            settings.setWsUrl("");
            settings.setGroqApiKey("");
            settings.setTwilioAccountSid("");
            settings.setTwilioAuthToken("");
            settings.setTwilioPhoneNumber("");
            return settingsRepository.save(settings);
        }
        return settingsList.get(0);
    }

    public AiConsultationSettings saveSettings(AiConsultationSettings newSettings) {
        AiConsultationSettings current = getSettings();
        current.setPackages(newSettings.getPackages());
        current.setInstructions(newSettings.getInstructions());
        current.setWsUrl(newSettings.getWsUrl());
        current.setGroqApiKey(newSettings.getGroqApiKey());
        current.setTwilioAccountSid(newSettings.getTwilioAccountSid());
        current.setTwilioAuthToken(newSettings.getTwilioAuthToken());
        current.setTwilioPhoneNumber(newSettings.getTwilioPhoneNumber());
        return settingsRepository.save(current);
    }

    public AiConsultationLog updateLogResult(String id, UpdateLogResultDTO dto) {
        AiConsultationLog logEntry = logRepository.findById(id).orElseThrow(() -> new RuntimeException("Log not found"));
        logEntry.setStatus("completed");
        logEntry.setResult(dto.getResult());
        if (dto.getNotes() != null) logEntry.setNotes(dto.getNotes());
        if (dto.getRecommendedPackage() != null) logEntry.setRecommendedPackage(dto.getRecommendedPackage());
        if (dto.getDuration() != null) logEntry.setDuration(dto.getDuration());
        logEntry.setUpdatedAt(LocalDateTime.now());
        
        // Gửi email nếu trạng thái là "registered"
        if ("registered".equals(dto.getResult()) && logEntry.getUserId() != null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                userIdentityService.findById(logEntry.getUserId()).ifPresent(user -> {
                    if (user.getEmail() != null) {
                        emailService.sendLivestreamQuotationEmail(
                            user.getEmail(), 
                            user.getFullName(), 
                            logEntry.getRecommendedPackage(),
                            user.getId()
                        );
                    }
                });
            });
        }
        
        return logRepository.save(logEntry);
    }

    public AiConsultationLog createAndCompleteWebLog(Map<String, Object> data) {
        try {
            String userId = (String) data.get("userId");
            String result = (String) data.get("result");
            String notes = (String) data.get("notes");
            String recommendedPackage = (String) data.get("recommendedPackage");
            
            Integer duration = null;
            if (data.get("duration") != null) {
                duration = ((Number) data.get("duration")).intValue();
            }

            // Tìm thông tin User
            String userName = "Khách hàng Web";
            String userAvatar = null;
            String phoneNumber = null;
            if (userId != null && !userId.isBlank()) {
                var userOpt = userIdentityService.findById(userId);
                if (userOpt.isPresent()) {
                    userName = userOpt.get().getFullName() != null ? userOpt.get().getFullName() : userOpt.get().getUsername();
                    userAvatar = userOpt.get().getAvatar();
                    phoneNumber = userOpt.get().getPhoneNumber();
                }
            }

            // Tìm xem có log email_sent nào của user này để cập nhật đè lên cho đồng bộ
            AiConsultationLog logEntry = null;
            if (userId != null && !userId.isBlank()) {
                List<AiConsultationLog> existing = logRepository.findAll();
                for (AiConsultationLog l : existing) {
                    if (userId.equals(l.getUserId()) && "email_sent".equals(l.getStatus())) {
                        logEntry = l;
                        break;
                    }
                }
            }

            if (logEntry == null) {
                logEntry = new AiConsultationLog();
                logEntry.setUserId(userId);
                logEntry.setUserName(userName);
                logEntry.setUserAvatar(userAvatar);
                logEntry.setPhoneNumber(phoneNumber);
                logEntry.setCreatedAt(LocalDateTime.now());
            }

            logEntry.setStatus("completed");
            logEntry.setResult(result);
            logEntry.setNotes(notes);
            logEntry.setRecommendedPackage(recommendedPackage);
            logEntry.setDuration(duration);
            logEntry.setUpdatedAt(LocalDateTime.now());

            // Gửi email nếu trạng thái là "registered"
            if ("registered".equals(result) && userId != null) {
                final String finalPkg = recommendedPackage;
                final String finalUser = userId;
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        userIdentityService.findById(finalUser).ifPresent(user -> {
                            if (user.getEmail() != null) {
                                emailService.sendLivestreamQuotationEmail(
                                    user.getEmail(), 
                                    user.getFullName(), 
                                    finalPkg,
                                    user.getId()
                                );
                            }
                        });
                    } catch (Exception ex) {
                        log.error("❌ Lỗi trong tiến trình gửi email báo giá async: ", ex);
                    }
                });
            }

            return logRepository.save(logEntry);
        } catch (Exception e) {
            log.error("❌ Lỗi xử lý createAndCompleteWebLog: ", e);
            throw new RuntimeException("Lỗi xử lý lưu cuộc gọi: " + e.getMessage(), e);
        }
    }

    public StartCallResponseDTO startCall(StartCallRequestDTO request) {
        try {
            // Retrieve User Info
            String userName = "Khách hàng";
            String userAvatar = null;
            if (request.getUserId() != null) {
                var userOpt = userIdentityService.findById(request.getUserId());
                if (userOpt.isPresent()) {
                    userName = userOpt.get().getFullName() != null ? userOpt.get().getFullName() : userOpt.get().getUsername();
                    userAvatar = userOpt.get().getAvatar();
                }
            }

            AiConsultationSettings settings = getSettings();

            // Create Log Entry First
            AiConsultationLog logEntry = new AiConsultationLog();
            logEntry.setUserId(request.getUserId());
            logEntry.setUserName(userName);
            logEntry.setUserAvatar(userAvatar);
            logEntry.setPhoneNumber(request.getPhoneNumber());
            logEntry.setStatus("calling");
            logEntry.setResult("pending");
            logEntry = logRepository.save(logEntry);

            if (settings.getTwilioAccountSid() == null || settings.getTwilioAccountSid().isBlank()) {
                throw new RuntimeException("Chưa cấu hình Twilio Account SID");
            }

            String twilioAccountSid = settings.getTwilioAccountSid();
            String twilioAuthToken = settings.getTwilioAuthToken();
            String twilioPhoneNumber = settings.getTwilioPhoneNumber();

            String twilioApiUrl = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Calls.json";
            
            // TwiML URL that Twilio will fetch when the call connects.
            String twimlContent = generateTwiML(logEntry.getId());
            // URL encode the TwiML content
            String encodedTwiml = java.net.URLEncoder.encode(twimlContent, java.nio.charset.StandardCharsets.UTF_8.toString());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(twilioAccountSid, twilioAuthToken);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            // Format phone number to E.164 if it's a Vietnamese local number
            String targetPhone = request.getPhoneNumber().trim();
            if (targetPhone.startsWith("0")) {
                targetPhone = "+84" + targetPhone.substring(1);
            }
            
            String requestBody = "To=" + java.net.URLEncoder.encode(targetPhone, java.nio.charset.StandardCharsets.UTF_8.toString()) +
                                 "&From=" + java.net.URLEncoder.encode(twilioPhoneNumber, java.nio.charset.StandardCharsets.UTF_8.toString()) +
                                 "&Twiml=" + encodedTwiml;
                                 
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            // Execute actual Twilio call
            ResponseEntity<String> response = restTemplate.postForEntity(twilioApiUrl, entity, String.class);
            log.info("Twilio response: {}", response.getBody());

            // Assuming call initiated successfully
            logEntry.setCallId(UUID.randomUUID().toString()); // Mock Call SID
            logRepository.save(logEntry);

            return new StartCallResponseDTO(true, logEntry.getCallId(), "Đang kết nối AI tư vấn...");
        } catch (Exception e) {
            log.error("Failed to start AI call", e);
            return new StartCallResponseDTO(false, null, "Lỗi kết nối: " + e.getMessage());
        }
    }

    public Map<String, Object> sendEmailLink(Map<String, String> request) {
        String userId = request.get("userId");
        if (userId == null || userId.isBlank()) {
            return Map.of("success", false, "message", "UserId không được để trống!");
        }

        var userOpt = userIdentityService.findById(userId);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "message", "Không tìm thấy người dùng!");
        }

        var user = userOpt.get();
        String toEmail = user.getEmail();
        if (toEmail == null || toEmail.isBlank()) {
            return Map.of("success", false, "message", "Người dùng chưa cấu hình email!");
        }

        String userName = user.getFullName() != null ? user.getFullName() : user.getUsername();
        AiConsultationSettings settings = getSettings();

        try {
            String clientBaseUrl = emailService.getBaseUrl();
            if (clientBaseUrl == null || clientBaseUrl.isBlank() || clientBaseUrl.contains("websitedev.software")) {
                clientBaseUrl = "http://localhost:5173";
            } else if (clientBaseUrl.endsWith("/")) {
                clientBaseUrl = clientBaseUrl.substring(0, clientBaseUrl.length() - 1);
            }

            String encodedName = java.net.URLEncoder.encode(userName, java.nio.charset.StandardCharsets.UTF_8.toString());
            String encodedPackages = java.net.URLEncoder.encode(settings.getPackages() != null ? settings.getPackages() : "", java.nio.charset.StandardCharsets.UTF_8.toString());
            String encodedInstructions = java.net.URLEncoder.encode(settings.getInstructions() != null ? settings.getInstructions() : "", java.nio.charset.StandardCharsets.UTF_8.toString());

            String webClientUrl = String.format("%s/web_client.html?userId=%s&name=%s&package=livestream&packages=%s&instructions=%s",
                    clientBaseUrl, userId, encodedName, encodedPackages, encodedInstructions);

            emailService.sendConsultationInvitationEmail(toEmail, userName, webClientUrl);

            // Log entry for the sent invitation
            AiConsultationLog logEntry = new AiConsultationLog();
            logEntry.setUserId(userId);
            logEntry.setUserName(userName);
            logEntry.setUserAvatar(user.getAvatar());
            logEntry.setPhoneNumber(user.getPhoneNumber());
            logEntry.setStatus("email_sent");
            logEntry.setResult("pending");
            logEntry.setCreatedAt(LocalDateTime.now());
            logEntry.setUpdatedAt(LocalDateTime.now());
            logRepository.save(logEntry);

            return Map.of("success", true, "message", "Gửi email lời mời tư vấn thành công!");
        } catch (Exception e) {
            log.error("Failed to send consultation email link", e);
            return Map.of("success", false, "message", "Lỗi gửi email: " + e.getMessage());
        }
    }

    public String generateTwiML(String logId) {
        AiConsultationSettings settings = getSettings();
        String wsUrl = settings.getWsUrl();
        if (wsUrl == null || wsUrl.isBlank()) {
            wsUrl = "wss://fallback-url.ngrok-free.dev/ws";
        }
        
        // Pass logId to WebSocket so it knows which session to update
        String streamUrl = wsUrl;
        
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<Response>\n" +
               "  <Connect>\n" +
               "    <Stream url=\"" + streamUrl + "\">\n" +
               "      <Parameter name=\"logId\" value=\"" + logId + "\" />\n" +
               "    </Stream>\n" +
               "  </Connect>\n" +
               "</Response>";
    }
}
