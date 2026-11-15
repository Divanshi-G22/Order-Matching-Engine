package com.exchange;

import com.exchange.engine.MatchingEngine;
import com.exchange.exception.InvalidOrderException;
import com.exchange.model.Order;
import com.exchange.model.Side;
import com.exchange.model.Trade;
import com.exchange.io.TradeLogger;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        MatchingEngine engine = new MatchingEngine();
        TradeLogger logger = new TradeLogger("trades.csv");
        engine.submit(Order.limit("INFY", Side.SELL, 101, 50));   // order #1
        engine.submit(Order.limit("INFY", Side.SELL, 101, 30));   // order #2
        engine.submit(Order.limit("INFY", Side.SELL, 102, 40));   // order #3

        System.out.println("=== Book before the buy ===");
        System.out.println(engine.getBook("INFY").print());

        System.out.println("=== Submitting BUY 100 @ 102 ===");
        List<Trade> trades = engine.submit(Order.limit("INFY", Side.BUY, 102, 100));
        trades.forEach(System.out::println);
        logger.log(trades);
        System.out.println("\n=== Book after the buy ===");
        System.out.println(engine.getBook("INFY").print());

        engine.submit(Order.limit("INFY", Side.BUY, 99, 25));
        System.out.println("=== After resting BUY 25 @ 99 ===");
        System.out.println(engine.getBook("INFY").print());

        try {
            Order.limit("INFY", Side.BUY, 100, 0);
        } catch (InvalidOrderException e) {
            System.out.println("Rejected as expected: " + e.getMessage());
        }
    }
}