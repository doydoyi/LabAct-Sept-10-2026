package edu.cit.alvarado.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationView> listNotifications() {
        return notificationService.listAll().stream()
                .map(n -> new NotificationView(n.getNotificationId(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}
