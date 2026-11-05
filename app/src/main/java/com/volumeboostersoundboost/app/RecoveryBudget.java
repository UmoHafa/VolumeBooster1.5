package com.volumeboostersoundboost.app;
/** Bounded retries per explicit user action or route/session change. */
final class RecoveryBudget {
    private int attempts;
    private long nextAttempt;
    void reset() { attempts = 0; nextAttempt = 0; }
    boolean acquire(long now) {
        if (attempts >= 3 || now < nextAttempt) return false;
        nextAttempt = now + (3000L << attempts);
        attempts++;
        return true;
    }
    int attempts() { return attempts; }
}
