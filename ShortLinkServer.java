import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Web layer: the dashboard pages and the redirect. Uses only the JDK's built-in HTTP server. */
public class ShortLinkServer {

    private static final long MAX_EXPIRY_DAYS = 3650;

    private final ShortLinkService service;
    private final int port;

    /** Web server with its own, new set of links. */
    public ShortLinkServer(int port) {
        this(new ShortLinkService(), port);
    }

    /** Web server that shares an existing service, so the terminal and the browser see the same links. */
    public ShortLinkServer(ShortLinkService service, int port) {
        this.service = service;
        this.port = port;
    }

    public HttpServer start() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/", this::handle);
        httpServer.start();
        System.out.println("ShortLink running at http://localhost:" + port + "/  (Ctrl+C to stop)");
        return httpServer;
    }

    /** Routes every request to the right handler. */
    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            try {
                if (path.equals("/") && method.equals("GET")) {
                    send(exchange, 200, home());
                } else if (path.equals("/shorten") && method.equals("POST")) {
                    handleShorten(exchange);
                } else if (path.equals("/expand") && method.equals("POST")) {
                    handleExpand(exchange);
                } else if (path.equals("/clicks") && method.equals("POST")) {
                    handleClicks(exchange);
                } else if (path.equals("/top") && method.equals("GET")) {
                    handleTop(exchange);
                } else if (path.length() > 1 && method.equals("GET")) {
                    handleRedirect(exchange, path.substring(1));
                } else {
                    send(exchange, 404, page("Not found", "<p>Page not found</p>"));
                }
            } catch (IllegalArgumentException e) {
                send(exchange, 400,
                        page("Input error", "<p class='err'>" + esc(e.getMessage()) + "</p>"));
            } catch (RuntimeException e) {
                send(exchange, 500,
                        page("Error", "<p class='err'>Something went wrong. Please try again.</p>"));
            }
        }
    }

    private void handleShorten(HttpExchange exchange) throws IOException {
        String body = read(exchange);
        Instant expiresAt = parseExpiry(val(body, "days"));

        LinkEntry entry = service.shorten(val(body, "url"), val(body, "alias"), expiresAt);

        String shortUrl = "http://localhost:" + port + "/" + entry.code;
        String expiry = entry.expiresAt == null ? "never" : entry.expiresAt.toString();
        send(exchange, 200, page("Created",
                "<h2>Your short link</h2><p><a href='" + esc(shortUrl) + "'>" + esc(shortUrl)
                + "</a></p><p>Code: " + esc(entry.code) + "</p><p>Expires: " + esc(expiry) + "</p>"));
    }

    private void handleExpand(HttpExchange exchange) throws IOException {
        LinkEntry entry = service.find(val(read(exchange), "code").trim());
        send(exchange, entry == null ? 404 : 200, page("Expand",
                entry == null
                        ? "<p class='err'>Code not found or expired</p>"
                        : "<h2>Original URL</h2><p><a href='" + esc(entry.originalUrl) + "'>"
                          + esc(entry.originalUrl) + "</a></p>"));
    }

    private void handleClicks(HttpExchange exchange) throws IOException {
        String code = val(read(exchange), "code").trim();
        long count = service.clicks(code);
        send(exchange, count < 0 ? 404 : 200, page("Clicks",
                count < 0
                        ? "<p class='err'>Code not found or expired</p>"
                        : "<h2>Clicks for " + esc(code) + "</h2><p class='count'>" + count + "</p>"));
    }

    private void handleTop(HttpExchange exchange) throws IOException {
        StringBuilder html = new StringBuilder(
                "<h2>Top 10 links by clicks</h2>"
                + "<table><tr><th>Rank</th><th>Code</th><th>Original URL</th>"
                + "<th>Clicks</th><th>Expires</th></tr>");
        int rank = 1;
        for (LinkEntry entry : service.top()) {
            html.append("<tr><td>").append(rank++)
                .append("</td><td>").append(esc(entry.code))
                .append("</td><td>").append(esc(entry.originalUrl))
                .append("</td><td>").append(entry.getClicks())
                .append("</td><td>").append(entry.expiresAt == null ? "never" : esc(entry.expiresAt.toString()))
                .append("</td></tr>");
        }
        html.append("</table>");
        send(exchange, 200, page("Top links", html.toString()));
    }

    private void handleRedirect(HttpExchange exchange, String code) throws IOException {
        LinkEntry entry = service.expand(code);   // counts the click
        if (entry == null) {
            send(exchange, 404, page("Not found", "<p>Short link not found or expired</p>"));
        } else {
            exchange.getResponseHeaders().set("Location", entry.originalUrl);
            exchange.sendResponseHeaders(302, -1);
        }
    }

    /** Empty -> never expires. Otherwise a whole number of days from now. */
    private Instant parseExpiry(String days) {
        if (days.isBlank()) {
            return null;
        }
        long numberOfDays;
        try {
            numberOfDays = Long.parseLong(days.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expiry days must be a whole number");
        }
        if (numberOfDays < 1 || numberOfDays > MAX_EXPIRY_DAYS) {
            throw new IllegalArgumentException("Expiry days must be between 1 and " + MAX_EXPIRY_DAYS);
        }
        return Instant.now().plusSeconds(numberOfDays * 86400);
    }

    private String home() {
        return page("ShortLink Dashboard", """
            <h1>ShortLink</h1>
            <p>URL Shortening and Click Analytics</p>

            <div class='grid'>

                <section>
                    <h2>1. Shorten a URL</h2>
                    <form method='post' action='/shorten'>
                        <input name='url' type='url'
                               placeholder='https://example.com' required>
                        <input name='days' type='number' min='1'
                               placeholder='expires in days (optional)'>
                        <button>Generate short link</button>
                    </form>
                </section>

                <section>
                    <h2>2. Custom alias</h2>
                    <form method='post' action='/shorten'>
                        <input name='url' type='url'
                               placeholder='https://example.com' required>
                        <input name='alias'
                               placeholder='my-link (3-20 chars)'>
                        <input name='days' type='number' min='1'
                               placeholder='expires in days (optional)'>
                        <button>Create alias</button>
                    </form>
                </section>

                <section>
                    <h2>3. Expand a short link</h2>
                    <form method='post' action='/expand'>
                        <input name='code'
                               placeholder='Enter short code' required>
                        <button>Show original URL</button>
                    </form>
                </section>

                <section>
                    <h2>4. Show clicks</h2>
                    <form method='post' action='/clicks'>
                        <input name='code'
                               placeholder='Enter short code' required>
                        <button>Show click count</button>
                    </form>
                </section>

                <section>
                    <h2>5. Top links</h2>
                    <p>View the ten most-clicked links.</p>
                    <a class='button' href='/top'>View top links</a>
                </section>

            </div>

            <p class='note'>
                Data resets when the server stops.
                Opening a short link increases its click count.
            </p>
            """);
    }

    private String page(String title, String body) {
        return """
            <!doctype html>
            <html>
            <head>
                <meta charset='UTF-8'>
                <meta name='viewport'
                      content='width=device-width,initial-scale=1'>

                <title>%s</title>

                <style>
                    * {
                        box-sizing: border-box;
                    }

                    body {
                        font-family: Arial;
                        background: #f2f4f8;
                        padding: 30px;
                        color: #202638;
                    }

                    main {
                        max-width: 950px;
                        margin: auto;
                        background: white;
                        padding: 28px;
                        border-radius: 14px;
                        box-shadow: 0 4px 22px #0001;
                    }

                    h1 {
                        color: #3157c8;
                    }

                    .grid {
                        display: grid;
                        grid-template-columns:
                            repeat(auto-fit, minmax(245px, 1fr));
                        gap: 16px;
                        margin-top: 22px;
                    }

                    section {
                        border: 1px solid #ddd;
                        border-radius: 10px;
                        padding: 17px;
                    }

                    input {
                        width: 100%%;
                        padding: 11px;
                        margin: 7px 0;
                        border: 1px solid #ccd;
                        border-radius: 7px;
                    }

                    button, .button {
                        background: #3157c8;
                        color: white;
                        padding: 10px 14px;
                        border: 0;
                        border-radius: 7px;
                        text-decoration: none;
                        cursor: pointer;
                    }

                    table {
                        width: 100%%;
                        border-collapse: collapse;
                    }

                    td, th {
                        text-align: left;
                        padding: 9px;
                        border-bottom: 1px solid #ddd;
                        overflow-wrap: anywhere;
                    }

                    .err {
                        color: #b22;
                    }

                    .count {
                        font-size: 40px;
                        color: #3157c8;
                    }

                    .note {
                        color: #667085;
                        font-size: 13px;
                    }

                    a {
                        overflow-wrap: anywhere;
                    }
                </style>
            </head>

            <body>
                <main>
                    %s
                    <p><a href='/'>&larr; Back to dashboard</a></p>
                </main>
            </body>
            </html>
            """.formatted(esc(title), body);
    }

    private String read(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Reads one field from a form body like "url=...&alias=...". Returns "" if missing. */
    private String val(String body, String key) {
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals(key)) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    /** Escapes user text so it cannot inject HTML or JavaScript (XSS). */
    private String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private void send(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
