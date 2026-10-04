package edu.cit.alvarado.channel;

import edu.cit.alvarado.instance.AppInstance;
import org.springframework.stereotype.Service;

@Service
class MarketplaceChannelImpl implements MarketplaceChannel {

    private final AppInstance appInstance;
    private final ChannelStartup startup;
    private final HeartbeatSender heartbeat;
    private final FeedPoller feedPoller;
    private final OrderFeedProcessor processor;
    private final TianggeOrderLinkRepository links;

    MarketplaceChannelImpl(AppInstance appInstance, ChannelStartup startup, HeartbeatSender heartbeat,
                           FeedPoller feedPoller, OrderFeedProcessor processor, TianggeOrderLinkRepository links) {
        this.appInstance = appInstance;
        this.startup = startup;
        this.heartbeat = heartbeat;
        this.feedPoller = feedPoller;
        this.processor = processor;
        this.links = links;
    }

    @Override
    public ChannelStatus status() {
        return new ChannelStatus(
                appInstance.id(),
                appInstance.startedAt(),
                startup.isLive(),
                heartbeat.lastSuccess(),
                feedPoller.lastSuccessfulRead(),
                processor.currentCursor(),
                links.count(),
                links.countByDecision(TianggeOrderTranslator.ACCEPTED),
                links.countByDecision(TianggeOrderTranslator.REJECTED),
                links.countByDecision(TianggeOrderTranslator.BACKORDERED));
    }
}
