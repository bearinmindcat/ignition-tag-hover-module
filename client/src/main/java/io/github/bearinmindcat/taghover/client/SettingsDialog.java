package io.github.bearinmindcat.taghover.client;

import io.github.bearinmindcat.taghover.client.TagHoverSettings.Detail;
import io.github.bearinmindcat.taghover.client.TagHoverSettings.Group;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

/** The Tag Hover Settings window: which key, hold or tap, and what the tooltip shows. */
final class SettingsDialog {

    private static final String RECORD = "Press a key...";

    private SettingsDialog() {}

    /** Shows the window modally. Returns the chosen settings, or {@code null} if cancelled. Call on the EDT. */
    static TagHoverSettings show(Window owner, TagHoverSettings current) {
        JDialog dialog = new JDialog(owner, "Tag Hover Settings", Dialog.ModalityType.APPLICATION_MODAL);
        TagHoverSettings[] result = {null};

        JComboBox<Integer> key = new JComboBox<>(TagHoverSettings.KEY_CHOICES.toArray(new Integer[0]));
        key.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focused) {
                Object text = value instanceof Integer ? TagHoverSettings.keyName((Integer) value) : value;
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        });
        key.setMaximumRowCount(TagHoverSettings.KEY_CHOICES.size());

        JRadioButton hold = new JRadioButton();
        JRadioButton toggle = new JRadioButton();
        ButtonGroup group = new ButtonGroup();
        group.add(hold);
        group.add(toggle);

        Runnable describe = () -> {
            String name = TagHoverSettings.keyName((Integer) key.getSelectedItem());
            hold.setText("Hold " + name + ": tags show while it is down");
            toggle.setText("Tap " + name + ": tag hover stays on until you tap it again or press Esc");
            dialog.pack();
        };
        key.addActionListener(e -> describe.run());

        // One checkbox per detail the tooltip can show, in a column per group.
        Map<Detail, JCheckBox> boxes = new EnumMap<>(Detail.class);
        for (Detail d : Detail.values()) {
            boxes.put(d, new JCheckBox(d.label()));
        }
        JPanel details = detailsPanel(boxes);

        setFields(key, hold, toggle, boxes, current);

        // "Press a key...": the next key pressed becomes the hotkey, if it's one that doesn't type text.
        JButton record = new JButton(RECORD);
        JLabel feedback = new JLabel(" ") {
            @Override
            public void setText(String text) {
                // Wraps like the note below instead of widening the window.
                super.setText(text == null || text.isBlank()
                    ? " "
                    : "<html><body style='width: 330px'>" + TagReport.esc(text) + "</body></html>");
            }
        };
        feedback.setForeground(UIManager.getColor("Label.disabledForeground"));
        boolean[] recording = {false};
        Runnable stopRecording = () -> {
            recording[0] = false;
            record.setText(RECORD);
        };
        record.addActionListener(e -> {
            recording[0] = true;
            record.setText("Press the key now...");
            feedback.setText(" ");
            dialog.pack();
        });
        record.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!recording[0]) {
                    return;
                }
                e.consume(); // So Space, Enter and Esc don't also press a button or close the window.
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_ESCAPE) {
                    stopRecording.run();
                    feedback.setText(" ");
                } else if (TagHoverSettings.isAllowedKey(code)) {
                    key.setSelectedItem(code);
                    stopRecording.run();
                    feedback.setText("Key set to " + TagHoverSettings.keyName(code) + ".");
                } else {
                    feedback.setText(KeyEvent.getKeyText(code) + " can't be the key, as it types text or is taken. "
                        + "Try Ctrl, Shift, Alt, F1-F12, Insert, Pause or Scroll Lock.");
                }
                dialog.pack();
            }
        });
        record.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                stopRecording.run();
            }
        });

        // A fixed width makes the note wrap instead of stretching the window.
        JLabel note = new JLabel("<html><body style='width: 330px'>Saved for your Windows user on this computer. "
            + "Press Ctrl+Comma to open this window from any Vision client or the Designer.</body></html>");
        note.setForeground(UIManager.getColor("Label.disabledForeground"));

        JButton defaults = new JButton("Restore defaults");
        defaults.addActionListener(e -> setFields(key, hold, toggle, boxes, TagHoverSettings.DEFAULT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            EnumSet<Detail> shown = EnumSet.noneOf(Detail.class);
            boxes.forEach((d, box) -> {
                if (box.isSelected()) {
                    shown.add(d);
                }
            });
            result[0] = new TagHoverSettings(
                (Integer) key.getSelectedItem(),
                toggle.isSelected() ? TagHoverSettings.Mode.TOGGLE : TagHoverSettings.Mode.HOLD,
                shown);
            dialog.dispose();
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        JPanel keyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        keyRow.add(key);
        keyRow.add(Box.createHorizontalStrut(8));
        keyRow.add(record);

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Key:"), c);
        c.gridx = 1;
        form.add(keyRow, c);
        c.gridy = 1;
        c.insets = new Insets(0, 4, 4, 4);
        form.add(feedback, c);
        c.gridx = 0;
        c.gridy = 2;
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Mode:"), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        form.add(hold, c);
        c.gridy = 3;
        c.insets = new Insets(0, 4, 4, 4);
        form.add(toggle, c);
        c.gridx = 0;
        c.gridy = 4;
        c.insets = new Insets(10, 4, 4, 4);
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Show:"), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        form.add(details, c);
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        c.insets = new Insets(10, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(note, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        buttons.add(defaults);
        buttons.add(cancel);
        buttons.add(save);

        JPanel content = new JPanel(new java.awt.BorderLayout());
        content.add(form, java.awt.BorderLayout.CENTER);
        content.add(buttons, java.awt.BorderLayout.SOUTH);
        content.registerKeyboardAction(
            e -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(save);
        dialog.setResizable(false);
        describe.run();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true); // Blocks until closed.
        return result[0];
    }

    /** The groups side by side: bindings on the left, tag source and other on the right. */
    private static JPanel detailsPanel(Map<Detail, JCheckBox> boxes) {
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        for (Group g : Group.values()) {
            JPanel column = g == Group.BINDINGS ? left : right;
            JLabel title = new JLabel(g.title());
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            // A border, not a strut, for the gap: struts are centered and would shift the column in a BoxLayout.
            title.setBorder(BorderFactory.createEmptyBorder(column.getComponentCount() > 0 ? 10 : 0, 0, 2, 0));
            column.add(title);
            for (Detail d : Detail.values()) {
                if (d.group() == g) {
                    column.add(boxes.get(d));
                }
            }
        }
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.gridy = 0;
        c.gridx = 0;
        panel.add(left, c);
        c.gridx = 1;
        c.insets = new Insets(0, 18, 0, 0);
        panel.add(right, c);
        return panel;
    }

    private static void setFields(JComboBox<Integer> key, JRadioButton hold, JRadioButton toggle,
        Map<Detail, JCheckBox> boxes, TagHoverSettings settings) {

        key.setSelectedItem(settings.keyCode()); // Fires the listener that rewrites the mode labels.
        (settings.mode() == TagHoverSettings.Mode.TOGGLE ? toggle : hold).setSelected(true);
        boxes.forEach((d, box) -> box.setSelected(settings.shows(d)));
    }
}
