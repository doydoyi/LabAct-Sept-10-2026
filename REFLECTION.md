# REFLECTION — Lab 4: Tiangge Marketplace

## Question 1

Event evt_d735e413b0acf29c (order TG-EUC43Y) reached your application twice, as seq 1 and seq 2, and you processed it once. Show the code and the stored data that made the second delivery harmless, and explain what would happen if your application restarted between the two.

The first thing `OrderFeedProcessor.handle()` does for every feed event is check whether its eventId has been handled before:

```java
if (processedEvents.existsById(event.eventId())) {
    log.info("Feed seq={} eventId={} ({} {}) already processed - redelivery skipped", ...);
    advanceCursor(event.seq());
    return;
}
```

At seq 1, evt_d735e413b0acf29c was new. So `handleOrderPlaced()` ran, and inside one `TransactionTemplate` transaction it did four things:
- created the shop order through the same `OrderService.placeOrderOrBackorder()` my React UI uses;
- saved a `tiangge_orders` row linking TG-EUC43Y to that shop order;
- inserted `evt_d735e413b0acf29c` into `tiangge_processed_events` (event_id is the primary key);
- moved `tiangge_feed_cursor.last_seq` to 1.

At seq 2 the same eventId was already in `tiangge_processed_events`, so the check above logged "already processed - redelivery skipped" and only moved the cursor to 2. No second order was created, nothing was reserved again, and no second decision was sent. There is also a second guard: even with a different eventId, `links.existsById("TG-EUC43Y")` would have stopped a duplicate order, because `tiangge_orders` is keyed by the Tiangge orderId. If the app had restarted between seq 1 and seq 2, nothing would change, because both tables live in Postgres and not in memory: the new instance reads the stored cursor (1), asks for events after it, gets seq 2, and skips it the same way. If it had crashed in the middle of handling seq 1, the transaction would roll back the order, the link, the eventId and the cursor together, so on restart seq 1 would be processed cleanly once, never half-done or twice.

## Question 2

During your restart test your application was down for about 248 seconds while 6 orders arrived. How did the restarted application find those orders, and how did it avoid handling earlier ones again?

Nothing told the restarted app about those 6 orders; it found them by reading the feed itself. On startup `ChannelStartup` sends the first heartbeat, republishes listings and stock, and starts `FeedPoller`, which reads the last processed position from the `tiangge_feed_cursor` table and logs `Order feed polling started from stored cursor N`, where N is where the old instance stopped, not 0. It then calls `GET /feed?after=N&limit=50` and handles the events oldest first. It keeps requesting pages until one comes back with fewer than 50 events, so all 6 orders that arrived during the 248 seconds were found and decided in its first ticks. Earlier orders were not handled again for two reasons. First, `after=N` means Tiangge never sends them back. Second, any event Tiangge redelivers under a newer seq is still caught by its eventId in `tiangge_processed_events`. The cursor also only moves forward (`FeedCursor.advanceTo()` ignores lower values), and it is saved in the same transaction as each order, so it can never point before an order that was already created.

## Question 3

Tiangge may deliver the same event more than once. Describe how your application recognises an event it has already handled, where that knowledge is stored, and whether it survives a restart.

My application recognises an event by its `eventId`, not its `seq`: Tiangge gives a redelivered event a new seq, so the cursor alone cannot detect duplicates. Every handled eventId is stored as a row in the `tiangge_processed_events` table (event_id primary key, plus seq, type, Tiangge orderId and time). `OrderFeedProcessor.handle()` checks this table before doing anything with an event. That row is written in the same database transaction as the shop order, the `tiangge_orders` link and the cursor update, so "order created" and "event recorded as handled" can never get out of step. As a second layer, `tiangge_orders` is keyed by the Tiangge orderId, so one Tiangge order can only ever become one order in my system. Because all of this lives in the Postgres (Supabase) database and not in a Java `Set` in memory, it survives a restart, as the restart test showed: the new instance with a new instance ID still skipped everything the old one had already handled.
