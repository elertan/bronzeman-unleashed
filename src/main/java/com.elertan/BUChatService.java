package com.elertan;

import com.elertan.chat.ChatMessageProvider;
import com.elertan.chat.ChatMessageProvider.MessageKey;
import com.elertan.chat.ParsedGameMessage.CollectionLogUnlockParsedGameMessage;
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
    private static final int DISABLED_SOUND_EFFECT_ID = 2277;
    private static final Set<ChatMessageType> ICON_CHAT_TYPES = ImmutableSet.of(
        ChatMessageType.PUBLICCHAT, ChatMessageType.CLAN_CHAT,
        ChatMessageType.FRIENDSCHAT, ChatMessageType.PRIVATECHAT);

    @Getter private final Observable<Boolean> isChatboxTransparent = Observable.empty();
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ChatMessageManager chatMessageManager;
    @Inject private BUPluginConfig config;
    @Inject private AccountConfigurationService accountConfigurationService;
    @Inject private BUResourceService buResourceService;
    @Inject private MemberService memberService;
    @Inject private BUEventService buEventService;
    @Inject private ChatMessageProvider chatMessageProvider;
    @Inject private CollectionLogService collectionLogService;
    private Subscription accountConfigSubscription;

    @Override
    public void startUp() throws Exception {
        accountConfigSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribe(cfg -> manageIconOnChatbox(false));
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
            || accountConfigurationService.getCurrentAccountConfiguration() == null) return;

        MessageNode messageNode = chatMessage.getMessageNode();
        ChatMessageType type = chatMessage.getType();

        if (ICON_CHAT_TYPES.contains(type)) {
            String name = TextUtils.sanitizePlayerName(messageNode.getName());
            if (memberService.getMemberByName(name) != null) {
                addIconToChatMessage(chatMessage);
            }
        }

        if (type != ChatMessageType.GAMEMESSAGE) return;

        ParsedGameMessage parsed = GameMessageParser.tryParseGameMessage(chatMessage.getMessage());
        if (parsed == null) return;

        if (parsed instanceof CollectionLogUnlockParsedGameMessage) {
            collectionLogService.addRecentCollectionLogUnlock(
                ((CollectionLogUnlockParsedGameMessage) parsed).getItemName());
        }

        BUEvent event = GameMessageToEventTransformer.transformGameMessage(
            parsed, client.getAccountHash());
        if (event != null) {
            withErrorLogging(buEventService.publishEvent(event), "error publishing game message event")
                .thenRun(() -> log.info("published game message event"));
        }
    }

    public void onScriptCallbackEvent(ScriptCallbackEvent e) {
        if ("setChatboxInput".equals(e.getEventName())) manageIconOnChatbox(false);
    }

    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            setIsChatboxTransparent(client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1);
        }
    }

    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() == VarbitID.CHATBOX_TRANSPARENCY) {
            setIsChatboxTransparent(event.getValue() == 1);
        }
    }

    public void sendRestrictionMessage(MessageKey key) {
        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(config.chatRestrictionColor(), chatMessageProvider.messageFor(key));
        sendMessage(builder.build());
        clientThread.invoke(() -> client.playSoundEffect(DISABLED_SOUND_EFFECT_ID));
    }

    public void sendErrorMessage(String message) {
        ChatMessageBuilder builder = new ChatMessageBuilder();
        builder.append(config.chatErrorColor(), message);
        sendMessage(builder.build());
    }

    public void sendMessage(String message) {
        log.debug("Sending chat message: {}", message);
        withErrorLogging(isChatboxTransparent.await(null),
            "error waiting for isChatboxTransparent to become ready")
            .thenAccept(isTransparent -> {
                String iconTag = getMessageChatIconTag();
                if (iconTag == null) throw new IllegalStateException("Chat icon has not been set");

                Color chatColor = Boolean.TRUE.equals(isTransparent)
                    ? config.chatColorTransparent() : config.chatColorOpaque();
                ChatMessageBuilder builder = new ChatMessageBuilder();
                builder.append(chatColor, iconTag + " ");
                if (config.useChatColor()) {
                    String colorTag = ColorUtil.colorTag(chatColor);
                    builder.append(chatColor, message.replaceAll("</col>", colorTag));
                } else {
                    builder.append(message);
                }
                QueuedMessage queued = QueuedMessage.builder()
                    .type(ChatMessageType.GAMEMESSAGE)
                    .runeLiteFormattedMessage(builder.build())
                    .build();
                clientThread.invoke(() -> chatMessageManager.queue(queued));
            });
    }

    public CompletableFuture<String> getItemIconTag(int itemId) {
        return buResourceService.getOrSetupItemImageModIconId(itemId)
            .thenApply(id -> "<img=" + id + ">");
    }

    public CompletableFuture<String> getItemIconTagIfEnabled(int itemId) {
        return config.useItemIconsInChat() ? getItemIconTag(itemId)
            : CompletableFuture.completedFuture(null);
    }

    private void setIsChatboxTransparent(Boolean isTransparent) {
        log.debug("isChatboxTransparent set to {}", isTransparent);
        isChatboxTransparent.set(isTransparent);
    }

    private void addIconToChatMessage(ChatMessage chatMessage) {
        String iconTag = getMessageChatIconTag();
        if (iconTag == null) return;
        MessageNode node = chatMessage.getMessageNode();
        node.setName(iconTag + TextUtils.sanitizePlayerName(node.getName()));
    }

    private void manageIconOnChatbox(boolean isShuttingDown) {
        String iconTag = getMessageChatIconTag();
        if (iconTag == null) return;

        AccountConfiguration accountConfig = accountConfigurationService.getCurrentAccountConfiguration();
        Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
        if (chatboxInput == null) return;
        String text = chatboxInput.getText();
        if (text == null) return;

        if (isShuttingDown || accountConfig == null) {
            if (text.contains(iconTag)) chatboxInput.setText(text.replace(iconTag, ""));
        } else {
            if (!text.contains(iconTag)) chatboxInput.setText(iconTag + text);
        }
    }

    private String getMessageChatIconTag() {
        BUResourceService.BUModIcons icons = buResourceService.getBuModIcons();
        if (icons == null) { log.error("buModIcons is null, can't get chatIconId"); return null; }
        return "<img=" + icons.getChatIconId() + ">";
    }
}
