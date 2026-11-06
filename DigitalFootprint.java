
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
 
public class DigitalFootprint {
 
    //1. DATA 
   
    record Finding(String source, String type, String detail, String link) {}
 
    static final Map<String, Integer> WEIGHTS = Map.of("PUBLIC_PROFILE", 10, "EXPOSED_DATA", 8);
 
  
   static final String[][] SITES = {
    {"GitHub", "https://github.com/"},
    {"GitLab", "https://gitlab.com/"},
    {"Reddit", "https://www.reddit.com/user/"},
    {"Pinterest", "https://www.pinterest.com/"},
    {"X (Twitter)", "https://x.com/"},
    {"Instagram", "https://www.instagram.com/"}
};
    static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8)).build();
 
    static List<Finding> findings = new ArrayList<>();
    static String lastUser = "";
 
    //2. MENU
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.println("=== Digital Footprint Visualizer ===");
        while (true) {
            System.out.println("\n1) Scan a username");
            System.out.println("2) Show last results");
            System.out.println("3) Export results to CSV");
            System.out.println("4) Clear all data");
            System.out.println("0) Exit");
            System.out.print("Choose: ");
            String choice = in.nextLine().trim();
            switch (choice) {
                case "1" -> scan(in);
                case "2" -> showResults();
                case "3" -> export(in);
                case "4" -> clearAll();
                case "0" -> { System.out.println("Goodbye."); return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }
 
    // 3. SCAN: consent, validation, then collect
    static void scan(Scanner in) {
        System.out.println("\nThis tool only checks PUBLIC information, for YOUR OWN accounts.");
        System.out.print("Type YES to confirm your consent: ");
        if (!in.nextLine().trim().equalsIgnoreCase("YES")) {      
            System.out.println("Consent not given. Scan cancelled.");
            return;
        }
        System.out.print("Username: ");
        String user = in.nextLine().trim();
        if (!user.matches("[A-Za-z0-9_.-]{1,39}")) {             
            System.out.println("Invalid username (letters, digits, _ . - ; max 39 characters).");
            return;
        }
 
        findings = new ArrayList<>();
        lastUser = user;
        checkProfiles(user);
        checkGitHub(user);
        showResults();
    }
 
    // 4. COLLECTORS 
    static HttpResponse<String> fetch(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Educational-Project").build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
 
    // Collector 1: does a public profile exist? 
    static void checkProfiles(String user) {
        for (String[] site : SITES) {
            String link = site[1] + user;
            System.out.println("Checking " + site[0] + " ...");
            try {
                if (fetch(link).statusCode() == 200) {
                    findings.add(new Finding(site[0], "PUBLIC_PROFILE", "Public profile exists", link));
                }
                Thread.sleep(500);                               
            } catch (Exception e) {
                System.out.println("  " + site[0] + " could not be reached, skipped.");
            }
        }
    }
 
    // Collector 2: GitHub's official API
    static void checkGitHub(String user) {
        System.out.println("Checking GitHub API ...");
        try {
            HttpResponse<String> r = fetch("https://api.github.com/users/" + user);
            if (r.statusCode() == 200) {
                for (String field : new String[]{"name", "location", "email", "company", "bio"}) {
                    String value = jsonValue(r.body(), field);
                    if (value != null) {
                        findings.add(new Finding("GitHub API", "EXPOSED_DATA",
                                field + " is public: " + value, "https://github.com/" + user));
                    }
                }
            } else if (r.statusCode() == 403 || r.statusCode() == 429) {
                System.out.println("  GitHub API rate limit reached, skipped.");
            }
        } catch (Exception e) {
            System.out.println("  GitHub API could not be reached, skipped.");
        }
    }
 
    static String jsonValue(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return (m.find() && !m.group(1).isBlank()) ? m.group(1) : null;
    }
 
    // 5. SCORING, CHART AND RECOMMENDATIONS
    static String risk(Finding f) {
        return f.type().equals("EXPOSED_DATA") ? "Medium" : "Low";
    }
 
    static void showResults() {
        if (lastUser.isEmpty()) {
            System.out.println("\nNo scan has been run yet.");
            return;
        }
        System.out.println("\n=== RESULTS for " + lastUser + " ===");
        int profiles = 0, exposed = 0, score = 0;
 
        if (!findings.isEmpty()) {
            System.out.printf("%-11s %-15s %-7s %s%n", "SOURCE", "TYPE", "RISK", "DETAIL");
            System.out.println("-".repeat(80));
        }
        for (Finding f : findings) {
            System.out.printf("%-11s %-15s %-7s %s%n", f.source(), f.type(), risk(f), f.detail());
            System.out.println("            " + f.link());
            score += WEIGHTS.get(f.type());                       
            if (f.type().equals("PUBLIC_PROFILE")) profiles++; else exposed++;
        }
        score = Math.min(100, score);                             
        String level = score >= 60 ? "High" : score >= 30 ? "Medium" : "Low";
 
        System.out.println("\nExposure score: " + score + "/100 (" + level + ")");
        System.out.println("\nFindings by type:");
        System.out.printf("  %-16s | %s %d%n", "Public profiles", "#".repeat(profiles), profiles);
        System.out.printf("  %-16s | %s %d%n", "Exposed data", "#".repeat(exposed), exposed);
 
        System.out.println("\nRecommendations:");
        if (profiles > 0) System.out.println("  - Review privacy settings on each public profile and delete unused accounts.");
        if (exposed > 0) System.out.println("  - Remove real name, email, location and employer from public bios.");
        if (profiles + exposed > 0) System.out.println("  - Enable two-factor authentication and use unique passwords.");
        if (profiles + exposed == 0) System.out.println("  - No public exposure found in the checked sources. Re-scan regularly.");
    }
 
    // 6. EXPORT AND PRIVACY CONTROL
    static void export(Scanner in) {
        if (findings.isEmpty()) {
            System.out.println("Nothing to export.");
            return;
        }
        System.out.print("File name [footprint_report.csv]: ");
        String name = in.nextLine().trim();
        if (name.isEmpty()) name = "footprint_report.csv";
        try (PrintWriter w = new PrintWriter(name)) {
            w.println("Source,Type,Detail,Link,Risk");
            for (Finding f : findings) {
                w.println("\"" + f.source() + "\",\"" + f.type() + "\",\""
                        + f.detail().replace("\"", "\"\"") + "\",\"" + f.link() + "\",\"" + risk(f) + "\"");
            }
            System.out.println("Report saved to " + name);
        } catch (IOException e) {
            System.out.println("Could not save the file.");
        }
    }
 
    
    static void clearAll() {
        findings = new ArrayList<>();
        lastUser = "";
        System.out.println("All data cleared.");
    }
}
 