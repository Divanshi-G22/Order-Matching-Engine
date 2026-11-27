# Order Matching Engine (Stock Exchange Simulator)

A core Java simulator of how an exchange matches buy and sell orders using price-time priority.

## Features
- Limit and market orders, with partial fills
- Price priority (best price first) and time priority (first come, first served)
- Order cancellation and multiple stock symbols
- Trade log saved to `trades.csv`
- Swing dashboard with live order book, trade table, and a random order simulator
- 12 JUnit tests covering the matching rules

## How it works
Bids and asks are stored in `TreeMap<Long, Deque<Order>>`: the map orders prices, and a FIFO queue at each price keeps time priority. Prices are stored as `long` to avoid floating-point errors. Trades execute at the resting order's price. The dashboard listens to the engine through `EngineListener`, so the engine has no UI code.

## Requirements
Java JDK 17 or later (check with `java -version`).

## Run the dashboard
From the project folder:

```
javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
java -cp out com.exchange.ui.Dashboard
```

(On Mac/Linux, compile with `javac -d out $(find src -name "*.java")`.)

Try it: click **Load sample orders**, then submit BUY 100 @ 102 to see a trade sweep two price levels. Click **Start simulator** for live random orders.

## Run the tests
Download `junit-platform-console-standalone-1.10.2.jar` from Maven Central into a folder named `lib`, then:

```
javac -cp "out;lib/junit-platform-console-standalone-1.10.2.jar" -d out-test test/com/exchange/engine/MatchingEngineTest.java
java -jar lib/junit-platform-console-standalone-1.10.2.jar execute --class-path "out;out-test" --select-class com.exchange.engine.MatchingEngineTest
```

## Project structure
```
src/src/com/exchange/
  model/       Order, Trade, Side, OrderType, OrderStatus
  engine/      MatchingEngine, OrderBook, EngineListener
  exception/   InvalidOrderException, OrderNotFoundException
  io/          TradeLogger (writes trades.csv)
  ui/          Dashboard (Swing)
test/          JUnit tests
```
