package com.elertan.models;

import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import lombok.Value;
import lombok.With;

@Value
public class AccountConfiguration {

    public enum StorageMode {
        LOCAL,
        FIREBASE
    }

    StorageMode storageMode;
    FirebaseRealtimeDatabaseURL firebaseRealtimeDatabaseURL;
    Long localAccountHash;
    // Null for accounts set up before item locking existed: they are new accounts.
    @With
    StartMode startMode;

    public static AccountConfiguration forFirebase(FirebaseRealtimeDatabaseURL url) {
        return new AccountConfiguration(StorageMode.FIREBASE, url, null, null);
    }

    public static AccountConfiguration forLocal(long accountHash) {
        return new AccountConfiguration(StorageMode.LOCAL, null, accountHash, null);
    }

    public boolean isExistingAccount() {
        return startMode == StartMode.EXISTING_ACCOUNT;
    }
}
