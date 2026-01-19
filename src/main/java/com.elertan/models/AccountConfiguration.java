package com.elertan.models;

import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import lombok.Value;

@Value
public class AccountConfiguration {

    FirebaseRealtimeDatabaseURL firebaseRealtimeDatabaseURL;
}
