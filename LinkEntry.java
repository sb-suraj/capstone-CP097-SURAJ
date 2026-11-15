import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** One short link: its code, the original URL, click counter and optional expiry. */
public class LinkEntry {

    public final String code;
    public final String originalUrl;
    public final Instant expiresAt;              // null means the link never expires
    private final AtomicLong clicks = new AtomicLong();

    public LinkEntry(String code, String originalUrl, Instant expiresAt) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
    }

    public void addClick() {
        clicks.incrementAndGet();
    }

    public long getClicks() {
        return clicks.get();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
