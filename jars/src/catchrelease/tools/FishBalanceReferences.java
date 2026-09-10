package catchrelease.tools;

import catchrelease.campaign.fish.data.FishMotion;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import static catchrelease.tools.FishBalance.*;

final class FishBalanceReferences extends JPanel {

    final FishBalancePanel owner;
    final FishBalanceNotebook notebook;
    final Consumer<Setup> applySetup;
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Movement references");
    final JTree tree = new JTree(root);
    final JTextArea note = new JTextArea(4, 40);
    final JTextArea summary = new JTextArea();
    final JComboBox<String> presets = new JComboBox<>();
    final JLabel status = new JLabel();

    FishBalanceReferences(FishBalancePanel owner, Consumer<Setup> applySetup) {
        super(new BorderLayout(4, 4));
        this.owner = owner;
        this.applySetup = applySetup;
        notebook = new FishBalanceNotebook(owner.sheet.root.resolve(".fish-balancing.properties"));
        JPanel controls = new JPanel(new GridLayout(0, 1));
        JPanel refs = new JPanel(new FlowLayout(FlowLayout.LEFT));
        owner.button(refs, "Capture selected fish", "Keep a named reference snapshot with its movement mode and notes. Later fish edits do not change the reference.", this::capture);
        owner.button(refs, "Save note", "Save the note for the selected reference without replacing its fish values.", this::saveNote);
        owner.button(refs, "Compare reference", "Compare the selected fish against this reference using the same seeds and equipment. References can be different species.", this::compare);
        owner.button(refs, "Remove reference", "Remove only the selected notebook reference; never deletes a fish.", this::remove);
        controls.add(refs);
        JPanel setups = new JPanel(new FlowLayout(FlowLayout.LEFT));
        owner.control(setups, "Equipment setup", presets, "Saved tackle, indicator size, player gain/loss and rumor multiplier. Fish values are not part of equipment presets.");
        owner.button(setups, "Save current setup", "Name the current live-tuner equipment controls. Stored separately from fish.csv.", this::savePreset);
        owner.button(setups, "Load setup", "Load the selected equipment preset into the tuner. Existing results become stale if the setup differs.", () -> {
            Setup selected = notebook.presets.get(presets.getSelectedItem());
            if (selected == null) throw new IllegalArgumentException("Select a saved setup.");
            applySetup.accept(selected);
            status.setText("Loaded setup: " + presets.getSelectedItem());
        });
        controls.add(setups);
        controls.add(status);
        add(controls, BorderLayout.NORTH);
        owner.help.accept(tree, "References are grouped by the movement stored in their snapshot, even if the live fish has since changed. Select a named reference, not the group heading.");
        tree.addTreeSelectionListener(event -> showReference());
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        owner.help.accept(note, "Your judgement: what feels right about this fish? Notes are saved only by Capture selected fish or Save note.");
        summary.setEditable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        owner.help.accept(summary, "Reference values are fixed snapshots. Comparing them never overwrites the current fish.");
        JPanel text = new JPanel(new BorderLayout());
        text.add(new JScrollPane(summary));
        text.add(new JScrollPane(note), BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), text);
        split.setResizeWeight(0.4);
        split.setDividerLocation(290);
        add(split);
        reload();
        status.setText(notebook.error == null ? "References and setups are saved in .fish-balancing.properties, not fish.csv." : notebook.error);
    }

    String name(String prompt) {
        String name = JOptionPane.showInputDialog(this, prompt);
        return name == null || name.isBlank() ? null : name.trim();
    }

    String selectedName() {
        Object value = tree.getLastSelectedPathComponent();
        if (!(value instanceof DefaultMutableTreeNode node) || node.getLevel() != 2) throw new IllegalArgumentException("Select a named reference under a movement group.");
        return node.getUserObject().toString();
    }

    void capture() {
        String name = name("Reference name");
        if (name == null) return;
        if (notebook.references.containsKey(name)) throw new IllegalArgumentException("Use a new reference name.");
        save(() -> notebook.references.put(name, new FishBalanceNotebook.Reference(Spec.of(owner.selected(), false), note.getText())));
        reload();
    }

    void saveNote() {
        String name = selectedName();
        save(() -> notebook.references.put(name, new FishBalanceNotebook.Reference(notebook.references.get(name).fish(), note.getText())));
    }

    void compare() {
        Spec reference = notebook.references.get(selectedName()).fish();
        owner.comparison.compareAgainst(reference);
        owner.views.setSelectedComponent(owner.comparison);
    }

    void remove() {
        String name = selectedName();
        if (JOptionPane.showConfirmDialog(this, "Remove reference " + name + "?", "Remove reference", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        save(() -> notebook.references.remove(name));
        reload();
    }

    void savePreset() {
        String name = name("Equipment setup name");
        if (name == null) return;
        if (notebook.presets.containsKey(name)) throw new IllegalArgumentException("Use a new setup name.");
        save(() -> notebook.presets.put(name, owner.setup.get()));
        reload();
        presets.setSelectedItem(name);
    }

    void save(Runnable change) {
        Map<String, FishBalanceNotebook.Reference> references = new TreeMap<>(notebook.references);
        Map<String, Setup> presets = new TreeMap<>(notebook.presets);
        try {
            change.run();
            notebook.save();
            status.setText("Notebook saved. Fish CSV unchanged.");
        } catch (IOException | RuntimeException ex) {
            notebook.references.clear();
            notebook.references.putAll(references);
            notebook.presets.clear();
            notebook.presets.putAll(presets);
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    void reload() {
        root.removeAllChildren();
        for (FishMotion movement : FishMotion.values()) {
            DefaultMutableTreeNode group = new DefaultMutableTreeNode(movement);
            notebook.references.forEach((name, reference) -> {
                if (reference.fish().motion() == movement) group.add(new DefaultMutableTreeNode(name));
            });
            if (group.getChildCount() > 0) root.add(group);
        }
        ((DefaultTreeModel) tree.getModel()).reload();
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        Object selected = presets.getSelectedItem();
        presets.removeAllItems();
        notebook.presets.keySet().forEach(presets::addItem);
        if (selected != null && notebook.presets.containsKey(selected)) presets.setSelectedItem(selected);
    }

    void showReference() {
        try {
            FishBalanceNotebook.Reference reference = notebook.references.get(selectedName());
            Spec fish = reference.fish();
            note.setText(reference.note());
            StringBuilder text = new StringBuilder(fish.name() + " [" + fish.id() + "]\n" + fish.motion() + " / " + fish.rarity() + "\n\n");
            for (FishTuningSheet.Field field : FishTuningSheet.Field.values()) text.append(field.label).append(": ").append(value(fish.values().get(field.ordinal()))).append("\n");
            text.append("\n").append(FishBalanceAdvice.movement(fish.motion()));
            summary.setText(text.toString());
            summary.setCaretPosition(0);
        } catch (IllegalArgumentException ex) {
            summary.setText("Select a reference inside a movement group.");
        }
    }
}
