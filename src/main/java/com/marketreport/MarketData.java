package com.marketreport;

public class MarketData {
    public final String symbol;
    public final String name;
    public final double previousClose;
    public final double currentPrice;
    public final double change;
    public final double changePercent;
    public final long volume;
    public final double preMarketPrice;
    public final double preMarketChange;
    public final double preMarketChangePercent;

    public MarketData(String symbol, String name, double previousClose, double currentPrice, 
                     double change, double changePercent, long volume, 
                     double preMarketPrice, double preMarketChange, double preMarketChangePercent) {
        this.symbol = symbol;
        this.name = name;
        this.previousClose = previousClose;
        this.currentPrice = currentPrice;
        this.change = change;
        this.changePercent = changePercent;
        this.volume = volume;
        this.preMarketPrice = preMarketPrice;
        this.preMarketChange = preMarketChange;
        this.preMarketChangePercent = preMarketChangePercent;
    }

    @Override
    public String toString() {
        return String.format("%s: $%.2f (%+.2f%%)", symbol, currentPrice, changePercent);
    }
}
