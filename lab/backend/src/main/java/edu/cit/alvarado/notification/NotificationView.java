package edu.cit.alvarado.notification;

import java.time.OffsetDateTime;

public record NotificationView(Long notificationId, String message, OffsetDateTime createdAt) {
}
