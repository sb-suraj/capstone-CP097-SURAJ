import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/** Core logic: create, expand and count short links. No web code in here. */
public class ShortLinkService {

    // Paths used by the web app itself, so they can never be short codes
    private static final Set<String> RESERVED = Set.of("top", "expand", "clicks", "shorten");
    private static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z0-9_-]{3,20}");
    private static final int TOP_LIMIT = 10;

    private final Map<String, LinkEntry> links = new ConcurrentHashMap<>();   // code -> link
    private final Map<String, String> codeByUrl = new ConcurrentHashMap<>();  // long URL -> code
    private final AtomicLong nextId = new AtomicLong(1);

    /** Shorten a URL. alias may be null/blank; expiresAt may be null (never expires). */
    public synchronized LinkEntry shorten(String url, String alias, Instant expiresAt) {
        String cleanUrl = validateUrl(url);

        // Feature 3: same long URL -> same code
        String existingCode = codeByUrl.get(cleanUrl);
        if (existingCode != null) {
            LinkEntry existing = links.get(existingCode);
            if (existing != null && !existing.isExpired()) {
                if (hasText(alias) && !existingCode.equals(alias.trim())) {
                    throw new IllegalArgumentException("URL already has code: " + existingCode);
                }
                return existing;
            }
            removeLink(existingCode);   // old link expired, so its code is free again
        }

        String code;
        if (!hasText(alias)) {
            // Feature 1: counter -> base62, skipping codes already in use
            do {
                code = Base62.encode(nextId.getAndIncrement());
            } while (links.containsKey(code) || RESERVED.contains(code));
        } else {
            // Feature 5: custom alias, validated for characters and uniqueness
            code = alias.trim();
            if (!ALIAS_PATTERN.matcher(code).matches()) {
                throw new IllegalArgumentException("Alias must be 3-20 letters, numbers, _ or -");
            }
            if (RESERVED.contains(code.toLowerCase())) {
                throw new IllegalArgumentException("That alias is reserved. Please choose another");
            }
            LinkEntry taken = links.get(code);
            if (taken != null && taken.isExpired()) {
                removeLink(code);       // an expired alias can be reused
            }
            if (links.containsKey(code)) {
                throw new IllegalArgumentException("Alias already taken");
            }
        }

        LinkEntry entry = new LinkEntry(code, cleanUrl, expiresAt);
        links.put(code, entry);
        codeByUrl.put(cleanUrl, code);
        return entry;
    }

    public LinkEntry shorten(String url, String alias) {
        return shorten(url, alias, null);
    }

    /** Look a code up WITHOUT counting a click. Expired links count as not found. */
    public LinkEntry find(String code) {
        LinkEntry entry = links.get(code);
        return (entry == null || entry.isExpired()) ? null : entry;
    }

    /** Feature 2 + 4: look a code up AND count one click. */
    public LinkEntry expand(String code) {
        LinkEntry entry = find(code);
        if (entry != null) {
            entry.addClick();
        }
        return entry;
    }

    /** Click count for a code, or -1 if the code does not exist (or expired). */
    public long clicks(String code) {
        LinkEntry entry = find(code);
        return entry == null ? -1 : entry.getClicks();
    }

    /** Stretch: the 10 most-clicked links that have not expired. */
    public List<LinkEntry> top() {
        // snapshot the click counts first so sorting is not affected by clicks arriving mid-sort
        List<Ranked> ranked = new ArrayList<>();
        for (LinkEntry entry : links.values()) {
            if (!entry.isExpired()) {
                ranked.add(new Ranked(entry, entry.getClicks()));
            }
        }
        ranked.sort(Comparator.comparingLong(Ranked::clicks).reversed());

        List<LinkEntry> result = new ArrayList<>();
        for (Ranked r : ranked.subList(0, Math.min(TOP_LIMIT, ranked.size()))) {
            result.add(r.entry());
        }
        return result;
    }

    private record Ranked(LinkEntry entry, long clicks) {}

    private void removeLink(String code) {
        LinkEntry removed = links.remove(code);
        if (removed != null) {
            codeByUrl.remove(removed.originalUrl, code);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String validateUrl(String url) {
        if (!hasText(url)) {
            throw new IllegalArgumentException("URL must not be empty");
        }
        String clean = url.trim();
        try {
            URI uri = new URI(clean);
            String scheme = uri.getScheme();
            boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!httpScheme || uri.getHost() == null) {
                throw new IllegalArgumentException("URL must start with http:// or https:// and have a host");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("That is not a valid URL");
        }
        return clean;
    }
}
