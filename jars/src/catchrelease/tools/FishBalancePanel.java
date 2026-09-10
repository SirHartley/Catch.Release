package catchrelease.tools;

import catchrelease.campaign.fish.data.FishMotion;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;

import static catchrelease.tools.FishBalance.*;
import static catchrelease.tools.SimulatedAngler.Skill;

final class FishBalancePanel extends JPanel {

    final FishTuningSheet sheet;
    final Supplier<Setup> setup;
    final Supplier<FishTuningSheet.Row> current;
    final Consumer<String> openFish;
    final BiConsumer<JComponent, String> help;
    final Consumer<String> bottomHelp;
    final Map<String, Result> results = new HashMap<>();
    final Map<Request, Result> cache = new LinkedHashMap<>();
    final Set<String> pendingEdits = new LinkedHashSet<>();

    final JSpinner samples = new JSpinner(new SpinnerNumberModel(30, 5, 2000, 5));
    final JSpinner seed = new JSpinner(new SpinnerNumberModel(1L, 0L, Long.MAX_VALUE - 2000, 1L));
    final JSpinner limit = new JSpinner(new SpinnerNumberModel(120, 5, 600, 5));
    final JComboBox<Skill> profile = new JComboBox<>(SKILLS.toArray(Skill[]::new));
    final JTextField search = new JTextField(15);
    final JComboBox<String> movement = new JComboBox<>();
    final JComboBox<String> rarity = new JComboBox<>();
    final JCheckBox editedOnly = new JCheckBox("Edited only");
    final JCheckBox flaggedOnly = new JCheckBox("Flagged only");
    final JCheckBox auto = new JCheckBox("Auto-retest edited fish", true);
    final JComboBox<String> chartMode = new JComboBox<>(new String[]{"Catch rates", "Catch time spread"});

    final ResultTable model = new ResultTable();
    final JTable table;
    final TableRowSorter<ResultTable> sorter;
    final JTextArea details = new JTextArea(7, 60);
    final JLabel progress = new JLabel("No runs yet. All batches use normal losing and all three anglers.");
    final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    final JTabbedPane views = new JTabbedPane();
    final FishBalanceChart chart = new FishBalanceChart();
    final JTextArea behaviors = new JTextArea();
    final FishBalanceComparison comparison;
    final javax.swing.Timer editDelay = new javax.swing.Timer(600, event -> retestPending());
    SwingWorker<Void, Result> worker;
    boolean closed;

    FishBalancePanel(FishTuningSheet sheet, Supplier<Setup> setup, Supplier<FishTuningSheet.Row> current,
                     Consumer<String> openFish, BiConsumer<JComponent, String> help, Consumer<String> bottomHelp) {
        super(new BorderLayout(4, 4));
        this.sheet = sheet;
        this.setup = setup;
        this.current = current;
        this.openFish = openFish;
        this.help = help;
        this.bottomHelp = bottomHelp;
        comparison = new FishBalanceComparison(this);
        table = new JTable(model);
        sorter = new TableRowSorter<>(model);
        samples.setPreferredSize(new Dimension(65, 25));
        seed.setPreferredSize(new Dimension(90, 25));
        limit.setPreferredSize(new Dimension(65, 25));
        profile.setSelectedItem(Skill.REGULAR);
        movement.addItem("All movements");
        for (FishMotion value : FishMotion.values()) movement.addItem(value.name());
        rarity.addItem("All rarities");
        sheet.fish.stream().map(row -> row.rarity).distinct().forEach(rarity::addItem);
        editDelay.setRepeats(false);
        build();
        refresh();
    }

    private void build() {
        JPanel top = new JPanel(new GridLayout(0, 1));
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT));
        control(settings, "Attempts / angler", samples, "30 is a quick screen, not a precise estimate. Use 100–500 to confirm a suspected difference.");
        control(settings, "First seed", seed, "Each fish and angler gets the same seed list. This makes reruns repeatable.");
        control(settings, "Time limit (s)", limit, "An unfinished attempt is a timeout, never silently a loss. Timeouts count in the total attempts.");
        control(settings, "Detail profile", profile, "Catch-rate columns show all three anglers. Other columns, time charts and movement summaries use this profile.");
        top.add(settings);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        control(filters, "Find", search, "Case-insensitive name or ID filter. Run visible tests exactly this filtered list.");
        control(filters, "", movement, "Filter by the fish's declared movement. MIXED stays its own group.");
        control(filters, "", rarity, "Compare within a rarity, or leave all rarities visible.");
        filters.add(editedOnly);
        filters.add(flaggedOnly);
        help.accept(editedOnly, "Only fish whose current values differ from fish.csv.");
        help.accept(flaggedOnly, "Only results with small samples, timeouts, wide catch times, early losses or near-catch losses. Flags are clues, not a bad-fish verdict.");
        top.add(filters);
        button(actions, "Run visible", "Run the filtered fish list in the background. Reuse identical cached runs; unchanged fish need no new simulations.",
                () -> runRows(visibleRows()));
        button(actions, "Test selected fish", "Test the selected table row, or the fish open in the tuner if no row is selected.",
                () -> runRows(List.of(selected())));
        button(actions, "Cancel", "Stop pending work. Keep completed fish; incomplete fish are not published.", this::cancel);
        button(actions, "Open in tuner", "Open the selected fish for editing without changing its values.", () -> openFish.accept(selected().id));
        actions.add(auto);
        help.accept(auto, "After an edit settles, retest only that previously tested fish. Edits made during a batch are queued; old results stay labelled stale.");
        top.add(actions);
        top.add(progress);
        help.accept(progress, "Runs use snapshots of fish values and the current tackle/player/rumor controls. Changing controls marks incompatible results stale.");
        add(top, BorderLayout.NORTH);

        table.setRowSorter(sorter);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(25);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new Cells());
        table.setDefaultRenderer(Double.class, new Cells());
        table.setDefaultRenderer(Integer.class, new Cells());
        for (int i = 0; i < model.getColumnCount(); i++) table.getColumnModel().getColumn(i).setPreferredWidth(i == 0 ? 170 : i == 14 ? 240 : 108);
        sorter.setSortKeys(List.of(new RowSorter.SortKey(15, SortOrder.ASCENDING), new RowSorter.SortKey(4, SortOrder.ASCENDING)));
        table.getSelectionModel().addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) showDetails(); });
        help.accept(table, "Click column headings to sort numerically. Catch percentages include timeouts in their denominator. Select a fish for sample counts and interpretation below. Orange rows need a new run.");
        help.accept(table.getTableHeader(), "Catch %: higher is easier. Catch times and spread use successful attempts only. Coverage is actual time in the indicator. Gap is the longest uninterrupted miss.");
        table.getTableHeader().addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int column = table.columnAtPoint(event.getPoint());
                if (column >= 0) showColumnHelp(table.convertColumnIndexToModel(column));
            }
        });
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        views.addTab("Fish results", tableScroll);
        JPanel plots = new JPanel(new BorderLayout());
        plots.add(chartMode, BorderLayout.NORTH);
        plots.add(new JScrollPane(chart));
        help.accept(chartMode, "Catch rates: three bars per fish, fixed 0–100% scale. Time spread: 10th–90th percentile of successful catches, with median. Few successes give weak estimates.");
        help.accept(chart, "Charts follow filters and table sort, and exclude stale results. Colours distinguish anglers, not fish rarity.");
        views.addTab("Charts", plots);
        behaviors.setEditable(false);
        behaviors.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        help.accept(behaviors, "Movement groups use only fresh visible fish. Catch rate is the mean per-fish rate; min/max exposes differences hidden by an average. This is not a controlled comparison of movement alone.");
        views.addTab("Movement groups", new JScrollPane(behaviors));
        views.addTab("Before / after", comparison);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        help.accept(details, "Select a fish above. Results describe these bots under this setup, not a measured percentage of human players.");
        JScrollPane detailScroll = new JScrollPane(details);
        detailScroll.setMinimumSize(new Dimension(200, 125));
        views.setMinimumSize(new Dimension(200, 120));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, views, detailScroll);
        split.setResizeWeight(0.7);
        split.setDividerLocation(300);
        add(split);
        DocumentListener filterChanges = new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { refresh(); }
            public void removeUpdate(DocumentEvent event) { refresh(); }
            public void changedUpdate(DocumentEvent event) { refresh(); }
        };
        search.getDocument().addDocumentListener(filterChanges);
        for (JComboBox<?> box : List.of(movement, rarity, profile, chartMode)) box.addActionListener(event -> refresh());
        editedOnly.addActionListener(event -> refresh());
        flaggedOnly.addActionListener(event -> refresh());
        for (JSpinner spinner : List.of(samples, seed, limit)) spinner.addChangeListener(event -> refresh());
        sorter.addRowSorterListener(event -> updateCharts());
    }

    void control(JPanel panel, String label, JComponent component, String description) {
        if (!label.isEmpty()) panel.add(new JLabel(label));
        panel.add(component);
        help.accept(component, description);
    }

    JButton button(JPanel panel, String label, String description, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> {
            try { action.run(); }
            catch (RuntimeException ex) { progress.setText(ex.getMessage()); }
        });
        help.accept(button, description);
        panel.add(button);
        return button;
    }

    Plan plan() {
        return new Plan(((Number) samples.getValue()).intValue(), ((Number) seed.getValue()).longValue(),
                ((Number) limit.getValue()).intValue());
    }

    Request request(FishTuningSheet.Row row) {
        return new Request(Spec.of(row, false), setup.get(), plan());
    }

    Skill profile() {
        return (Skill) profile.getSelectedItem();
    }

    FishTuningSheet.Row selected() {
        int index = table.getSelectedRow();
        return index < 0 ? current.get() : sheet.fish.get(table.convertRowIndexToModel(index));
    }

    List<FishTuningSheet.Row> visibleRows() {
        List<FishTuningSheet.Row> rows = new ArrayList<>();
        for (int i = 0; i < table.getRowCount(); i++) rows.add(sheet.fish.get(table.convertRowIndexToModel(i)));
        return rows;
    }

    boolean fresh(FishTuningSheet.Row row, Result result) {
        return result != null && result.request().equals(request(row));
    }

    void runRows(List<FishTuningSheet.Row> rows) {
        if (worker != null) { progress.setText("A run is active. Cancel it first, or wait for it to finish."); return; }
        List<Request> requests = rows.stream().map(this::request).toList();
        start(requests, result -> results.put(result.request().fish().id(), result));
    }

    void start(List<Request> requests, Consumer<Result> received) {
        if (worker != null || closed) return;
        List<Request> missing = new ArrayList<>();
        for (Request request : new LinkedHashSet<>(requests)) {
            Result hit = cache.get(request);
            if (hit == null) missing.add(request);
            else received.accept(hit);
        }
        refresh();
        if (missing.isEmpty()) { progress.setText("Results already cached for these exact values and settings."); return; }
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        progress.setText("Running " + missing.size() + " fish snapshots on " + threads + " workers…");
        worker = new SwingWorker<>() {
            int finished;

            @Override
            protected Void doInBackground() throws Exception {
                batch(missing, threads, result -> publish(result));
                return null;
            }

            @Override
            protected void process(List<Result> chunks) {
                if (worker != this || isCancelled() || closed) return;
                for (Result result : chunks) {
                    cache.put(result.request(), result);
                    while (cache.size() > 512) cache.remove(cache.keySet().iterator().next());
                    received.accept(result);
                    finished++;
                }
                refresh();
                progress.setText("Completed " + finished + " / " + missing.size() + " fish snapshots.");
            }

            @Override
            protected void done() {
                if (worker != this) return;
                try {
                    get();
                    progress.setText("Finished. " + finished + " new fish snapshots; unchanged results reused.");
                } catch (CancellationException ex) {
                    progress.setText("Cancelled. Completed fish kept.");
                } catch (Exception ex) {
                    progress.setText("Run failed: " + (ex.getCause() == null ? ex : ex.getCause()));
                } finally {
                    worker = null;
                    refresh();
                    if (!closed && !pendingEdits.isEmpty()) editDelay.restart();
                }
            }
        };
        worker.execute();
    }

    void edited(String id) {
        refresh();
        if (auto.isSelected() && results.containsKey(id)) {
            pendingEdits.add(id);
            editDelay.restart();
        }
    }

    void retestPending() {
        if (closed || worker != null) return;
        List<FishTuningSheet.Row> rows = sheet.fish.stream().filter(row -> pendingEdits.contains(row.id)).toList();
        pendingEdits.clear();
        if (auto.isSelected() && !rows.isEmpty()) runRows(rows);
    }

    void cancel() {
        pendingEdits.clear();
        editDelay.stop();
        if (worker != null) worker.cancel(true);
    }

    void close() {
        closed = true;
        cancel();
    }

    void refresh() {
        String selectedId = table.getSelectedRow() < 0 ? null : selected().id;
        model.fireTableDataChanged();
        sorter.setRowFilter(new RowFilter<>() {
            public boolean include(Entry<? extends ResultTable, ? extends Integer> entry) {
                FishTuningSheet.Row row = sheet.fish.get(entry.getIdentifier());
                Result result = results.get(row.id);
                String query = search.getText().trim().toLowerCase(Locale.ROOT);
                return (row.id + " " + row.name).toLowerCase(Locale.ROOT).contains(query)
                        && (movement.getSelectedIndex() == 0 || row.motion.name().equals(movement.getSelectedItem()))
                        && (rarity.getSelectedIndex() == 0 || row.rarity.equals(rarity.getSelectedItem()))
                        && (!editedOnly.isSelected() || row.changed())
                        && (!flaggedOnly.isSelected() || result != null && !result.skills().get(profile()).flags().isEmpty());
            }
        });
        if (selectedId != null) {
            for (int i = 0; i < sheet.fish.size(); i++) if (sheet.fish.get(i).id.equals(selectedId)) {
                int visible = table.convertRowIndexToView(i);
                if (visible >= 0) table.setRowSelectionInterval(visible, visible);
            }
        }
        showDetails();
        updateCharts();
        comparison.refresh();
    }

    void showDetails() {
        FishTuningSheet.Row row = selected();
        Result result = results.get(row.id);
        if (result == null) { details.setText(row.name + ": not tested yet."); return; }
        Stats stats = result.skills().get(profile());
        details.setText(row.name + " — " + profile() + (fresh(row, result) ? "" : " — STALE: values or run settings changed")
                + "\n" + result.request().setup() + " | " + stats.attempts().size() + " attempts | seeds "
                + result.request().plan().seed() + " onward | timeout " + result.request().plan().seconds() + "s"
                + "\nCaught " + stats.count(Outcome.CAUGHT) + ", lost " + stats.count(Outcome.LOST) + ", timed out " + stats.count(Outcome.TIMEOUT)
                + ". Catch estimate " + number(stats.catchRate()) + "%; 95% interval " + number(stats.confidence(false)) + "–" + number(stats.confidence(true)) + "%."
                + "\nSuccessful catch times: min " + number(stats.time(0)) + "s; P10 " + number(stats.time(0.1)) + "s; median "
                + number(stats.time(0.5)) + "s; P90 " + number(stats.time(0.9)) + "s; max " + number(stats.time(1)) + "s; standard deviation " + number(stats.deviation()) + "s."
                + "\nFlags: " + (stats.flags().isEmpty() ? "none" : stats.flags())
                + ". Percentiles use successful catches only; a small number of successes makes them unreliable."
                + FishBalanceAdvice.describe(result, profile()));
        details.setCaretPosition(0);
    }

    void updateCharts() {
        List<Result> visible = visibleRows().stream().filter(row -> fresh(row, results.get(row.id))).map(row -> results.get(row.id)).toList();
        chart.setResults(visible, profile(), chartMode.getSelectedIndex() == 1);
        StringBuilder text = new StringBuilder("Fresh visible fish only. Mean catch rate weights each fish equally. Other stats pool attempts.\n\n");
        for (FishMotion type : FishMotion.values()) {
            List<Result> group = visible.stream().filter(result -> result.request().fish().motion() == type).toList();
            if (group.isEmpty()) continue;
            double[] rates = group.stream().mapToDouble(result -> result.skills().get(profile()).catchRate()).toArray();
            Stats pooled = new Stats(group.stream().flatMap(result -> result.skills().get(profile()).attempts().stream()).toList());
            text.append(String.format(Locale.ROOT, "%-9s %3d fish | %s | mean catch %5.1f%% | fish range %5.1f–%5.1f%% | median %s s | P90 %s s | timeouts %d\n",
                    type, group.size(), profile(), Arrays.stream(rates).average().orElse(0), Arrays.stream(rates).min().orElse(0),
                    Arrays.stream(rates).max().orElse(0), number(pooled.time(0.5)), number(pooled.time(0.9)), pooled.count(Outcome.TIMEOUT)));
            text.append("  ").append(FishBalanceAdvice.movement(type)).append("\n\n");
        }
        behaviors.setText(text.toString());
    }

    void showColumnHelp(int column) {
        String[] text = {"Fish name; Open in tuner edits this species.", "Declared movement; MIXED remains a separate group.", "Rarity, not a difficulty rating.",
                "Beginner catches / all attempts. Timeouts stay in the denominator.", "Regular catches / all attempts.", "Skilled catches / all attempts.",
                "Attempts per angler. More samples reduce random uncertainty.", "Median successful catch time: half the catches took longer.",
                "90th percentile: roughly one in ten successful catches took longer.", "Sample standard deviation of successful catch times; higher means more variation.",
                "Actual covered time / total time across all attempts.", "Longest uninterrupted time outside the indicator in any attempt.",
                "Total time spent on losses and timeouts divided by all attempts.", "Unfinished attempts at the time limit, not losses.", "Warnings for the detail profile; not automatic balance judgements.", "Freshness compared with current fish values, equipment, sample count, seeds and time limit."};
        bottomHelp.accept(text[column]);
    }

    final class ResultTable extends AbstractTableModel {

        final String[] columns = {"Fish", "Movement", "Rarity", "Beginner %", "Regular %", "Skilled %", "N / angler",
                "Median catch s", "P90 catch s", "Catch SD s", "Coverage %", "Worst gap s", "Wasted s / try", "Timeouts", "Flags", "Status"};

        public int getRowCount() { return sheet.fish.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int column) { return columns[column]; }
        public Class<?> getColumnClass(int column) { return column == 6 || column == 13 ? Integer.class : column >= 3 && column <= 12 ? Double.class : String.class; }

        public Object getValueAt(int index, int column) {
            FishTuningSheet.Row row = sheet.fish.get(index);
            Result result = results.get(row.id);
            if (column == 0) return row.name;
            if (column == 1) return row.motion.name();
            if (column == 2) return row.rarity;
            if (column == 15) return result == null ? "Not tested" : fresh(row, result) ? "Current" : "Stale";
            if (result == null) return null;
            Stats stats = result.skills().get(profile());
            return switch (column) {
                case 3, 4, 5 -> result.skills().get(SKILLS.get(column - 3)).catchRate();
                case 6 -> stats.attempts().size();
                case 7 -> finite(stats.time(0.5));
                case 8 -> finite(stats.time(0.9));
                case 9 -> finite(stats.deviation());
                case 10 -> stats.coverage();
                case 11 -> stats.gap();
                case 12 -> stats.failedSeconds();
                case 13 -> (int) stats.count(Outcome.TIMEOUT);
                case 14 -> stats.flags();
                default -> null;
            };
        }
    }

    static Double finite(double value) { return Double.isFinite(value) ? value : null; }

    final class Cells extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value instanceof Double d ? number(d) : value == null ? "—" : value, selected, focus, row, column);
            setHorizontalAlignment(value instanceof Number ? RIGHT : LEFT);
            if (!selected) {
                FishTuningSheet.Row fish = sheet.fish.get(table.convertRowIndexToModel(row));
                Result result = results.get(fish.id);
                setForeground(result != null && !fresh(fish, result) ? new Color(145, 80, 15) : Color.BLACK);
                int actual = table.convertColumnIndexToModel(column);
                setBackground(actual >= 3 && actual <= 5 && value instanceof Double d
                        ? new Color(255 - (int) (d * 0.6), 210 + (int) (d * 0.35), 210)
                        : row % 2 == 0 ? Color.WHITE : new Color(240, 243, 247));
            }
            return this;
        }
    }
}
