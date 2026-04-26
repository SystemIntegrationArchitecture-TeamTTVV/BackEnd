package edu.iuh.fit.se.commonservice.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * OpenFeign client for calling MessegeService from CommonService.
 */
@FeignClient(
        name = "messageService",
        url = "${message.service.base-url:http://localhost:8082}"
)
public interface MessageServiceClient {

    @GetMapping("/conversations/user/{userId}")
    List<Map<String, Object>> getConversationsByUserId(@PathVariable("userId") String userId);

    @GetMapping("/conversations/{id}")
    Map<String, Object> getConversationById(@PathVariable("id") String id);

    @GetMapping("/messages/conversation/{conversationId}")
    List<Map<String, Object>> getMessagesByConversationId(@PathVariable("conversationId") String conversationId);
}

