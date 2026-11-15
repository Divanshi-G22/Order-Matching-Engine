package com.exchange.engine;

import com.exchange.exception.InvalidOrderException;
import com.exchange.exception.OrderNotFoundException;
import com.exchange.model.Order;
import com.exchange.model.OrderType;
import com.exchange.model.Side;
import com.exchange.model.Trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MatchingEngine {
    private final Map<String, OrderBook> books = new HashMap<>();
    private final List<Trade> tradeLog = new ArrayList<>();
    private final Map<Long, String> restingSymbol = new HashMap<>(); // order id -> symbol

    public List<Trade> submit(Order in) {
        if (in == null) throw new InvalidOrderException("Order must not be null");

        OrderBook book = books.computeIfAbsent(in.symbol, s -> new OrderBook());
        TreeMap<Long, Deque<Order>> opposite = book.sideOf(in.side.opposite());
        List<Trade> trades = new ArrayList<>();

        while (in.getRemaining() > 0 && !opposite.isEmpty()) {
            Map.Entry<Long, Deque<Order>> level = opposite.firstEntry();
            long levelPrice = level.getKey();

            if (in.type == OrderType.LIMIT && !crosses(in, levelPrice)) break;

            Deque<Order> queue = level.getValue();
            Order resting = queue.peekFirst();
            int qty = Math.min(in.getRemaining(), resting.getRemaining());

            Order buy = (in.side == Side.BUY) ? in : resting;
            Order sell = (in.side == Side.SELL) ? in : resting;
            trades.add(new Trade(buy, sell, levelPrice, qty));

            in.fill(qty);
            resting.fill(qty);

            if (resting.getRemaining() == 0) {
                queue.pollFirst();
                book.forget(resting);
                restingSymbol.remove(resting.id);
            }
            if (queue.isEmpty()) opposite.remove(levelPrice);
        }

        if (in.getRemaining() > 0) {
            if (in.type == OrderType.LIMIT) {
                book.add(in);
                restingSymbol.put(in.id, in.symbol);
            } else {
                in.cancel();   // market order: unfilled remainder is dropped
            }
        }

        tradeLog.addAll(trades);
        return trades;
    }

    public void cancel(long orderId) {
        String symbol = restingSymbol.remove(orderId);
        if (symbol == null || !books.get(symbol).cancel(orderId))
            throw new OrderNotFoundException("No resting order with id " + orderId);
    }

    private boolean crosses(Order in, long levelPrice) {
        return in.side == Side.BUY ? in.price >= levelPrice : in.price <= levelPrice;
    }

    public OrderBook getBook(String symbol) {
        return books.get(symbol.trim().toUpperCase());
    }

    public List<Trade> getTradeLog() {
        return Collections.unmodifiableList(tradeLog);
    }
}