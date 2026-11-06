package dev.heysulo.databridge.core.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;

/**
 * Helpers for code that may run on a Netty event loop, such as lifecycle calls made from a callback.
 */
public final class EventLoops {

    private EventLoops() {
    }

    /**
     * Returns whether the calling thread belongs to the group. Such a thread must not wait for the group to
     * terminate, because the group cannot terminate until that thread returns.
     */
    public static boolean isCurrentThreadIn(EventExecutorGroup group) {
        for (EventExecutor executor : group) {
            if (executor.inEventLoop()) {
                return true;
            }
        }
        return false;
    }
}
