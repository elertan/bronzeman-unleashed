package com.elertan.models;

import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import lombok.Value;

@Value
public class AccountConfiguration {

    public enum StorageMode {
        LOCAL,
        FIREBASE
    }

    StorageMode storageMode;
    FirebaseRealtimeDatabaseURL firebaseRealtimeDatabaseURL;
    Long localAccountHash;

    public static AccountConfiguration forFirebase(FirebaseRealtimeDatabaseURL url) {
        return new AccountConfiguration(StorageMode.FIREBASE, url, null);
    }

    public static AccountConfiguration forLocal(long accountHash) {
        return new AccountConfiguration(StorageMode.LOCAL, null, accountHash);
    }
}
