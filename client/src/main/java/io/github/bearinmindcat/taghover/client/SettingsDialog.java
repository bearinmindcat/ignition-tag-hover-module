package io.github.bearinmindcat.taghover.client;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
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

/** The Tag Hover Settings window: which key, and hold or tap. */
final class SettingsDialog {

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

        setFields(key, hold, toggle, current);

        // A fixed width makes the note wrap instead of stretching the window.
        JLabel note = new JLabel("<html><body style='width: 330px'>Saved for your Windows user on this computer. "
            + "Press Ctrl+Comma to open this window from any Vision client or the Designer.</body></html>");
        note.setForeground(UIManager.getColor("Label.disabledForeground"));

        JButton defaults = new JButton("Restore defaults");
        defaults.addActionListener(e -> setFields(key, hold, toggle, TagHoverSettings.DEFAULT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            result[0] = new TagHoverSettings(
                (Integer) key.getSelectedItem(),
                toggle.isSelected() ? TagHoverSettings.Mode.TOGGLE : TagHoverSettings.Mode.HOLD);
            dialog.dispose();
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Key:"), c);
        c.gridx = 1;
        form.add(key, c);
        c.gridx = 0;
        c.gridy = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Mode:"), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        form.add(hold, c);
        c.gridy = 2;
        c.insets = new Insets(0, 4, 4, 4);
        form.add(toggle, c);
        c.gridx = 0;
        c.gridy = 3;
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

    private static void setFields(
        JComboBox<Integer> key, JRadioButton hold, JRadioButton toggle, TagHoverSettings settings) {

        key.setSelectedItem(settings.keyCode()); // Fires the listener that rewrites the mode labels.
        (settings.mode() == TagHoverSettings.Mode.TOGGLE ? toggle : hold).setSelected(true);
    }
}
