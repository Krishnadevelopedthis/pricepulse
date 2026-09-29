package com.pricepulse.web;

import com.pricepulse.dto.AckRequest;
import com.pricepulse.dto.NotificationDto;
import com.pricepulse.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final ProductService service;

    public NotificationController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public List<NotificationDto> pending(@RequestAttribute("clientId") String clientId) {
        return service.pendingNotifications(clientId);
    }

    @PostMapping("/ack")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ack(@RequestAttribute("clientId") String clientId, @Valid @RequestBody AckRequest body) {
        service.acknowledge(clientId, body.productIds());
    }
}
