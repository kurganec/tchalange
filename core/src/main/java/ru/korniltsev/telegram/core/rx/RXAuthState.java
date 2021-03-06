package ru.korniltsev.telegram.core.rx;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DebugUtils;
import android.util.Log;
import com.crashlytics.android.core.CrashlyticsCore;
import org.drinkless.td.libcore.telegram.TdApi;
import org.json.JSONObject;
import ru.korniltsev.telegram.core.adapters.ObserverAdapter;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
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
import static rx.Observable.concat;
import static rx.Observable.just;


public class RXAuthState {
    public static final String PREF_AUTHORIZED = "pref_authorized";
    public static final String RX_CLIENT = "rx_client";
    public static final String ME_UID = "ME_UID";
    public static final String ME_JSON = "ME_JSON";
    private final SharedPreferences prefs;



    public Observable<StateAuthorized> getMe(RXClient client) {
        if (state instanceof StateAuthorized){
            StateAuthorized auth = (StateAuthorized) this.state;
            Observable<StateAuthorized> cached = just(auth);
            if (auth.fresh) {
                return cached;
            } else {
                final Observable<StateAuthorized> request = client.sendRx(new TdApi.GetMe())
                        .map(RXClient.CAST_TO_USER)
                        .map(new Func1<TdApi.User, StateAuthorized>() {
                            @Override
                            public StateAuthorized call(TdApi.User user) {
                                saveToDisk(user);
                                return new StateAuthorized(user.id, user, true);
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .cache();

                request.subscribe(new ObserverAdapter<StateAuthorized>() {
                    @Override
                    public void onNext(StateAuthorized response) {
                        state = response;
                    }
                });
                return concat(cached, request);
            }
        } else {
            CrashlyticsCore.getInstance().logException(new IllegalStateException("getMe unauthorized"));
            return Observable.empty();
        }
    }


    public static abstract class AuthState implements Serializable{
    }

    public static class StateLogout extends AuthState{

    }

    public static class StateAuthorized extends AuthState{
        public final int id;
        @NonNull
        public final TdApi.User user;
        public final boolean fresh;

        public StateAuthorized(int id, @NonNull TdApi.User user, boolean fresh) {
            this.id = id;
            this.user = user;
            this.fresh = fresh;
        }

        @Override
        public String toString() {
            return "StateAuthorized{" +
                    "id=" + id +
                    ", user=" + user +
                    ", fresh=" + fresh +
                    '}';
        }
    }

    private final PublishSubject<AuthState> authState = PublishSubject.create();;
    final Context ctx;
    AuthState state;


    public RXAuthState(Context ctx) {
        this.ctx = ctx;
        prefs = ctx.getSharedPreferences(RX_CLIENT, Context.MODE_PRIVATE);
        boolean authorized = prefs.getBoolean(PREF_AUTHORIZED, false);
        if (authorized) {
            int uid = prefs.getInt(ME_UID, -1);
            TdApi.User user = getCurrentUser();
            if (user == null) {
                CrashlyticsCore.getInstance()
                        .logException(new IllegalStateException("ftw"));
                state = new StateLogout();
            } else {
                state = new StateAuthorized(uid, user, false);
            }
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
//        long start = System.nanoTime();
        final String serializedMe = prefs.getString(ME_JSON, null);
        if (serializedMe != null) {
            final TdApi.User deserialized = FastUserSerialization.deserialize(serializedMe);
            if (deserialized != null){
//                long end = System.nanoTime();
//                Log.d("Duration", (end - start) + " deserialization");
                return deserialized;
            }
        }

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
//        long start = System.nanoTime();
        final String serializedUser = FastUserSerialization.serialize(user);
        prefs.edit().putString(ME_JSON, serializedUser).apply();
//        long end = System.nanoTime();
//        Log.d("Duration", (end - start) + " serialization");

        //        final JSONObject jsonObject = new JSONObject();
//        jsonObject.put("firstName", user.firstName);
//        jsonObject.put("lastName", user.firstName);
//        try {
//
//            ObjectOutputStream out = new ObjectOutputStream(
//                    new FileOutputStream(
//                            getCurrentUserFile()));
//            out.writeObject(user);
//            out.close();
//        } catch (IOException e) {
//            CrashlyticsCore.getInstance().logException(e);
//        }
    }

    public Observable<AuthState> listen() {
        return authState;
    }
}
