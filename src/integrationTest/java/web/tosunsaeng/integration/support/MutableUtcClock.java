package web.tosunsaeng.integration.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableUtcClock extends Clock {

    private final AtomicReference<Instant> current;

    public MutableUtcClock(Instant initialInstant) {
        this.current = new AtomicReference<>(Objects.requireNonNull(initialInstant));
    }

    public void set(Instant instant) {
        current.set(Objects.requireNonNull(instant));
    }

    public void advance(Duration duration) {
        current.updateAndGet(instant -> instant.plus(Objects.requireNonNull(duration)));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (!ZoneOffset.UTC.equals(zone)) {
            throw new IllegalArgumentException("통합 테스트 Clock은 UTC만 지원합니다.");
        }
        return this;
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}
