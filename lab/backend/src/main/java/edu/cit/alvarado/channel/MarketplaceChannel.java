package edu.cit.alvarado.channel;

/**
 * Public contract of the marketplace channel module (Lab 4). The channel
 * runs by itself once the app starts - heartbeats, listings, stock sync and
 * order-feed processing need no caller - so the only thing other code (or a
 * person) can ask it is how it is doing. Everything Tiangge-specific in this
 * package (HTTP client, JSON shapes, feed poller, translator, persistence)
 * is package-private.
 */
public interface MarketplaceChannel {

    ChannelStatus status();
}
