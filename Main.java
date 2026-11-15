import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;

/**
 * Terminal menu. It also starts the web server in the background, so every short link
 * you create here opens in the browser while the program is running.
 */
public class Main {

    private static final int DEFAULT_PORT = 8080;
    private static final int PORT_TRIES = 11;      // tries 8080, 8081 ... 8090 if a port is busy
    private static final long MAX_EXPIRY_DAYS = 3650;

    private final ShortLinkService service = new ShortLinkService();
    private final Scanner input = new Scanner(System.in);
    private HttpServer webServer;
    private String baseUrl = "short.ly/";          // replaced by http://localhost:<port>/ once the server starts

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new Main().run(port);
    }

    private void run(int firstPort) {
        System.out.println("===== ShortLink: Base62 URL Shortener =====");
        startWebServer(firstPort);

        try {
            menuLoop();
        } finally {
            if (webServer != null) {
                webServer.stop(0);
            }
        }
    }

    private void menuLoop() {
        while (true) {
            printMenu();
            if (!input.hasNextLine()) {
                return;
            }
            String choice = input.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> shorten(false);
                    case "2" -> shorten(true);
                    case "3" -> expand();
                    case "4" -> showClicks();
                    case "5" -> showTop();
                    case "0" -> {
                        System.out.println("Bye!");
                        return;
                    }
                    default -> System.out.println("Please choose a number from 0 to 5.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    /** Starts the browser side. If the port is busy, the next one is tried. */
    private void startWebServer(int firstPort) {
        for (int port = firstPort; port < firstPort + PORT_TRIES; port++) {
            try {
                webServer = new ShortLinkServer(service, port).start();
                baseUrl = "http://localhost:" + port + "/";
                System.out.println("Short links will open in your browser while this program is running.");
                return;
            } catch (IOException e) {
                // port already in use: try the next one
            }
        }
        System.out.println("Could not start the web server (ports " + firstPort + "-"
                + (firstPort + PORT_TRIES - 1) + " are busy).");
        System.out.println("Links will work in this menu only, not in the browser.");
    }

    private void printMenu() {
        System.out.println();
        System.out.println("1. Shorten a URL");
        System.out.println("2. Shorten a URL with a custom alias");
        System.out.println("3. Expand a short link");
        System.out.println("4. Show clicks for a short link");
        System.out.println("5. Show top links");
        System.out.println("0. Exit");
        System.out.print("Choose: ");
    }

    private void shorten(boolean withAlias) {
        String url = ask("Enter long URL: ");
        String alias = withAlias ? ask("Enter custom alias (3-20 letters, numbers, _ or -): ") : "";
        Instant expiresAt = askExpiry();

        LinkEntry entry = service.shorten(url, alias, expiresAt);
        System.out.println("Short link: " + baseUrl + entry.code);
        if (entry.expiresAt != null) {
            System.out.println("Expires:    " + entry.expiresAt);
        }
    }

    /** Shows the original URL. Clicks are counted when the short link is opened in the browser. */
    private void expand() {
        String code = stripBase(ask("Enter short link or code: "));
        LinkEntry entry = service.find(code);
        System.out.println(entry == null
                ? "Short link not found or expired."
                : "Original URL: " + entry.originalUrl);
    }

    private void showClicks() {
        String code = stripBase(ask("Enter short link or code: "));
        long count = service.clicks(code);
        System.out.println(count < 0 ? "Short link not found or expired." : "Clicks: " + count);
    }

    private void showTop() {
        List<LinkEntry> top = service.top();
        if (top.isEmpty()) {
            System.out.println("No links yet.");
            return;
        }
        for (LinkEntry entry : top) {
            System.out.println(baseUrl + entry.code + "  " + entry.getClicks()
                    + " clicks  " + entry.originalUrl);
        }
    }

    private String ask(String question) {
        System.out.print(question);
        return input.hasNextLine() ? input.nextLine().trim() : "";
    }

    /** Empty -> never expires. Otherwise a whole number of days from now. */
    private Instant askExpiry() {
        String days = ask("Expires in how many days? (press Enter to skip): ");
        if (days.isEmpty()) {
            return null;
        }
        long numberOfDays;
        try {
            numberOfDays = Long.parseLong(days);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expiry days must be a whole number");
        }
        if (numberOfDays < 1 || numberOfDays > MAX_EXPIRY_DAYS) {
            throw new IllegalArgumentException("Expiry days must be between 1 and " + MAX_EXPIRY_DAYS);
        }
        return Instant.now().plusSeconds(numberOfDays * 86400);
    }

    /** Lets the user type either the full short link or just the code. */
    private String stripBase(String text) {
        return text.startsWith(baseUrl) ? text.substring(baseUrl.length()) : text;
    }
}
