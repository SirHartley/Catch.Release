package catchrelease.tools;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

import static catchrelease.tools.FishBalance.*;
import static catchrelease.tools.FishTuningSheet.Field;

final class FishBalanceExperiments extends JPanel {

    final FishBalancePanel owner;
    final JComboBox<Field> field = new JComboBox<>(Field.values());
    final JSpinner low = new JSpinner(new SpinnerNumberModel(0.8, 0d, 10000d, 0.05));
    final JSpinner high = new JSpinner(new SpinnerNumberModel(1.2, 0d, 10000d, 0.05));
    final JSpinner points = new JSpinner(new SpinnerNumberModel(5, 2, 21, 1));
    final DefaultTableModel model = new DefaultTableModel(new String[]{"Value", "Beginner %", "Regular %", "Skilled %",
            "Median s", "P90 s", "Timeouts", "Flags"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
        @Override
        public Class<?> getColumnClass(int column) { return column == 7 ? String.class : Double.class; }
    };
    final JTable table = new JTable(model);
    final FishBalanceChart chart = new FishBalanceChart();
    final JTextArea status = new JTextArea("Choose a field and range; experiments never apply themselves.", 3, 60);
    final Map<Request, Result> results = new LinkedHashMap<>();
    List<Request> requests = List.of();
    Spec original;
    Field testedField;
    String rangeFish;

    FishBalanceExperiments(FishBalancePanel owner) {
        super(new BorderLayout(4, 4));
        this.owner = owner;
        field.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof Field f ? f.label : value, index, selected, focus);
            }
        });
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        low.setPreferredSize(new Dimension(80, 25));
        high.setPreferredSize(new Dimension(80, 25));
        owner.control(controls, "Field", field, "Vary one field while all other fish values and the test setup stay fixed.");
        owner.control(controls, "From", low, "Lowest candidate value, inclusive. Must be within this fish field's allowed range.");
        owner.control(controls, "To", high, "Highest candidate value, inclusive. Candidates are evenly spaced.");
        owner.control(controls, "Steps", points, "Every candidate is tested with all three anglers and the same seeds.");
        owner.button(controls, "Run sweep", "Run a one-field experiment for the selected fish. Does not edit fish.csv or the live values.", this::run);
        owner.button(controls, "Apply selected", "Apply just this candidate field through the tuner's normal Undo history. Save fish.csv is still separate.", this::apply);
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        JPanel top = new JPanel(new BorderLayout());
        top.add(controls, BorderLayout.NORTH);
        top.add(new JScrollPane(status));
        owner.help.accept(status, "Old experiment results stay visible. Applying is blocked if the source fish or test setup has since changed. Rerun to test the new values.");
        add(top, BorderLayout.NORTH);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(25);
        table.setDefaultRenderer(Double.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
                return super.getTableCellRendererComponent(table, value instanceof Number n
                        ? table.convertColumnIndexToModel(column) == 0 ? FishBalance.value(n.doubleValue()) : number(n.doubleValue()) : "—", selected, focus, row, column);
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        owner.help.accept(table, "Rows are candidate values. Time and flags use the detail profile above. Pick a candidate, then Apply selected. Higher catch percentage is easier for that bot.");
        JTabbedPane views = new JTabbedPane();
        views.addTab("Candidates", scroll);
        views.addTab("Candidate chart", new JScrollPane(chart));
        owner.help.accept(chart, "All candidate catch rates share a fixed 0–100% scale. Look for a useful range rather than selecting whichever candidate is easiest.");
        add(views);
        field.addActionListener(event -> resetRange());
        resetRange();
    }

    void resetRange() {
        FishTuningSheet.Row row = owner.selected();
        rangeFish = row.id;
        Field selected = (Field) field.getSelectedItem();
        double value = row.values[selected.ordinal()];
        double step = Math.max(0.1, Math.abs(value) * 0.2);
        low.setValue(Math.max(selected.min, value - step));
        high.setValue(Math.min(Math.max(selected.max, row.saved[selected.ordinal()]), value + step));
    }

    static List<Request> candidates(Spec original, Setup setup, Plan plan, Field field, double low, double high, int count, double maximum) {
        if (!Double.isFinite(low) || !Double.isFinite(high) || low < field.min || high > maximum || low >= high || count < 2 || count > 21) {
            throw new IllegalArgumentException("Choose an increasing range within " + field.min + "–" + maximum + ", with 2–21 steps.");
        }
        List<Request> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) candidates.add(new Request(original.with(field, low + (high - low) * i / (count - 1)), setup, plan));
        return candidates;
    }

    void run() {
        if (owner.worker != null) throw new IllegalStateException("Wait for the active run or cancel it first.");
        FishTuningSheet.Row row = owner.selected();
        Spec source = Spec.of(row, false);
        Field selected = (Field) field.getSelectedItem();
        List<Request> candidates = candidates(source, owner.setup.get(), owner.plan(), selected, ((Number) low.getValue()).doubleValue(),
                ((Number) high.getValue()).doubleValue(), ((Number) points.getValue()).intValue(), Math.max(selected.max, row.saved[selected.ordinal()]));
        original = source;
        testedField = selected;
        requests = candidates;
        results.clear();
        owner.start(requests, result -> { results.put(result.request(), result); refresh(); });
        refresh();
    }

    void apply() {
        if (owner.worker != null) throw new IllegalStateException("Wait for the experiment to finish or cancel it first.");
        int selected = table.getSelectedRow();
        if (selected < 0) throw new IllegalArgumentException("Select a completed candidate first.");
        if (!sourceUnchanged()) throw new IllegalArgumentException("The source fish changed. Run the experiment again before applying.");
        if (!contextUnchanged()) throw new IllegalArgumentException("The test settings changed. Run the experiment again before applying.");
        owner.apply.accept(completed().get(table.convertRowIndexToModel(selected)).request().fish(), testedField);
    }

    boolean sourceUnchanged() {
        return original != null && owner.sheet.fish.stream().anyMatch(row -> row.id.equals(original.id()) && Spec.of(row, false).equals(original));
    }

    boolean contextUnchanged() {
        return !requests.isEmpty() && requests.get(0).setup().equals(owner.setup.get()) && requests.get(0).plan().equals(owner.plan());
    }

    List<Result> completed() {
        return requests.stream().filter(results::containsKey).map(results::get).toList();
    }

    void refresh() {
        if (!owner.selected().id.equals(rangeFish)) resetRange();
        if (original == null) return;
        model.setRowCount(0);
        List<Result> completed = completed();
        List<Result> chartRows = new ArrayList<>();
        for (Result result : completed) {
            Stats stats = result.skills().get(owner.profile());
            double value = result.request().fish().values().get(testedField.ordinal());
            model.addRow(new Object[]{value, result.skills().get(SKILLS.get(0)).catchRate(), result.skills().get(SKILLS.get(1)).catchRate(),
                    result.skills().get(SKILLS.get(2)).catchRate(), FishBalancePanel.finite(stats.time(0.5)), FishBalancePanel.finite(stats.time(0.9)),
                    (double) stats.count(Outcome.TIMEOUT), stats.flags()});
            Spec labelled = new Spec(original.id(), testedField.label + " " + value(value), original.rarity(), original.motion(), result.request().fish().values());
            chartRows.add(new Result(new Request(labelled, result.request().setup(), result.request().plan()), result.skills()));
        }
        chart.setResults(chartRows, owner.profile(), false);
        status.setText(original.name() + " | " + testedField.label + " | " + completed.size() + "/" + requests.size()
                + " candidates | " + owner.profile() + (sourceUnchanged() ? "" : " | STALE: source fish changed")
                + (contextUnchanged() ? "" : " | STALE: test settings changed")
                + "\n" + requests.get(0).setup() + " | " + requests.get(0).plan());
        status.setCaretPosition(0);
    }
}
