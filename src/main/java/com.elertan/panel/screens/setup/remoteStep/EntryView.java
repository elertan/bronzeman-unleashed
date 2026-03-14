package com.elertan.panel.screens.setup.remoteStep;

import com.elertan.ui.Bindings;
import com.elertan.ui.Property;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.HyperlinkEvent;
import lombok.Getter;

public class EntryView extends JPanel implements AutoCloseable {

    @Getter
    private final EntryViewViewModel viewModel;

    private final AutoCloseable firebaseRealtimeDatabaseURLTextFieldTextBinding;
    private final AutoCloseable errorMessageLabelVisibleBinding;
    private final AutoCloseable errorMessageLabelTextBinding;
    private final AutoCloseable continueButtonEnabledBinding;

    public EntryView(EntryViewViewModel viewModel) {
        this.viewModel = viewModel;

        setLayout(new GridBagLayout());
        setAlignmentY(Component.TOP_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridy = 0;

        JLabel firebaseUrlLabel = new JLabel("Firebase Realtime DB URL:");
        add(firebaseUrlLabel, gbc);

        // spacing
        gbc.gridy++;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        add(Box.createVerticalStrut(3), gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy++;
        JTextField firebaseRealtimeDatabaseURLTextField = new JTextField(22);
        firebaseRealtimeDatabaseURLTextFieldTextBinding = Bindings.bindTextFieldText(
            firebaseRealtimeDatabaseURLTextField,
            viewModel.firebaseRealtimeDatabaseURL
        );
        add(firebaseRealtimeDatabaseURLTextField, gbc);

        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy++;

        final String guideUrl = "https://github.com/elertan/bronzeman-unleashed/blob/main/firebase-guide.md";
        JEditorPane explanationPane = createHtmlInfoPane(
            "<html><div style=\"text-align:left;color:gray;\">"
                + "Either use the URL your group owner has given you or set up a Firebase Realtime DB using the guide."
                + "<br><br>"
                + "<a href=\"" + guideUrl + "\">You can view the guide here</a>."
                + "</div></html>");
        add(explanationPane, gbc);

        gbc.gridy++;
        gbc.weighty = 0;
        add(Box.createVerticalStrut(20), gbc);

        // Add disclaimer pane
        gbc.gridy++;
        JEditorPane disclaimerPane = createHtmlInfoPane(
            "<html><div style=\"text-align:left;color:gray;\"><b>Disclaimer:</b> We are not responsible for any data loss, security issues, or charges incurred through Firebase usage. Use Firebase at your own risk.</div></html>");
        add(disclaimerPane, gbc);

        // spacing before buttons
        gbc.gridy++;
        gbc.weighty = 0;
        add(Box.createVerticalStrut(20), gbc);

        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy++;

        JLabel errorMessageLabel = new JLabel();
        errorMessageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        errorMessageLabelVisibleBinding = Bindings.bindVisible(
            errorMessageLabel,
            viewModel.errorMessage.derive(errorMessage -> errorMessage != null
                && !errorMessage.isEmpty())
        );
        errorMessageLabelTextBinding = Bindings.bindLabelText(
            errorMessageLabel, viewModel.errorMessage.derive(errorMessage -> {
                if (errorMessage == null || errorMessage.isEmpty()) {
                    return "";
                }

                String sb = "<html><div style=\"text-align:center;color:red;\">" +
                    errorMessage +
                    "</div></html>";

                return sb;
            })
        );
        add(errorMessageLabel, gbc);

        gbc.gridy++;
        gbc.weighty = 0;
        add(Box.createVerticalStrut(20), gbc);

        // Button row
        gbc.gridy++;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.setOpaque(false);

        JButton backButton = new JButton("Go back");
        backButton.addActionListener(e -> viewModel.onBackClick());
        buttonRow.add(backButton);
        buttonRow.add(Box.createHorizontalStrut(8));
        buttonRow.add(Box.createHorizontalGlue());

        JButton continueButton = new JButton("Continue");
        continueButtonEnabledBinding = Bindings.bindEnabled(
            continueButton,
            Property.deriveMany(
                Arrays.asList(
                    viewModel.isLoading.derive(isLoading -> !isLoading),
                    viewModel.isValid
                ),
                values -> values.stream().allMatch(value -> (Boolean) value)
            )
        );
        continueButton.addActionListener(e -> viewModel.onContinueClick());
        buttonRow.add(continueButton);

        add(buttonRow, gbc);

        // filler to push content to the top
        gbc.gridy++;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(Box.createVerticalGlue(), gbc);
    }

    @Override
    public void close() throws Exception {
        continueButtonEnabledBinding.close();
        errorMessageLabelVisibleBinding.close();
        errorMessageLabelTextBinding.close();
        firebaseRealtimeDatabaseURLTextFieldTextBinding.close();

        viewModel.close();
    }

    private JEditorPane createHtmlInfoPane(String html) {
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        pane.setText(html);
        pane.setEditable(false);
        pane.setHighlighter(null);
        pane.setOpaque(false);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                if (Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(
                            this,
                            "Could not open link: " + e.getURL(),
                            "Error opening link",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            }
        });
        return pane;
    }
}
