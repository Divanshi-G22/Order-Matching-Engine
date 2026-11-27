package com.exchange.ui;

import com.exchange.engine.EngineListener;
import com.exchange.engine.MatchingEngine;
import com.exchange.engine.OrderBook;
import com.exchange.exception.InvalidOrderException;
import com.exchange.exception.OrderNotFoundException;
import com.exchange.io.TradeLogger;
import com.exchange.model.Order;
import com.exchange.model.OrderType;
import com.exchange.model.Side;
import com.exchange.model.Trade;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.table.JTableHeader;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Swing dashboard for the matching engine.
 * It only displays what the engine reports through EngineListener and sends
 * the user's orders to the engine. All matching logic stays in the engine.
 */
public class Dashboard extends JFrame implements EngineListener {
    private static final String[] SYMBOLS = {"INFY", "TCS", "RELIANCE", "HDFCBANK"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Small palette: one dark header, green for buy, red for sell, soft greys elsewhere.
    private static final Color PAGE = new Color(0xF5F6FA);
    private static final Color NAVY = new Color(0x1E2A3A);
    private static final Color BUY = new Color(0x1E9E5A);
    private static final Color SELL = new Color(0xD64545);
    private static final Color BUY_TINT = new Color(0xF0FAF4);
    private static final Color SELL_TINT = new Color(0xFDF2F2);
    private static final Color GREY_BTN = new Color(0xE5E7EB);
    private static final Color GRID = new Color(0xE5E7EB);
    private static final Color TEXT_MUTED = new Color(0x4B5563);

    // Starting prices for the random order simulator (used until the first trade sets a last price).
    private static final Map<String, Long> BASE_PRICE = new HashMap<>();
    static {
        BASE_PRICE.put("INFY", 100L);
        BASE_PRICE.put("TCS", 250L);
        BASE_PRICE.put("RELIANCE", 180L);
        BASE_PRICE.put("HDFCBANK", 150L);
    }

    private final MatchingEngine engine = new MatchingEngine();
    private final TradeLogger logger = new TradeLogger("trades.csv");
    private final Map<String, Long> lastPrice = new HashMap<>();
    private final Map<String, Long> volume = new HashMap<>();

    private final Random random = new Random();
    private final Timer simTimer = new Timer(250, e -> simulateOrder());
    private final JButton simButton = new JButton("Start simulator");
    private final JComboBox<String> speedBox = new JComboBox<>(new String[]{"Slow", "Normal", "Fast"});

    private final JComboBox<String> symbolBox = new JComboBox<>(SYMBOLS);
    private final JLabel statsLabel = new JLabel();
    private final JComboBox<Side> sideBox = new JComboBox<>(Side.values());
    private final JComboBox<OrderType> typeBox = new JComboBox<>(OrderType.values());
    private final JTextField priceField = new JTextField("100", 8);
    private final JTextField qtyField = new JTextField("10", 8);
    private final JTextField cancelField = new JTextField(8);
    private final JLabel statusLabel = new JLabel("Ready.");

    private final DefaultTableModel bidModel = newModel("Price", "Qty", "Orders (id/remaining)");
    private final DefaultTableModel askModel = newModel("Price", "Qty", "Orders (id/remaining)");
    private final DefaultTableModel tradeModel =
            newModel("Time", "Symbol", "Buy order", "Sell order", "Price", "Qty");

    public Dashboard() {
        super("Stock Exchange Simulator");
        engine.addListener(this);
        buildUi();
        refresh();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        getContentPane().setBackground(PAGE);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        top.setBackground(NAVY);
        JLabel symbolLabel = new JLabel("Symbol");
        symbolLabel.setForeground(Color.WHITE);
        statsLabel.setForeground(Color.WHITE);
        statsLabel.setFont(statsLabel.getFont().deriveFont(Font.PLAIN, 13f));
        top.add(symbolLabel);
        top.add(symbolBox);
        top.add(statsLabel);
        add(top, BorderLayout.NORTH);

        add(buildOrderPanel(), BorderLayout.WEST);

        JPanel books = new JPanel(new GridLayout(1, 2, 8, 0));
        books.setOpaque(false);
        books.add(scroll("Bids (buy orders, best first)", new JTable(bidModel), BUY, BUY_TINT));
        books.add(scroll("Asks (sell orders, best first)", new JTable(askModel), SELL, SELL_TINT));

        JScrollPane trades = scroll("Trades (newest first)", new JTable(tradeModel), NAVY, Color.WHITE);
        trades.setPreferredSize(new Dimension(0, 180));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(books, BorderLayout.CENTER);
        center.add(trades, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        add(statusLabel, BorderLayout.SOUTH);

        symbolBox.addActionListener(e -> refresh());
    }

    private JPanel buildOrderPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(titled("Order entry"));

        int row = 0;
        addRow(p, row++, "Side", sideBox);
        addRow(p, row++, "Type", typeBox);
        addRow(p, row++, "Price", priceField);
        addRow(p, row++, "Quantity", qtyField);

        JButton submit = new JButton("Submit order");
        submit.addActionListener(e -> submitOrder());
        addWide(p, row++, submit);
        // The submit button is green for BUY and red for SELL.
        Runnable recolour = () -> styleButton(submit,
                sideBox.getSelectedItem() == Side.BUY ? BUY : SELL, Color.WHITE);
        sideBox.addActionListener(e -> recolour.run());
        recolour.run();

        addRow(p, row++, "Cancel order #", cancelField);
        JButton cancel = new JButton("Cancel order");
        cancel.addActionListener(e -> cancelOrder());
        styleButton(cancel, GREY_BTN, NAVY);
        addWide(p, row++, cancel);

        JButton sample = new JButton("Load sample orders");
        sample.addActionListener(e -> loadSample());
        styleButton(sample, GREY_BTN, NAVY);
        addWide(p, row++, sample);

        speedBox.setSelectedItem("Normal");
        speedBox.addActionListener(e -> simTimer.setDelay(simDelay()));
        addRow(p, row++, "Sim speed", speedBox);
        simButton.addActionListener(e -> toggleSimulator());
        styleButton(simButton, NAVY, Color.WHITE);
        addWide(p, row++, simButton);

        // push everything to the top
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = row;
        filler.weighty = 1;
        JLabel spacer = new JLabel();
        p.add(spacer, filler);

        typeBox.addActionListener(e ->
                priceField.setEnabled(typeBox.getSelectedItem() == OrderType.LIMIT));
        return p;
    }

    private static void addRow(JPanel p, int row, String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        JLabel l = new JLabel(label);
        l.setForeground(TEXT_MUTED);
        p.add(l, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(field, c);
    }

    private static void addWide(JPanel p, int row, JComponent comp) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 3, 4);
        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(comp, c);
    }

    private static DefaultTableModel newModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static TitledBorder titled(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(GRID), title);
        b.setTitleColor(NAVY);
        b.setTitleFont(b.getTitleFont().deriveFont(Font.BOLD));
        return b;
    }

    private static JScrollPane scroll(String title, JTable table, Color textColor, Color background) {
        table.setForeground(textColor);
        table.setBackground(background);
        table.setGridColor(GRID);
        table.setRowHeight(24);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(GRID);
        table.setSelectionForeground(NAVY);
        JTableHeader header = table.getTableHeader();
        header.setBackground(Color.WHITE);
        header.setForeground(TEXT_MUTED);
        header.setReorderingAllowed(false);
        JScrollPane sp = new JScrollPane(table);
        sp.getViewport().setBackground(background);
        sp.setBorder(titled(title));
        sp.setBackground(PAGE);
        return sp;
    }

    private static void styleButton(JButton b, Color background, Color foreground) {
        b.setBackground(background);
        b.setForeground(foreground);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
    }

    // ----------------------------------------------------------- actions

    private String selectedSymbol() {
        return (String) symbolBox.getSelectedItem();
    }

    private void submitOrder() {
        try {
            String symbol = selectedSymbol();
            Side side = (Side) sideBox.getSelectedItem();
            OrderType type = (OrderType) typeBox.getSelectedItem();
            int qty = Integer.parseInt(qtyField.getText().trim());

            Order order = (type == OrderType.MARKET)
                    ? Order.market(symbol, side, qty)
                    : Order.limit(symbol, side, Long.parseLong(priceField.getText().trim()), qty);

            List<Trade> trades = engine.submit(order);
            status("Order #" + order.id + " " + order.getStatus() + ": "
                    + trades.size() + " trade(s), " + order.getRemaining() + " remaining.");
        } catch (NumberFormatException ex) {
            error("Price and quantity must be whole numbers.");
        } catch (InvalidOrderException ex) {
            error(ex.getMessage());
        }
    }

    private void cancelOrder() {
        try {
            long id = Long.parseLong(cancelField.getText().trim());
            engine.cancel(id);
            status("Order #" + id + " cancelled.");
        } catch (NumberFormatException ex) {
            error("Enter the order number to cancel (see the id in the book tables).");
        } catch (OrderNotFoundException ex) {
            error(ex.getMessage());
        }
    }

    /** Puts a few non-crossing orders in the book so the tables have something to show. */
    private void loadSample() {
        String s = selectedSymbol();
        engine.submit(Order.limit(s, Side.SELL, 101, 50));
        engine.submit(Order.limit(s, Side.SELL, 101, 30));
        engine.submit(Order.limit(s, Side.SELL, 102, 40));
        engine.submit(Order.limit(s, Side.BUY, 99, 25));
        engine.submit(Order.limit(s, Side.BUY, 98, 40));
        status("Sample orders loaded for " + s + ". Try BUY 100 @ 102 to sweep two price levels.");
    }

    // ---------------------------------------------------------- simulator

    private int simDelay() {
        String speed = (String) speedBox.getSelectedItem();
        if ("Slow".equals(speed)) return 600;
        if ("Fast".equals(speed)) return 80;
        return 250;
    }

    private void toggleSimulator() {
        if (simTimer.isRunning()) {
            simTimer.stop();
            simButton.setText("Start simulator");
            status("Simulator stopped.");
        } else {
            simTimer.setDelay(simDelay());
            simTimer.start();
            simButton.setText("Stop simulator");
            status("Simulator running on " + selectedSymbol() + "...");
        }
    }

    /** Sends one random order near the last price of the selected symbol. */
    private void simulateOrder() {
        String symbol = selectedSymbol();
        Long last = lastPrice.get(symbol);
        long reference = (last != null) ? last : BASE_PRICE.getOrDefault(symbol, 100L);

        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        int qty = 1 + random.nextInt(50);

        Order order;
        if (random.nextInt(100) < 15) {
            order = Order.market(symbol, side, qty);                       // ~15% market orders
        } else {
            long price = Math.max(1, reference + random.nextInt(7) - 3);   // within 3 of the last price
            order = Order.limit(symbol, side, price, qty);
        }
        List<Trade> trades = engine.submit(order);
        status("Simulator: order #" + order.id + " " + side + " " + qty + " -> "
                + trades.size() + " trade(s).");
    }

    private void status(String message) {
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setText(message);
    }

    private void error(String message) {
        statusLabel.setForeground(SELL);
        statusLabel.setText("Error: " + message);
        JOptionPane.showMessageDialog(this, message, "Invalid input", JOptionPane.ERROR_MESSAGE);
    }

    // ------------------------------------------------- engine callbacks

    @Override
    public void onTrade(Trade t) {
        lastPrice.put(t.getSymbol(), t.getPrice());
        volume.merge(t.getSymbol(), (long) t.getQuantity(), Long::sum);
        tradeModel.insertRow(0, new Object[]{
                t.getTimestamp().format(TIME), t.getSymbol(),
                "#" + t.getBuyOrderId(), "#" + t.getSellOrderId(),
                t.getPrice(), t.getQuantity()});
        if (tradeModel.getRowCount() > 200) tradeModel.removeRow(tradeModel.getRowCount() - 1);
        logger.log(Collections.singletonList(t));
    }

    @Override
    public void onBookChanged(String symbol) {
        if (symbol.equals(selectedSymbol())) refresh();
    }

    // ------------------------------------------------------------ refresh

    private void refresh() {
        String symbol = selectedSymbol();
        OrderBook book = engine.getBook(symbol);

        fill(bidModel, book == null ? Collections.<Object[]>emptyList() : book.levels(Side.BUY));
        fill(askModel, book == null ? Collections.<Object[]>emptyList() : book.levels(Side.SELL));

        Long bid = (book == null) ? null : book.bestBid();
        Long ask = (book == null) ? null : book.bestAsk();
        statsLabel.setText("<html>Best bid <b><font color='#7EE2A8'>" + show(bid) + "</font></b>"
                + " &nbsp;&nbsp;|&nbsp;&nbsp; Best ask <b><font color='#FF9C9C'>" + show(ask) + "</font></b>"
                + " &nbsp;&nbsp;|&nbsp;&nbsp; Last <b>" + show(lastPrice.get(symbol)) + "</b>"
                + " &nbsp;&nbsp;|&nbsp;&nbsp; Volume <b>" + volume.getOrDefault(symbol, 0L) + "</b></html>");
    }

    private static void fill(DefaultTableModel model, List<Object[]> rows) {
        model.setRowCount(0);
        for (Object[] r : rows) model.addRow(r);
    }

    private static String show(Long value) {
        return value == null ? "--" : String.valueOf(value);
    }

    // --------------------------------------------------------------- main

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Dashboard().setVisible(true));
    }
}