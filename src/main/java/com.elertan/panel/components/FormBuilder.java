package com.elertan.panel.components;

import com.elertan.ui.Property;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

public class FormBuilder {

    private final List<FormEntry> entries = new ArrayList<>();
    private String currentSection;

    public FormBuilder section(String title) {
        currentSection = title;
        return this;
    }

    public FormBuilder checkbox(String label, String tooltip,
        Property<Boolean> prop, Property<Boolean> enabled) {
        entries.add(new FormEntry(currentSection, label, tooltip, prop, enabled));
        currentSection = null;
        return this;
    }

    public JPanel build() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        int y = 0;

        for (FormEntry entry : entries) {
            if (entry.section != null) {
                if (y > 0) {
                    c.gridy = y++;
                    c.insets = new Insets(8, 0, 2, 0);
                    panel.add(new JSeparator(), c);
                }
                JLabel header = new JLabel(entry.section);
                header.setFont(header.getFont().deriveFont(Font.BOLD));
                c.gridy = y++;
                c.insets = new Insets(y == 1 ? 0 : 4, 0, 4, 0);
                panel.add(header, c);
            }

            JCheckBox cb = new JCheckBox(entry.label);
            cb.setOpaque(false);
            if (entry.tooltip != null) {
                cb.setToolTipText(wrapTooltip(entry.tooltip));
            }
            if (entry.prop != null) {
                cb.setSelected(Boolean.TRUE.equals(entry.prop.get()));
                entry.prop.addListener(evt -> cb.setSelected(Boolean.TRUE.equals(evt.getNewValue())));
                cb.addActionListener(e -> entry.prop.set(cb.isSelected()));
            }
            if (entry.enabled != null) {
                cb.setEnabled(Boolean.TRUE.equals(entry.enabled.get()));
                entry.enabled.addListener(evt -> cb.setEnabled(Boolean.TRUE.equals(evt.getNewValue())));
            }
            c.gridy = y++;
            c.insets = new Insets(0, 0, 0, 0);
            panel.add(cb, c);
        }
        return panel;
    }

    private static String wrapTooltip(String text) {
        return "<html><body style='width:200px'>" + text + "</body></html>";
    }

    private static class FormEntry {

        final String section, label, tooltip;
        final Property<Boolean> prop;
        final Property<Boolean> enabled;

        FormEntry(String s, String l, String t, Property<Boolean> p, Property<Boolean> e) {
            section = s;
            label = l;
            tooltip = t;
            prop = p;
            enabled = e;
        }
    }
}
