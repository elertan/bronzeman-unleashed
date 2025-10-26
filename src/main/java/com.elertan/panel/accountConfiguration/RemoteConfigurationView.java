package com.elertan.panel.accountConfiguration;

import com.elertan.data.GameRulesDataProvider;
import com.elertan.models.GameRules;
import com.elertan.remote.ObjectStoragePort;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.elertan.remote.firebase.storageAdapters.GameRulesFirebaseObjectStorageAdapter;
import com.google.gson.Gson;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.net.URL;

public class RemoteConfigurationView extends JPanel {
    private enum State {
        ENTRY,
        CHECKING
    }

    public interface Listener {
        void onSuccess(FirebaseRealtimeDatabaseURL url, GameRules gameRules);
    }

    private final OkHttpClient httpClient;
    private final ClientThread clientThread;
    private final Gson gson;
    private final Listener listener;

    private State state = State.ENTRY;

    // Card layout + container for swapping UI per state
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JTextField firebaseUrlTextField;
    private JButton continueButton;
    private String firebaseUrlInput = "";

    private String errorMessage;
    private JLabel errorLabel;

    public RemoteConfigurationView(OkHttpClient httpClient, ClientThread clientThread, Gson gson, Listener listener) {
        this.httpClient = httpClient;
        this.clientThread = clientThread;
        this.gson = gson;
        this.listener = listener;

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        // Title/header wrapper (stays static above cards)
        JLabel titleLabel = new JLabel("Remote Configuration");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(10));
        add(header, BorderLayout.NORTH);

        // Build cards once and switch using CardLayout
        cards.setOpaque(false);
        cards.add(createEntryPanel(), State.ENTRY.name());
        cards.add(createCheckingPanel(), State.CHECKING.name());

        add(cards, BorderLayout.CENTER);

        // Show initial state
        showState(state);
    }

    private JPanel createEntryPanel() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridy = 0;

        JLabel firebaseUrlLabel = new JLabel("Firebase Realtime DB URL:");
        formPanel.add(firebaseUrlLabel, gbc);

        // spacing
        gbc.gridy++;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(Box.createVerticalStrut(3), gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy++;
        firebaseUrlTextField = new JTextField(22);
        firebaseUrlTextField.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                firebaseUrlInput = firebaseUrlTextField.getText().trim();
                boolean ok = isValidFirebaseUrl(firebaseUrlInput);
                if (continueButton != null) {
                    continueButton.setEnabled(ok);
                }
            }
            @Override
            public void insertUpdate(DocumentEvent e) { update(); }
            @Override
            public void removeUpdate(DocumentEvent e) { update(); }
            @Override
            public void changedUpdate(DocumentEvent e) { update(); }
        });
        formPanel.add(firebaseUrlTextField, gbc);

        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy++;

        final String guideUrl = "https://github.com/elertan/bronzeman-unleashed/firebase-guide.md";
        JEditorPane explanationPane = createHtmlInfoPane("<html><div style=\"text-align:left;color:gray;\">"
                + "Either use the URL your group owner has given you or set up a Firebase Realtime DB using the guide."
                + "<br><br>"
                + "<a href=\"" + guideUrl + "\">You can view the guide here</a>."
                + "</div></html>");
        formPanel.add(explanationPane, gbc);

        gbc.gridy++;
        gbc.weighty = 0;
        formPanel.add(Box.createVerticalStrut(20), gbc);

        // Add disclaimer pane
        gbc.gridy++;
        JEditorPane disclaimerPane = createHtmlInfoPane("<html><div style=\"text-align:left;color:gray;\"><b>Disclaimer:</b> We are not responsible for any data loss, security issues, or charges incurred through Firebase usage. Use Firebase at your own risk.</div></html>");
        formPanel.add(disclaimerPane, gbc);

        // spacing before buttons
        gbc.gridy++;
        gbc.weighty = 0;
        formPanel.add(Box.createVerticalStrut(20), gbc);

        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy++;

        errorLabel = new JLabel();
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        errorLabel.setVisible(errorMessage != null && !errorMessage.isEmpty());
        if (errorMessage != null && !errorMessage.isEmpty()) {
            errorLabel.setText("<html><div style=\"text-align:center;color:red;\">" + errorMessage + "</div></html>");
        }
        formPanel.add(errorLabel, gbc);

        gbc.gridy++;
        gbc.weighty = 0;
        formPanel.add(Box.createVerticalStrut(20), gbc);

        // Button row
        gbc.gridy++;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.setOpaque(false);

//        JButton backButton = new JButton("Go back");
//        backButton.addActionListener(e -> listener.onBackButtonClicked());
//        buttonRow.add(backButton);

        buttonRow.add(Box.createHorizontalGlue());

        continueButton = new JButton("Continue");
        continueButton.setEnabled(false);
        continueButton.addActionListener(e -> {
            setState(State.CHECKING);
            checkUrl();
        });
        buttonRow.add(continueButton);

        formPanel.add(buttonRow, gbc);

        // filler to push content to the top
        gbc.gridy++;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(Box.createVerticalGlue(), gbc);

        return formPanel;
    }

    private JPanel createCheckingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        JLabel lbl = new JLabel("Checking configuration...");
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(lbl);
        center.add(Box.createVerticalStrut(10));

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(bar);

        center.add(Box.createVerticalStrut(20));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton back = new JButton("Cancel");
        back.addActionListener(e -> setState(State.ENTRY));
        buttons.add(back);
        buttons.add(Box.createHorizontalGlue());

        center.add(buttons, BorderLayout.SOUTH);

        panel.add(center, BorderLayout.CENTER);

        return panel;
    }

    private void setState(State newState) {
        this.state = newState;
        showState(this.state);
    }

    private void showState(State s) {
        cardLayout.show(cards, s.name());
        revalidate();
        repaint();
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

    private boolean isValidFirebaseUrl(String input) {
        try {
            new FirebaseRealtimeDatabaseURL(input);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void checkUrl() {
        FirebaseRealtimeDatabaseURL url;
        try {
            url = new FirebaseRealtimeDatabaseURL(firebaseUrlInput);
        } catch (Exception ex) {
            setErrorMessage("The given URL is not a valid Firebase URL");
            setState(State.ENTRY);
            return;
        }

        FirebaseRealtimeDatabase.canConnectTo(httpClient, url).whenComplete((canConnect, throwable) -> {
            if (throwable != null || !canConnect) {
                clientThread.invokeLater(() -> {
                    setErrorMessage("Could not connect to the Firebase Realtime database, please check the URL or try again later.");
                    setState(State.ENTRY);
                });
                return;
            }

            FirebaseRealtimeDatabase db = new FirebaseRealtimeDatabase(httpClient, gson, url);
            ObjectStoragePort<GameRules> gameRulesStoragePort = new GameRulesFirebaseObjectStorageAdapter(db, gson);
            gameRulesStoragePort.read().whenComplete((gameRules, throwable2) -> {
                try {
                    db.close();
                } catch (Exception ex) {
                    // ignored
                }

               if (throwable2 != null) {
                   clientThread.invokeLater(() -> {
                       setErrorMessage("Could not read the game rules from the Firebase Realtime database, please check the URL or try again later.");
                       setState(State.ENTRY);
                   });
                   return;
               }

                clientThread.invokeLater(() -> {
                    listener.onSuccess(url, gameRules);
                    setErrorMessage(null);
                    setState(State.ENTRY);
                });
            });
        });
    }

    private void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        if (errorLabel != null) {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                errorLabel.setText("<html><div style=\"text-align:center;color:red;\">" + errorMessage + "</div></html>");
                errorLabel.setVisible(true);
            } else {
                errorLabel.setText("");
                errorLabel.setVisible(false);
            }
            errorLabel.revalidate();
            errorLabel.repaint();
        }
        revalidate();
        repaint();
    }
}
