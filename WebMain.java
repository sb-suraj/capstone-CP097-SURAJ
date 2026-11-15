/** Optional: starts the browser dashboard instead of the terminal menu. Run: java WebMain [port] */
public class WebMain {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9090;
        new ShortLinkServer(port).start();
    }
}
