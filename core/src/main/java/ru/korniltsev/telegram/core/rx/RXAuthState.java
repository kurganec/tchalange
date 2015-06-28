package ru.korniltsev.telegram.core.rx;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.crashlytics.android.core.CrashlyticsCore;
import org.drinkless.td.libcore.telegram.TdApi;
import rx.Observable;
import rx.subjects.PublishSubject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static io.fabric.sdk.android.services.common.CommonUtils.closeQuietly;

@Singleton
public class RXAuthState {
    public static final String PREF_AUTHORIZED = "pref_authorized";
    public static final String RX_CLIENT = "rx_client";
    public static final String ME_UID = "ME_UID";
    private final SharedPreferences prefs;


    //    private TdApi.User user;

    public abstract class AuthState implements Serializable{
    }

    public class StateLogout extends AuthState{

    }

    public class StateAuthorized extends AuthState{
        public final int id;
        @Nullable
        public final TdApi.User user;
        public final boolean fresh;

        public StateAuthorized(int id, @Nullable TdApi.User user, boolean fresh) {
            this.id = id;
            this.user = user;
            this.fresh = fresh;
        }
    }

    private final PublishSubject<AuthState> authState = PublishSubject.create();;
    final Context ctx;
    AuthState state;

    @Inject
    public RXAuthState(Context ctx) {
        this.ctx = ctx;
        prefs = ctx.getSharedPreferences(RX_CLIENT, Context.MODE_PRIVATE);
        boolean authorized = prefs.getBoolean(PREF_AUTHORIZED, false);
        if (authorized) {
            int uid = prefs.getInt(ME_UID, -1);
            TdApi.User user = getCurrentUser();
            state = new StateAuthorized(uid, user, false);
        } else {
            state = new StateLogout();
        }
    }

    public AuthState getState() {
        return state;
    }

    public void authorized(TdApi.User user) {
        saveToDisk(user);
        this.state = new StateAuthorized(user.id, user, true);
        prefs.edit()
                .putBoolean(PREF_AUTHORIZED, true)
                .putInt(ME_UID, user.id)
                .apply();
        authState.onNext(state);
    }



    public void logout() {
        state = new StateLogout();
        deleteUserFromDisk();
        prefs.edit()
                .remove(PREF_AUTHORIZED)
                .apply();

        authState.onNext(state);
    }

    private void deleteUserFromDisk() {
        File currentUserDir = getCurrentUserFile();
        currentUserDir.delete();
    }

    @NonNull
    private File getCurrentUserFile() {
        return new File(ctx.getFilesDir(), "currentUser");
    }
    
    @Nullable
    private TdApi.User getCurrentUser() {
        ObjectInputStream i = null;
        try {
            i = new ObjectInputStream(new FileInputStream(getCurrentUserFile()));
            Object o = i.readObject();
            i.close();
            return (TdApi.User) o;
        } catch (Exception e) {
            return null;
        } finally {
            closeQuietly(i);
        }
    }

    public void saveToDisk(TdApi.User user) {
        try {

            ObjectOutputStream out = new ObjectOutputStream(
                    new FileOutputStream(
                            getCurrentUserFile()));
            out.writeObject(user);
            out.close();
        } catch (IOException e) {
            CrashlyticsCore.getInstance().logException(e);
        }
    }

    public Observable<AuthState> listen() {
        return authState;
    }
}
