# Adding a New Event Type

Events broadcast achievements/moments to group members.

## Steps

### 1. Add Event Type Enum

Edit `event/BUEventType.java`:

```java
public enum BUEventType {
    // ... existing
    MY_EVENT
}
```

### 2. Create Event Class

Location: `src/main/java/com.elertan/event/MyBUEvent.java`

```java
package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.Getter;

public class MyBUEvent extends BUEvent {

    @Getter
    private final String myData;

    public MyBUEvent(long accountHash, ISOOffsetDateTime timestamp, String myData) {
        super(accountHash, timestamp);
        this.myData = myData;
    }

    @Override
    public BUEventType getType() {
        return BUEventType.MY_EVENT;
    }
}
```

### 3. Register in BUEventGson

Edit `event/BUEventGson.java` to add type adapter mapping:

```java
// In the RuntimeTypeAdapterFactory setup
.registerSubtype(MyBUEvent.class, BUEventType.MY_EVENT.name())
```

### 4. Create ParsedGameMessage (if chat-triggered)

Location: `src/main/java/com.elertan/chat/MyParsedGameMessage.java`

```java
package com.elertan.chat;

import lombok.Getter;

public class MyParsedGameMessage extends ParsedGameMessage {

    @Getter
    private final String extractedData;

    public MyParsedGameMessage(String extractedData) {
        this.extractedData = extractedData;
    }

    @Override
    public ParsedGameMessageType getType() {
        return ParsedGameMessageType.MY_TYPE;
    }
}
```

### 5. Add Parsing Logic

Edit `chat/GameMessageParser.java`:

```java
private static final Pattern MY_PATTERN = Pattern.compile("...");

public ParsedGameMessage parse(String message) {
    // ... existing parsing
    Matcher myMatcher = MY_PATTERN.matcher(message);
    if (myMatcher.matches()) {
        return new MyParsedGameMessage(myMatcher.group(1));
    }
    // ...
}
```

### 6. Add Transformer Logic

Edit `event/GameMessageToEventTransformer.java`:

```java
public BUEvent transform(ParsedGameMessage msg, long accountHash) {
    ISOOffsetDateTime ts = ISOOffsetDateTime.now();
    switch (msg.getType()) {
        // ... existing
        case MY_TYPE:
            MyParsedGameMessage myMsg = (MyParsedGameMessage) msg;
            return new MyBUEvent(accountHash, ts, myMsg.getExtractedData());
    }
}
```

### 7. Add Notification Display

Edit `BUChatService` to handle new event type for notifications.

## Checklist

- [ ] Enum added to `BUEventType`
- [ ] Event class extends `BUEvent`
- [ ] Registered in `BUEventGson`
- [ ] If chat-triggered:
  - [ ] ParsedGameMessage subclass
  - [ ] Enum in `ParsedGameMessageType`
  - [ ] Pattern in `GameMessageParser`
  - [ ] Transform logic in `GameMessageToEventTransformer`
- [ ] Notification display added
