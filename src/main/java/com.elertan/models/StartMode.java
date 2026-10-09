package com.elertan.models;

/**
 * On an existing account, the items owned when it was counted are locked (see ItemLockService).
 * A null value in stored data means {@link #NEW_ACCOUNT}.
 */
public enum StartMode {
    NEW_ACCOUNT,
    EXISTING_ACCOUNT
}
