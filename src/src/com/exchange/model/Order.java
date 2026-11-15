package com.exchange.model;

import com.exchange.exception.InvalidOrderException;

public class Order {
    private static long nextId = 1;

    public final long id;
    public final String symbol;
    public final Side side;
    public final OrderType type;
    public final long price;      // in paise; ignored for MARKET
    public final int quantity;
    private int remaining;
    private OrderStatus status = OrderStatus.NEW;

    public Order(String symbol, Side side, OrderType type, long price, int quantity) {
        if (symbol == null || symbol.trim().isEmpty())
            throw new InvalidOrderException("Symbol must not be empty");
        if (side == null || type == null)
            throw new InvalidOrderException("Side and type are required");
        if (quantity <= 0)
            throw new InvalidOrderException("Quantity must be positive");
        if (type == OrderType.LIMIT && price <= 0)
            throw new InvalidOrderException("Limit price must be positive");

        this.id = nextId++;
        this.symbol = symbol.trim().toUpperCase();
        this.side = side;
        this.type = type;
        this.price = (type == OrderType.MARKET) ? 0 : price;
        this.quantity = quantity;
        this.remaining = quantity;
    }

    public static Order limit(String symbol, Side side, long price, int quantity) {
        return new Order(symbol, side, OrderType.LIMIT, price, quantity);
    }

    public static Order market(String symbol, Side side, int quantity) {
        return new Order(symbol, side, OrderType.MARKET, 0, quantity);
    }

    public int getRemaining() { return remaining; }
    public OrderStatus getStatus() { return status; }

    public void fill(int qty) {
        if (qty <= 0 || qty > remaining)
            throw new IllegalArgumentException("Invalid fill quantity " + qty);
        remaining -= qty;
        status = (remaining == 0) ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    public void cancel() {
        status = OrderStatus.CANCELLED;
    }

    @Override
    public String toString() {
        String p = (type == OrderType.MARKET) ? "MKT" : String.valueOf(price);
        return "#" + id + " " + side + " " + remaining + "/" + quantity + " " + symbol
                + " @ " + p + " [" + status + "]";
    }
}