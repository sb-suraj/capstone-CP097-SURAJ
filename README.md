# CP-097 ShortLink: Base62 URL Shortening and Click Analytics

A link shortener written in plain Java (no libraries). It turns a long URL into a short code,
counts clicks, and works from a terminal menu (with an optional browser dashboard).

## Run

```
javac *.java
java Main
```

A menu appears in the terminal and a small web server starts in the background (port 8080, or the
next free port). Choose 1, paste a long URL, and you get a short link such as
`http://localhost:8080/1`. Paste that link into a browser while the program is running and it
redirects to the original URL and counts a click. Data is stored in memory, so it resets when
you choose 0 to exit.

Optional: `java WebMain` starts only the browser dashboard (http://localhost:9090), without the terminal menu.

## Files

| File | Purpose |
|---|---|
| `Base62.java` | `encode(number)` and `decode(code)` using the alphabet 0-9, a-z, A-Z |
| `LinkEntry.java` | One link: code, original URL, click counter, optional expiry |
| `ShortLinkService.java` | All the logic: shorten, expand, clicks, aliases, expiry, top links |
| `Main.java` | Terminal menu (the main program); also starts the web server in the background |
| `ShortLinkServer.java` | Browser dashboard and the redirect (JDK built-in HTTP server) |
| `WebMain.java` | Optional: starts only the browser dashboard |

## Features

Compulsory
1. Short code from a counter converted to base 62 (counter 1, 2, 3 ... gives `1`, `2`, `3` ... `10`, `11`)
2. Expand a short code back to the original URL
3. Same long URL gives the same code (`codeByUrl` map)
4. Click count per code (opening the short link counts a click)
5. Custom alias: 3-20 letters, digits, `_` or `-`, must be unique, cannot be a page name (`top`, `expand`, `clicks`, `shorten`)

Stretch
- Optional expiry in days (expired links return 404 and their code/alias can be reused)
- Top 10 links by clicks

## Design notes (for the viva)

- **Base62 encoding:** repeatedly take `number % 62` for the next character, then divide by 62; the digits are reversed at the end. `decode` does the reverse.
- **Two-way lookup:** `links` (code to link) makes redirects fast; `codeByUrl` (URL to code) makes "same URL, same code" fast. Both are hash maps, so lookups are O(1).
- **Counter, not random:** a counter never produces two equal codes, so no collision handling is needed except skipping codes already taken by a custom alias.
- **Thread safety:** `ConcurrentHashMap`, `AtomicLong` for the counter and click counts, and `synchronized` on `shorten` so two requests cannot take the same alias.
- **Validation:** URLs are parsed with `java.net.URI` and must be http/https with a host. Aliases are checked with a regular expression.
- **Security:** all user text is HTML-escaped (prevents XSS); only http/https URLs can be redirected to.
- **Limits and improvements:** codes are sequential and guessable; data is lost on restart. A real system would use a database with a unique index on the code, and random or shuffled IDs.
