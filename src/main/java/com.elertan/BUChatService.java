package com.elertan;

import com.elertan.chat.GameMessageParser;
import com.elertan.chat.ParsedGameMessage;
import com.elertan.event.BUEvent;
import com.elertan.event.GameMessageToEventTransformer;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.Member;
import com.elertan.utils.ListenerUtils;
import com.elertan.utils.TextUtils;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.Color;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.ColorUtil;

@Slf4j
@Singleton
public class BUChatService implements BUPluginLifecycle {

    private static final Set<ChatMessageType> CHAT_MESSAGE_TYPES_TO_APPLY_ICON_TO = ImmutableSet.of(
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.CLAN_CHAT,
        ChatMessageType.FRIENDSCHAT,
        ChatMessageType.PRIVATECHAT
    );
    private final ConcurrentLinkedQueue<Consumer<Boolean>> isChatboxTransparentListeners = new ConcurrentLinkedQueue<>();
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ChatMessageManager chatMessageManager;
    @Inject
    private BUPluginConfig config;
    @Inject
    private AccountConfigurationService accountConfigurationService;
    @Inject
    private BUResourceService buResourceService;
    private final Consumer<AccountConfiguration> currentAccountConfigurationChangeListener = this::currentAccountConfigurationChangeListener;
    @Inject
    private MemberService memberService;
    @Inject
    private BUEventService buEventService;
    private Boolean isChatboxTransparent = null;

    @Override
    public void startUp() throws Exception {
        accountConfigurationService.addCurrentAccountConfigurationChangeListener(
            currentAccountConfigurationChangeListener);

        manageIconOnChatbox(false);
    }

    @Override
    public void shutDown() throws Exception {
        manageIconOnChatbox(true);
        accountConfigurationService.removeCurrentAccountConfigurationChangeListener(
            currentAccountConfigurationChangeListener);
    }

    public void onChatMessage(ChatMessage chatMessage) {
        MessageNode messageNode = chatMessage.getMessageNode();
        ChatMessageType chatMessageType = chatMessage.getType();

        if (CHAT_MESSAGE_TYPES_TO_APPLY_ICON_TO.contains(chatMessageType)) {
            String name = messageNode.getName();
            String sanitizedName = TextUtils.sanitizePlayerName(name);
            Member member = memberService.getMemberByName(sanitizedName);
            if (member == null) {
                return;
            }

            addIconToChatMessage(chatMessage);
        }

        if (chatMessageType == ChatMessageType.GAMEMESSAGE) {
            String message = chatMessage.getMessage();
            ParsedGameMessage parsedGameMessage = GameMessageParser.tryParseGameMessage(message);
            if (parsedGameMessage != null) {
                BUEvent event = GameMessageToEventTransformer.transformGameMessage(
                    parsedGameMessage,
                    client.getAccountHash()
                );
                if (event != null) {
                    buEventService.publishEvent(event).whenComplete((__, throwable) -> {
                        if (throwable != null) {
                            log.error("error publishing game message event", throwable);
                            return;
                        }

                        log.info("published game message event");
                    });
                }
            }
        }
    }

    public void onScriptCallbackEvent(ScriptCallbackEvent scriptCallbackEvent) {
        String eventName = scriptCallbackEvent.getEventName();
        if (eventName.equals("setChatboxInput")) {
            manageIconOnChatbox(false);
        }
    }

    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
//            boolean isTransparent = client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1;
//            log.info("isTransparent after logged in: {}", isTransparent);
//            setIsChatboxTransparent(isTransparent);
        } else if (event.getGameState() == GameState.LOGIN_SCREEN) {
            setIsChatboxTransparent(null);
        }
    }

    public void onVarbitChanged(VarbitChanged event) {
        int varbitId = event.getVarbitId();
        if (varbitId == VarbitID.CHATBOX_TRANSPARENCY) {
            boolean isTransparent = event.getValue() == 1;
//            log.info("isTransparent after varbit change: {}", isTransparent);
            setIsChatboxTransparent(isTransparent);
        }
    }

    public void sendMessage(String message) {
        log.debug("Sending chat message: {}", message);

        waitForIsChatboxTransparentSet(null)
            .whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("error waiting for isChatboxTransparent to become ready", throwable);
                    return;
                }

                clientThread.invokeLater(() -> {
                    String messageChatIcon = getMessageChatIcon();

                    if (messageChatIcon == null) {
                        throw new IllegalStateException("Chat icon has not been set");
                    }
                    Color chatColor = isChatboxTransparent ? config.chatColorTransparent()
                        : config.chatColorOpaque();

                    ChatMessageBuilder builder = new ChatMessageBuilder();
                    // We need to supply a color here, otherwise the image does not work...
                    builder.append(chatColor, messageChatIcon);
                    // Replacing all closing cols with our chat color to reset it back to our default
                    if (config.useChatColor()) {
                        String pluginChatColorTag = ColorUtil.colorTag(chatColor);
                        String chatColorFixedMessage = message.replaceAll(
                            "</col>",
                            pluginChatColorTag
                        );
                        builder.append(chatColor, " " + chatColorFixedMessage);
                    } else {
                        builder.append(" " + message);
                    }

                    String formattedMessage = builder.build();
                    QueuedMessage queuedMessage = QueuedMessage.builder()
                        .type(ChatMessageType.GAMEMESSAGE)
                        .runeLiteFormattedMessage(formattedMessage)
                        .build();
                    chatMessageManager.queue(queuedMessage);
                });
            });
    }

    public void addIsChatboxTransparentListener(Consumer<Boolean> listener) {
        isChatboxTransparentListeners.add(listener);
    }

    public void removeIsChatboxTransparentListener(Consumer<Boolean> listener) {
        isChatboxTransparentListeners.remove(listener);
    }

    public CompletableFuture<Void> waitForIsChatboxTransparentSet(Duration timeout) {
        return ListenerUtils.waitUntilReady(new ListenerUtils.WaitUntilReadyContext() {
            Consumer<Boolean> listener;

            @Override
            public boolean isReady() {
                return isChatboxTransparent != null;
            }

            @Override
            public void addListener(Runnable notify) {
                addIsChatboxTransparentListener(isChatboxTransparent -> notify.run());
            }

            @Override
            public void removeListener() {
                removeIsChatboxTransparentListener(listener);
            }

            @Override
            public Duration getTimeout() {
                return timeout;
            }
        });
    }

    private void setIsChatboxTransparent(Boolean isChatboxTransparent) {
        if (Objects.equals(this.isChatboxTransparent, isChatboxTransparent)) {
            return;
        }
        this.isChatboxTransparent = isChatboxTransparent;
        log.debug("isChatboxTransparent set to {}", isChatboxTransparent);

        for (Consumer<Boolean> listener : isChatboxTransparentListeners) {
            try {
                listener.accept(isChatboxTransparent);
            } catch (Exception e) {
                log.error("set isChatboxTransparent listener error", e);
            }
        }
    }

    private void currentAccountConfigurationChangeListener(
        AccountConfiguration accountConfiguration) {
        manageIconOnChatbox(false);
    }

    private void addIconToChatMessage(ChatMessage chatMessage) {
        String messageChatIcon = getMessageChatIcon();
        if (messageChatIcon == null) {
            return;
        }

        MessageNode messageNode = chatMessage.getMessageNode();
        String name = TextUtils.sanitizePlayerName(messageNode.getName());

        String newName = messageChatIcon + name;
        messageNode.setName(newName);
    }

    private void manageIconOnChatbox(boolean isShuttingDown) {
        String messageChatIcon = getMessageChatIcon();
        if (messageChatIcon == null) {
            return;
        }

        AccountConfiguration accountConfiguration = accountConfigurationService.getCurrentAccountConfiguration();

        Widget chatboxInput = client.getWidget(ComponentID.CHATBOX_INPUT);
        if (chatboxInput == null) {
            return;
        }

        String currentText = chatboxInput.getText();
        if (currentText == null) {
            return;
        }

        if (isShuttingDown || accountConfiguration == null) {
            // Remove
            if (!currentText.contains(messageChatIcon)) {
                return;
            }
            String newText = currentText.replace(messageChatIcon, "");
            chatboxInput.setText(newText);
        } else {
            // Add
            if (currentText.contains(messageChatIcon)) {
                return;
            }
            chatboxInput.setText(messageChatIcon + currentText);
        }
    }

    private String getMessageChatIcon() {
        BUResourceService.BUModIcons buModIcons = buResourceService.getBuModIcons();
        if (buModIcons == null) {
            log.error("buModIcons is null, can't get chatIconId");
            return null;
        }
        int chatIconId = buModIcons.getChatIconId();
        return "<img=" + chatIconId + ">";
    }
}
