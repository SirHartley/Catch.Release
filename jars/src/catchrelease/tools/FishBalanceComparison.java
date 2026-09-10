package catchrelease.tools;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

import static catchrelease.tools.FishBalance.*;
import static catchrelease.tools.SimulatedAngler.Skill;

final class FishBalanceComparison extends JPanel {

    final FishBalancePanel owner;
    final Map<String, Spec> checkpoints = new LinkedHashMap<>();
    final JComboBox<String> baseline = new JComboBox<>(new String[]{"Saved fish.csv"});
    final DefaultTableModel model = new DefaultTableModel(new String[]{"Angler", "Before %", "After %", "Change pp",
            "Before median s", "After median s", "New catches", "Lost catches"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    final JTextArea notes = new JTextArea();
    Request beforeRequest;
    Request afterRequest;
    Result before;
    Result after;

    FishBalanceComparison(FishBalancePanel owner) {
        super(new BorderLayout(4, 4));
        this.owner = owner;
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        owner.control(controls, "Baseline", baseline, "Saved CSV values, or a named snapshot captured before experimenting. Checkpoints retain the original movement and all six fish values.");
        owner.button(controls, "Capture checkpoint", "Name a snapshot of the selected fish's current values. Does not save or change fish.csv.", this::capture);
        owner.button(controls, "Compare selected fish", "Run before and after with identical seeds, attempt counts, equipment and time limit. Existing exact results are reused.", this::compare);
        add(controls, BorderLayout.NORTH);
        JTable table = new JTable(model);
        table.setRowHeight(27);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setPreferredSize(new Dimension(800, 120));
        owner.help.accept(table, "Change pp is a percentage-point difference, not relative percent. New/lost catches compare outcomes on matching seeds. Time medians use each side's successes, which may be different attempts.");
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        owner.help.accept(notes, "Each side keeps its settings snapshot. A stale warning means the current controls no longer match the displayed comparison. Check uncertainty and sample count before drawing conclusions.");
        JPanel content = new JPanel(new BorderLayout());
        content.add(scroll, BorderLayout.NORTH);
        content.add(new JScrollPane(notes));
        add(content);
        refresh();
    }

    void capture() {
        String name = JOptionPane.showInputDialog(this, "Checkpoint name");
        if (name == null || name.isBlank()) return;
        name = name.trim();
        if (name.equals("Saved fish.csv") || checkpoints.containsKey(name)) throw new IllegalArgumentException("Use a new checkpoint name.");
        checkpoints.put(name, Spec.of(owner.selected(), false));
        baseline.addItem(name);
        baseline.setSelectedItem(name);
    }

    void compare() {
        if (owner.worker != null) throw new IllegalStateException("Wait for the active run or cancel it first.");
        FishTuningSheet.Row row = owner.selected();
        Spec spec = baseline.getSelectedIndex() == 0 ? Spec.of(row, true) : checkpoints.get(baseline.getSelectedItem());
        if (spec == null || !spec.id().equals(row.id)) throw new IllegalArgumentException("That checkpoint belongs to a different fish.");
        beforeRequest = new Request(spec, owner.setup.get(), owner.plan());
        afterRequest = owner.request(row);
        before = after = null;
        owner.start(List.of(beforeRequest, afterRequest), result -> {
            if (result.request().equals(beforeRequest)) before = result;
            if (result.request().equals(afterRequest)) {
                after = result;
                owner.results.put(row.id, result);
            }
            refresh();
        });
        refresh();
    }

    void refresh() {
        model.setRowCount(0);
        if (afterRequest == null) { notes.setText("Select a fish in the results table, choose a baseline, then compare. Nothing is applied or saved."); return; }
        boolean stale = !afterRequest.equals(owner.request(owner.selected()));
        StringBuilder text = new StringBuilder(afterRequest.fish().name() + (stale ? " — STALE: current selection or settings differ" : "")
                + "\n" + afterRequest.setup() + "\n" + afterRequest.plan().attempts() + " attempts per angler, first seed "
                + afterRequest.plan().seed() + ", limit " + afterRequest.plan().seconds() + "s.\n");
        text.append("Movement: ").append(beforeRequest.fish().motion()).append(" → ").append(afterRequest.fish().motion()).append("\n");
        for (FishTuningSheet.Field field : FishTuningSheet.Field.values()) {
            text.append(field.label).append(": ").append(value(beforeRequest.fish().values().get(field.ordinal()))).append(" → ")
                    .append(value(afterRequest.fish().values().get(field.ordinal()))).append("   ");
        }
        text.append("\n\n");
        if (before == null || after == null) text.append("Waiting for both completed snapshots. Cancelled or failed runs do not count as a comparison.");
        else for (Skill skill : SKILLS) {
            Stats a = before.skills().get(skill), b = after.skills().get(skill);
            int improved = 0, worsened = 0;
            for (int i = 0; i < a.attempts().size(); i++) {
                boolean oldCatch = a.attempts().get(i).outcome() == Outcome.CAUGHT;
                boolean newCatch = b.attempts().get(i).outcome() == Outcome.CAUGHT;
                if (!oldCatch && newCatch) improved++;
                if (oldCatch && !newCatch) worsened++;
            }
            model.addRow(new Object[]{skill, number(a.catchRate()), number(b.catchRate()), number(b.catchRate() - a.catchRate()),
                    number(a.time(0.5)), number(b.time(0.5)), improved, worsened});
            text.append(skill).append(": before 95% interval ").append(number(a.confidence(false))).append("–").append(number(a.confidence(true)))
                    .append("%; after ").append(number(b.confidence(false))).append("–").append(number(b.confidence(true))).append("%.")
                    .append(" Timeouts ").append(a.count(Outcome.TIMEOUT)).append(" → ").append(b.count(Outcome.TIMEOUT)).append(".\n");
        }
        text.append("\nThe seed list is paired, but changed movement can change when random draws occur. These intervals describe each catch rate, not significance of the difference. Confirm small differences with more attempts and a new seed list.");
        notes.setText(text.toString());
        notes.setCaretPosition(0);
    }
}
