package com.elertan;

import com.elertan.chat.ChatMessageProvider;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.elertan.chat.CollectionLogUnlockParsedGameMessage;
import com.elertan.chat.GameMessageParser;
import com.elertan.chat.ParsedGameMessage;
import com.elertan.event.BUEvent;
import com.elertan.event.GameMessageToEventTransformer;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.Member;
import com.elertan.utils.Observable;
import com.elertan.utils.Subscription;
import com.elertan.utils.TextUtils;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import java.awt.Color;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.ColorUtil;

import static com.elertan.utils.AsyncUtils.withErrorLogging;

@Slf4j
@Singleton
public class BUChatService implements BUPluginLifecycle {

    private static final Set<ChatMessageType> CHAT_MESSAGE_TYPES_TO_APPLY_ICON_TO = ImmutableSet.of(
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.CLAN_CHAT,
        ChatMessageType.FRIENDSCHAT,
        ChatMessageType.PRIVATECHAT
    );
    @Getter
    private final Observable<Boolean> isChatboxTransparent = Observable.empty();
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
    private Subscription accountConfigSubscription;
    @Inject
    private MemberService memberService;
    @Inject
    private BUEventService buEventService;
    @Inject
    private ChatMessageProvider chatMessageProvider;
    @Inject
    private BUSoundHelper buSoundHelper;
    @Inject
    private CollectionLogService collectionLogService;

    @Override
    public void startUp() throws Exception {
        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe(this::currentAccountConfigurationChangeListener);

        manageIconOnChatbox(false);
    }

    @Override
    public void shutDown() throws Exception {
        manageIconOnChatbox(true);
        if (accountConfigSubscription != null) {
            accountConfigSubscription.dispose();
            accountConfigSubscription = null;
        }
        isChatboxTransparent.clear();
    }

    public void onChatMessage(ChatMessage chatMessage) {
        if (!accountConfigurationService.isReady()
            || accountConfigurationService.getCurrentAccountConfiguration() == null) {
            return;
        }

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

        if (chatMessageType != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        ParsedGameMessage parsedGameMessage =
            GameMessageParser.tryParseGameMessage(chatMessage.getMessage());
        if (parsedGameMessage == null) {
            return;
        }

        // Track collection log unlocks for overlay suppression (must be synchronous, before async publish)
        if (parsedGameMessage instanceof CollectionLogUnlockParsedGameMessage) {
            CollectionLogUnlockParsedGameMessage clogMessage =
                (CollectionLogUnlockParsedGameMessage) parsedGameMessage;
            collectionLogService.addRecentCollectionLogUnlock(clogMessage.getItemName());
        }

        BUEvent event = GameMessageToEventTransformer.transformGameMessage(
            parsedGameMessage, client.getAccountHash());

        if (event == null) {
            return;
        }

        withErrorLogging(buEventService.publishEvent(event), "error publishing game message event")
            .thenRun(() -> log.info("published game message event"));
    }

    public void onScriptCallbackEvent(ScriptCallbackEvent scriptCallbackEvent) {
        String eventName = scriptCallbackEvent.getEventName();
        if (eventName.equals("setChatboxInput")) {
            manageIconOnChatbox(false);
        }
    }

    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            // TODO: Find fix so we can wait till varbit value is correctly set....
            boolean isTransparent =
                client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1;
            setIsChatboxTransparent(isTransparent);
        }
    }

    public void onVarbitChanged(VarbitChanged event) {
        int varbitId = event.getVarbitId();
        if (varbitId == VarbitID.CHATBOX_TRANSPARENCY) {
            boolean isTransparent = event.getValue() == 1;
            setIsChatboxTransparent(isTransparent);
        }
    }

    /**
     * Sends a restriction message with standardized formatting and plays the disabled sound.
     *
     * @param key the message key for the restriction message
     */
    public void sendRestrictionMessage(MessageKey key) {
        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(config.chatRestrictionColor(), chatMessageProvider.messageFor(key));
        sendMessage(builder.build());
        buSoundHelper.playDisabledSound();
    }

    public void sendMessage(String message) {
        log.debug("Sending chat message: {}", message);

        waitForIsChatboxTransparentSet(null)
            .whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("error waiting for isChatboxTransparent to become ready", throwable);
                    return;
                }

                clientThread.invoke(() -> {
                    String messageChatIcon = getMessageChatIconTag();

                    if (messageChatIcon == null) {
                        throw new IllegalStateException("Chat icon has not been set");
                    }
                    Boolean isTransparent = isChatboxTransparent.get();
                    Color chatColor = Boolean.TRUE.equals(isTransparent) ? config.chatColorTransparent()
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

    public CompletableFuture<Boolean> waitForIsChatboxTransparentSet(Duration timeout) {
        return isChatboxTransparent.await(timeout);
    }

    private void setIsChatboxTransparent(Boolean isTransparent) {
        log.debug("isChatboxTransparent set to {}", isTransparent);
        isChatboxTransparent.set(isTransparent);
    }

    private void currentAccountConfigurationChangeListener(
        AccountConfiguration accountConfiguration) {
        manageIconOnChatbox(false);
    }

    private void addIconToChatMessage(ChatMessage chatMessage) {
        String messageChatIcon = getMessageChatIconTag();
        if (messageChatIcon == null) {
            return;
        }

        MessageNode messageNode = chatMessage.getMessageNode();
        String name = TextUtils.sanitizePlayerName(messageNode.getName());

        String newName = messageChatIcon + name;
        messageNode.setName(newName);
    }

    private void manageIconOnChatbox(boolean isShuttingDown) {
        String messageChatIcon = getMessageChatIconTag();
        if (messageChatIcon == null) {
            return;
        }

        AccountConfiguration accountConfiguration = accountConfigurationService.getCurrentAccountConfiguration();

        Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
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

    private String getMessageChatIconTag() {
        BUResourceService.BUModIcons buModIcons = buResourceService.getBuModIcons();
        if (buModIcons == null) {
            log.error("buModIcons is null, can't get chatIconId");
            return null;
        }
        int chatIconId = buModIcons.getChatIconId();
        return "<img=" + chatIconId + ">";
    }

    public CompletableFuture<String> getItemIconTag(int itemId) {
        return buResourceService.getOrSetupItemImageModIconId(itemId)
            .thenApply((id) -> "<img=" + id + ">");
    }

    public CompletableFuture<String> getItemIconTagIfEnabled(int itemId) {
        if (config.useItemIconsInChat()) {
            return getItemIconTag(itemId);
        }
        return CompletableFuture.completedFuture(null);
    }
}
