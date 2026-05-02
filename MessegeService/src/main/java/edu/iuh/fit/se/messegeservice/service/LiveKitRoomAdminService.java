package edu.iuh.fit.se.messegeservice.service;

import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import retrofit2.Response;

import java.util.Collections;
import java.util.List;

/**
 * LiveKit Server API — duyệt quyền participant, kick, xóa phòng (ThamKhao parity).
 */
@Service
@Slf4j
public class LiveKitRoomAdminService {

    private final RoomServiceClient roomClient;
    private final boolean enabled;

    public LiveKitRoomAdminService(
            @Value("${livekit.api.key:}") String apiKey,
            @Value("${livekit.api.secret:}") String apiSecret,
            @Value("${livekit.url:wss://livestream-demo.livekit.cloud}") String livekitUrl
    ) {
        RoomServiceClient client = null;
        boolean ok = false;
        try {
            if (StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret) && StringUtils.hasText(livekitUrl)) {
                String httpUrl = livekitUrl
                        .replace("wss://", "https://")
                        .replace("ws://", "http://");
                client = RoomServiceClient.createClient(httpUrl, apiKey, apiSecret);
                ok = true;
                log.info("LiveKit RoomServiceClient initialised (url={})", httpUrl);
            }
        } catch (Exception e) {
            log.warn("LiveKit RoomServiceClient disabled: {}", e.getMessage());
        }
        this.roomClient = client;
        this.enabled = ok && client != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void deleteRoom(String roomName) {
        if (!enabled || roomName == null || roomName.isBlank()) return;
        try {
            Response<Void> r = roomClient.deleteRoom(roomName).execute();
            if (!r.isSuccessful()) {
                log.warn("deleteRoom {} failed: {}", roomName, r.code());
            }
        } catch (Exception e) {
            log.warn("deleteRoom {} error: {}", roomName, e.getMessage());
        }
    }

    public void removeParticipant(String roomName, String identity) {
        if (!enabled || roomName == null || identity == null) return;
        try {
            Response<Void> r = roomClient.removeParticipant(roomName, identity).execute();
            if (!r.isSuccessful()) {
                log.warn("removeParticipant {}/{} failed: {}", roomName, identity, r.code());
            }
        } catch (Exception e) {
            log.warn("removeParticipant {}/{} error: {}", roomName, identity, e.getMessage());
        }
    }

    /**
     * Cấp quyền subscribe + metadata approved (giống ThamKhao approveParticipant).
     */
    public void approveParticipantSubscribe(String roomName, String identity) {
        if (!enabled || roomName == null || identity == null) return;
        try {
            String metadata = "{\"isHost\":false,\"status\":\"approved\"}";
            LivekitModels.ParticipantPermission perm = LivekitModels.ParticipantPermission.newBuilder()
                    .setCanSubscribe(true)
                    .setCanPublish(false)
                    .setCanPublishData(true)
                    .build();
            Response<LivekitModels.ParticipantInfo> r =
                    roomClient.updateParticipant(roomName, identity, "", metadata, perm, null).execute();
            if (!r.isSuccessful()) {
                log.warn("updateParticipant approve {}/{} failed: {}", roomName, identity, r.code());
            }
        } catch (Exception e) {
            log.warn("approveParticipantSubscribe {}/{} error: {}", roomName, identity, e.getMessage());
        }
    }

    public List<LivekitModels.ParticipantInfo> listParticipants(String roomName) {
        if (!enabled || roomName == null || roomName.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Response<List<LivekitModels.ParticipantInfo>> r = roomClient.listParticipants(roomName).execute();
            if (r.isSuccessful() && r.body() != null) {
                return r.body();
            }
        } catch (Exception e) {
            log.warn("listParticipants {} error: {}", roomName, e.getMessage());
        }
        return Collections.emptyList();
    }
}
