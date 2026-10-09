package com.elertan.models;

/**
 * How a player started Bronzeman on their account. On an existing account, the items it owned
 * when it was counted are locked (see ItemLockService). A null value in stored data means
 * {@link #NEW_ACCOUNT}.
 */
public enum StartMode {
    NEW_ACCOUNT,
    EXISTING_ACCOUNT
}
