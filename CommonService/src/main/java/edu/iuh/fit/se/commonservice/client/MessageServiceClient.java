package edu.iuh.fit.se.commonservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * OpenFeign client for calling MessegeService from CommonService.
 */
@FeignClient(
        name = "message-service",
        url = "${message.service.url:http://localhost:8082}"
)
public interface MessageServiceClient {

    @GetMapping("/conversations/{id}")
    Map<String, Object> getConversationById(@PathVariable("id") String id);
}

