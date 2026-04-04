package edu.iuh.fit.se.commonservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/**
 * OpenFeign client for calling MessegeService from CommonService.
 */
@FeignClient(
        name = "MessegeService"
)
public interface MessageServiceClient {

    @GetMapping("/conversations/user/{userId}")
    List<Map<String, Object>> getConversationsByUserId(@PathVariable("userId") String userId);

    @GetMapping("/conversations/{id}")
    Map<String, Object> getConversationById(@PathVariable("id") String id);

    @GetMapping("/messages/conversation/{conversationId}")
    List<Map<String, Object>> getMessagesByConversationId(@PathVariable("conversationId") String conversationId);
}

