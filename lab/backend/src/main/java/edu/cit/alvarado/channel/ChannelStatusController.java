package edu.cit.alvarado.channel;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only status for people (GET /api/channel/status). Never called by Tiangge. */
@RestController
@RequestMapping("/api/channel")
class ChannelStatusController {

    private final MarketplaceChannel channel;

    ChannelStatusController(MarketplaceChannel channel) {
        this.channel = channel;
    }

    @GetMapping("/status")
    ChannelStatus status() {
        return channel.status();
    }
}
