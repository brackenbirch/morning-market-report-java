package com.marketreport;

public class NewsHeadline {
    public final String title;
    public final String description;
    public final String source;
    public final String publishedAt;
    public final String url;
    public final int relevanceScore;

    public NewsHeadline(String title, String description, String source, 
                       String publishedAt, String url, int relevanceScore) {
        this.title = title;
        this.description = description;
        this.source = source;
        this.publishedAt = publishedAt;
        this.url = url;
        this.relevanceScore = relevanceScore;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (Score: %d)", source, title, relevanceScore);
    }
}
