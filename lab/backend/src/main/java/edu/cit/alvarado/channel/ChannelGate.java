package edu.cit.alvarado.channel;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Tiangge's rule: "send the stock update after Tiangge has your decision,
 * not before." The feed processor holds this lock from the moment it
 * changes Inventory for a Tiangge order until Tiangge has acknowledged the
 * decision; the stock publisher takes the same lock before every PUT
 * /stock. Changes that don't come from Tiangge (React UI orders, supplier
 * deliveries) never hold it, so their stock goes out right away.
 */
@Component
class ChannelGate {

    private final ReentrantLock lock = new ReentrantLock(true);

    void lock() {
        lock.lock();
    }

    void unlock() {
        lock.unlock();
    }
}
