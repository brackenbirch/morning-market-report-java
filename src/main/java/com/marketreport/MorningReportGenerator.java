package com.marketreport;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MorningReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MorningReportGenerator.class);
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String newsApiKey;
    private final EmailService emailService;
    
    // Major symbols to track
    private final String[] MAJOR_INDICES = {"^GSPC", "^DJI", "^IXIC", "^RUT", "^VIX"};
    private final String[] MAJOR_STOCKS = {
        "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", 
        "META", "NVDA", "JPM", "JNJ", "PG"
    };

    public MorningReportGenerator() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.newsApiKey = System.getenv("NEWS_API_KEY");
        this.emailService = new EmailService();
        
        logger.info("Morning Report Generator initialized");
    }

    public static void main(String[] args) {
        try {
            MorningReportGenerator generator = new MorningReportGenerator();
            generator.generateAndSendReport();
        } catch (Exception e) {
            logger.error("Error generating morning report", e);
            System.exit(1);
        }
    }

    public void generateAndSendReport() throws Exception {
        logger.info("Starting morning report generation...");
        
        // Fetch market data
        List<MarketData> marketData = fetchMarketData();
        
        // Fetch news headlines
        List<NewsHeadline> headlines = fetchNewsHeadlines();
        
        // Generate HTML report
        String htmlReport = generateHTMLReport(marketData, headlines);
        
        // Save report to file
        String reportPath = saveReportToFile(htmlReport);
        
        // Send email
        emailService.sendReport(htmlReport, marketData, headlines);
        
        logger.info("Morning report generated and sent successfully!");
    }

    private List<MarketData> fetchMarketData() throws IOException {
        logger.info("Fetching market data...");
        List<MarketData> marketData = new ArrayList<>();
        
        // Combine all symbols
        List<String> allSymbols = new ArrayList<>();
        allSymbols.addAll(Arrays.asList(MAJOR_INDICES));
        allSymbols.addAll(Arrays.asList(MAJOR_STOCKS));
        
        for (String symbol : allSymbols) {
            try {
                MarketData data = fetchYahooFinanceData(symbol);
                if (data != null) {
                    marketData.add(data);
                }
                
                // Small delay to avoid rate limiting
                Thread.sleep(100);
                
            } catch (Exception e) {
                logger.warn("Error fetching data for symbol: " + symbol, e);
            }
        }
        
        logger.info("Fetched data for {} symbols", marketData.size());
        return marketData;
    }

    private MarketData fetchYahooFinanceData(String symbol) throws IOException {
        // Yahoo Finance API endpoint
        String url = String.format(
            "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=5d", 
            symbol
        );

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("Failed to fetch data for {}: {}", symbol, response.code());
                return null;
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            JsonObject chart = jsonResponse.getAsJsonObject("chart");
            JsonArray result = chart.getAsJsonArray("result");
            
            if (result.size() == 0) {
                return null;
            }
            
            JsonObject data = result.get(0).getAsJsonObject();
            JsonObject meta = data.getAsJsonObject("meta");
            
            // Extract price data
            double currentPrice = meta.get("regularMarketPrice").getAsDouble();
            double previousClose = meta.get("previousClose").getAsDouble();
            double change = currentPrice - previousClose;
            double changePercent = (change / previousClose) * 100;
            
            // Get volume and market cap if available
            long volume = meta.has("regularMarketVolume") ? 
                meta.get("regularMarketVolume").getAsLong() : 0L;
            
            // Pre-market data if available
            double preMarketPrice = meta.has("preMarketPrice") ? 
                meta.get("preMarketPrice").getAsDouble() : 0.0;
            double preMarketChange = meta.has("preMarketChange") ? 
                meta.get("preMarketChange").getAsDouble() : 0.0;
            double preMarketChangePercent = meta.has("preMarketChangePercent") ? 
                meta.get("preMarketChangePercent").getAsDouble() : 0.0;

            String displayName = meta.has("longName") ? 
                meta.get("longName").getAsString() : symbol;

            return new MarketData(
                symbol, displayName, previousClose, currentPrice, change, changePercent,
                volume, preMarketPrice, preMarketChange, preMarketChangePercent
            );
        }
    }

    private List<NewsHeadline> fetchNewsHeadlines() throws IOException {
        logger.info("Fetching news headlines...");
        
        if (newsApiKey == null || newsApiKey.isEmpty()) {
            logger.warn("NEWS_API_KEY not provided, skipping news fetch");
            return new ArrayList<>();
        }

        List<NewsHeadline> headlines = new ArrayList<>();
        
        // Calculate time range (last 16 hours)
        LocalDateTime sixteenHoursAgo = LocalDateTime.now().minusHours(16);
        String fromDate = sixteenHoursAgo.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String url = "https://newsapi.org/v2/everything?" +
                "q=(stock market OR economy OR federal reserve OR inflation OR earnings) AND (US OR America)&" +
                "from=" + fromDate + "&" +
                "sortBy=relevancy&" +
                "language=en&" +
                "pageSize=15&" +
                "apiKey=" + newsApiKey;

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("Failed to fetch news: {}", response.code());
                return headlines;
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            JsonArray articles = jsonResponse.getAsJsonArray("articles");
            
            for (JsonElement articleElement : articles) {
                JsonObject article = articleElement.getAsJsonObject();
                
                String title = getJsonString(article, "title");
                String description = getJsonString(article, "description");
                String source = article.getAsJsonObject("source").get("name").getAsString();
                String publishedAt = getJsonString(article, "publishedAt");
                String url_link = getJsonString(article, "url");
                
                int relevanceScore = calculateRelevanceScore(title + " " + description);
                
                headlines.add(new NewsHeadline(title, description, source, publishedAt, url_link, relevanceScore));
            }
        }
        
        // Sort by relevance score
        headlines.sort((h1, h2) -> Integer.compare(h2.relevanceScore, h1.relevanceScore));
        
        logger.info("Fetched {} news headlines", headlines.size());
        return headlines.subList(0, Math.min(10, headlines.size())); // Top 10
    }

    private String getJsonString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return (element != null && !element.isJsonNull()) ? element.getAsString() : "";
    }

    private int calculateRelevanceScore(String text) {
        String[] highImpactKeywords = {
            "federal reserve", "fed", "interest rate", "inflation", "recession",
            "earnings", "gdp", "unemployment", "market crash", "rally",
            "stimulus", "trade war", "geopolitical"
        };
        
        String[] mediumImpactKeywords = {
            "stock market", "dow jones", "nasdaq", "s&p 500", "wall street",
            "investor", "trading", "economic", "financial"
        };
        
        String textLower = text.toLowerCase();
        int score = 0;
        
        for (String keyword : highImpactKeywords) {
            if (textLower.contains(keyword)) {
                score += 3;
            }
        }
        
        for (String keyword : mediumImpactKeywords) {
            if (textLower.contains(keyword)) {
                score += 1;
            }
        }
        
        return score;
    }

    private String generateHTMLReport(List<MarketData> marketData, List<NewsHeadline> headlines) {
        logger.info("Generating HTML report...");
        
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html>\n")
            .append("<head>\n")
            .append("    <title>Morning Market Report - ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))).append("</title>\n")
            .append("    <style>\n")
            .append("        body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }\n")
            .append("        .header { background-color: #1f4e79; color: white; padding: 20px; text-align: center; border-radius: 8px; }\n")
            .append("        .section { margin: 20px 0; }\n")
            .append("        .data-table { border-collapse: collapse; width: 100%; margin: 10px 0; }\n")
            .append("        .data-table th, .data-table td { border: 1px solid #ddd; padding: 12px; text-align: left; }\n")
            .append("        .data-table th { background-color: #f2f2f2; font-weight: bold; }\n")
            .append("        .positive { color: #28a745; font-weight: bold; }\n")
            .append("        .negative { color: #dc3545; font-weight: bold; }\n")
            .append("        .news-item { border-left: 4px solid #1f4e79; padding: 15px; margin: 15px 0; background-color: #f8f9fa; border-radius: 4px; }\n")
            .append("        .news-title { font-size: 1.1em; font-weight: bold; margin-bottom: 8px; }\n")
            .append("        .news-meta { color: #666; font-size: 0.9em; }\n")
            .append("        .footer { color: #666; font-size: 0.9em; margin-top: 30px; text-align: center; }\n")
            .append("        .summary-box { background-color: #e9ecef; padding: 15px; border-radius: 8px; margin: 15px 0; }\n")
            .append("    </style>\n")
            .append("</head>\n")
            .append("<body>\n");

        // Header
        html.append("    <div class=\"header\">\n")
            .append("        <h1>🌅 Morning Market Report</h1>\n")
            .append("        <p>Generated: ").append(timestamp).append(" EST</p>\n")
            .append("    </div>\n");

        // Market Summary
        html.append("    <div class=\"section\">\n")
            .append("        <h2>📊 Market Summary</h2>\n")
            .append("        <div class=\"summary-box\">\n")
            .append("            <p><strong>Market Sentiment:</strong> ").append(analyzeMarketSentiment(marketData)).append("</p>\n")
            .append("            <p><strong>Top Mover:</strong> ").append(getTopMover(marketData)).append("</p>\n")
            .append("            <p><strong>Headlines Tracked:</strong> ").append(headlines.size()).append(" relevant stories</p>\n")
            .append("            <p><strong>Market Opens:</strong> 9:30 AM EST</p>\n")
            .append("        </div>\n")
            .append("    </div>\n");

        // Pre-Market Movements Table
        html.append("    <div class=\"section\">\n")
            .append("        <h2>📈 Pre-Market Movements</h2>\n")
            .append("        <table class=\"data-table\">\n")
            .append("            <thead>\n")
            .append("                <tr>\n")
            .append("                    <th>Symbol</th>\n")
            .append("                    <th>Name</th>\n")
            .append("                    <th>Previous Close</th>\n")
            .append("                    <th>Current Price</th>\n")
            .append("                    <th>Change ($)</th>\n")
            .append("                    <th>Change (%)</th>\n")
            .append("                    <th>Volume</th>\n")
            .append("                </tr>\n")
            .append("            </thead>\n")
            .append("            <tbody>\n");

        // Add market data rows
        for (MarketData data : marketData) {
            String changeClass = data.change >= 0 ? "positive" : "negative";
            String changeSymbol = data.change >= 0 ? "+" : "";
            
            html.append("                <tr>\n")
                .append("                    <td><strong>").append(data.symbol).append("</strong></td>\n")
                .append("                    <td>").append(truncate(data.name, 40)).append("</td>\n")
                .append("                    <td>$").append(String.format("%.2f", data.previousClose)).append("</td>\n")
                .append("                    <td>$").append(String.format("%.2f", data.currentPrice)).append("</td>\n")
                .append("                    <td class=\"").append(changeClass).append("\">").append(changeSymbol).append("$").append(String.format("%.2f", data.change)).append("</td>\n")
                .append("                    <td class=\"").append(changeClass).append("\">").append(changeSymbol).append(String.format("%.2f", data.changePercent)).append("%</td>\n")
                .append("                    <td>").append(formatVolume(data.volume)).append("</td>\n")
                .append("                </tr>\n");
        }

        html.append("            </tbody>\n")
            .append("        </table>\n")
            .append("    </div>\n");

        // News Headlines
        html.append("    <div class=\"section\">\n")
            .append("        <h2>📰 Overnight Headlines</h2>\n");

        for (NewsHeadline headline : headlines) {
            String relevanceIcon = headline.relevanceScore > 5 ? "🔥" : "📊";
            
            html.append("        <div class=\"news-item\">\n")
                .append("            <div class=\"news-title\">").append(relevanceIcon).append(" ").append(headline.title).append("</div>\n")
                .append("            <p>").append(headline.description).append("</p>\n")
                .append("            <div class=\"news-meta\">\n")
                .append("                <strong>Source:</strong> ").append(headline.source).append(" | \n")
                .append("                <strong>Published:</strong> ").append(formatPublishTime(headline.publishedAt)).append(" | \n")
                .append("                <a href=\"").append(headline.url).append("\" target=\"_blank\">Read More</a>\n")
                .append("            </div>\n")
                .append("        </div>\n");
        }

        html.append("    </div>\n");

        // Footer
        html.append("    <div class=\"footer\">\n")
            .append("        <p><em>Generated automatically by GitHub Actions | Data from Yahoo Finance & NewsAPI</em></p>\n")
            .append("        <p><em>Disclaimer: This report is for informational purposes only and should not be considered investment advice.</em></p>\n")
            .append("    </div>\n");

        html.append("</body>\n")
            .append("</html>");

        return html.toString();
    }

    private String saveReportToFile(String htmlContent) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "reports/morning_report_" + timestamp + ".html";
        
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(htmlContent);
        }
        
        logger.info("Report saved to: {}", filename);
        return filename;
    }

    // Helper methods
    private String analyzeMarketSentiment(List<MarketData> marketData) {
        if (marketData.isEmpty()) return "Neutral";
        
        long positiveCount = marketData.stream().mapToLong(d -> d.change > 0 ? 1 : 0).sum();
        double positiveRatio = (double) positiveCount / marketData.size();
        
        if (positiveRatio > 0.6) return "Positive";
        else if (positiveRatio < 0.4) return "Negative";
        else return "Mixed";
    }

    private String getTopMover(List<MarketData> marketData) {
        if (marketData.isEmpty()) return "N/A";
        
        return marketData.stream()
                .max(Comparator.comparing(d -> Math.abs(d.changePercent)))
                .map(d -> String.format("%s (%+.2f%%)", d.symbol, d.changePercent))
                .orElse("N/A");
    }

    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private String formatVolume(long volume) {
        if (volume >= 1_000_000_000) {
            return String.format("%.1fB", volume / 1_000_000_000.0);
        } else if (volume >= 1_000_000) {
            return String.format("%.1fM", volume / 1_000_000.0);
        } else if (volume >= 1_000) {
            return String.format("%.1fK", volume / 1_000.0);
        } else {
            return String.valueOf(volume);
        }
    }

    private String formatPublishTime(String publishedAt) {
        try {
            // Parse ISO date and format to readable time
            LocalDateTime dateTime = LocalDateTime.parse(publishedAt.replace("Z", ""));
            return dateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return publishedAt;
        }
    }
}
