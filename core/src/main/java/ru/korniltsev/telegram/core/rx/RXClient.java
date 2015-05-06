package ru.korniltsev.telegram.core.rx;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;
import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TG;
import org.drinkless.td.libcore.telegram.TdApi;
import org.drinkless.td.libcore.telegram.TdApi.TLObject;
import ru.korniltsev.telegram.core.adapters.RequestHandlerAdapter;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static rx.android.schedulers.AndroidSchedulers.mainThread;

/**
 * Created by korniltsev on 21/04/15.
 */
@Singleton
public class RXClient {

    public static final Func1<TLObject, TdApi.Messages> CAST_TO_MESSAGE = new Func1<TLObject, TdApi.Messages>() {
        @Override
        public TdApi.Messages call(TLObject o) {
            return (TdApi.Messages) o;
        }
    };
    public static final Func1<TLObject, TdApi.User> CAST_TO_USER = new Func1<TLObject, TdApi.User>() {
        @Override
        public TdApi.User call(TLObject o) {
            return (TdApi.User) o;
        }
    };
    public static final Func1<TLObject, TdApi.Chats> CAST_TO_CHATS = new Func1<TLObject, TdApi.Chats>() {
        @Override
        public TdApi.Chats call(TLObject o) {
            return (TdApi.Chats) o;
        }
    };
    public static final Func1<TLObject, TdApi.UpdateFile> CAST_TO_FILE_UPDATE = new Func1<TLObject, TdApi.UpdateFile>() {
        @Override
        public TdApi.UpdateFile call(TLObject o) {
            return (TdApi.UpdateFile) o;
        }
    };
    public static final Func1<TLObject, Boolean> ONLY_FILE_UPDATES = new Func1<TLObject, Boolean>() {
        @Override
        public Boolean call(TLObject tlObject) {
            return tlObject instanceof TdApi.UpdateFile;
        }
    };
    public static final Func1<TLObject, Boolean> ONLY_NEW_MESSAGE_UPDATES = new Func1<TLObject, Boolean>() {
        @Override
        public Boolean call(TLObject tlObject) {
            return tlObject instanceof TdApi.UpdateNewMessage;
        }
    };
    public static final Func1<TLObject, TdApi.UpdateNewMessage> CAST_TO_NEW_MESSAGE_UPDATE = new Func1<TLObject, TdApi.UpdateNewMessage>() {
        @Override
        public TdApi.UpdateNewMessage call(TLObject tlObject) {
            return (TdApi.UpdateNewMessage) tlObject;
        }
    };
    public static final Func1<TLObject, Boolean> ONLY_UPDATE_MESSAGE_ID = new Func1<TLObject, Boolean>() {
        @Override
        public Boolean call(TLObject tlObject) {
            return tlObject instanceof TdApi.UpdateMessageId;
        }
    };
    public static final Func1<TLObject, TdApi.UpdateMessageId> CAST_TO_UPDATE_MESSAGE_ID = new Func1<TLObject, TdApi.UpdateMessageId>() {
        @Override
        public TdApi.UpdateMessageId call(TLObject tlObject) {
            return (TdApi.UpdateMessageId) tlObject;
        }
    };
    private Context ctx;

    private final Client client;
    private final PublishSubject<TdApi.TLObject> globalSubject = PublishSubject.create();



    @Inject
    public RXClient(Context ctx) {
        this.ctx = ctx;
        TG.setUpdatesHandler(new Client.ResultHandler() {
            @Override
            public void onResult(TLObject object) {
                globalSubject.onNext(object);
            }
        });
        TG.setDir(ctx.getFilesDir().getAbsolutePath() + "/");
        this.client = TG.getClientInstance();

        globalSubject
                .filter(ONLY_UPDATE_MESSAGE_ID).subscribe(new Action1<TLObject>() {
            @Override
            public void call(TLObject tlObject) {
                System.out.println(tlObject);
            }
        });



    }


    //observe function on ui thread
    public Observable<TLObject> sendRXUI(final TdApi.TLFunction function) {
        return sendRX(function)
                .observeOn(mainThread());
    }

    //observe function
    public Observable<TLObject> sendRX(final TdApi.TLFunction function) {
        return Observable.create(new Observable.OnSubscribe<TLObject>() {
            @Override
            public void call(final Subscriber<? super TLObject> s) {
                client.send(function, new Client.ResultHandler() {
                    @Override
                    public void onResult(TLObject object) {
                        if (object instanceof TdApi.Error) {
                            Log.e("RxClient", ((TdApi.Error) object).text);
                            s.onError(new RxClientException((TdApi.Error) object));
                        } else {
                            s.onNext(object);
                            s.onCompleted();
                        }
                    }
                });
            }
        }).cache();
    }

    public void sendSilently(final TdApi.TLFunction function) {
        client.send(function, RequestHandlerAdapter.INSTANCE);
    }

    public Observable<TdApi.User> getUser(int id) {

        return sendRXUI(new TdApi.GetUser(id))
                .map(CAST_TO_USER);
    }

    public Observable<TdApi.GroupChatFull> getGroupChatInfo(int id) {
        return sendRXUI(new TdApi.GetGroupChatFull(id))
                .map(new Func1<TLObject, TdApi.GroupChatFull>() {
                    @Override
                    public TdApi.GroupChatFull call(TLObject o) {
                        return (TdApi.GroupChatFull) o;
                    }
                });
    }

    // ui thread
    public Observable<TdApi.UpdateMessageId> messageIdsUpdates(final long chatId) {
        return globalSubject.filter(ONLY_UPDATE_MESSAGE_ID)
                .map(CAST_TO_UPDATE_MESSAGE_ID)
                .filter(new Func1<TdApi.UpdateMessageId, Boolean>() {
                    @Override
                    public Boolean call(TdApi.UpdateMessageId updateMessageId) {
                        return updateMessageId.chatId == chatId;
                    }
                });
    }

    static class RxClientException extends Exception {
        public final TdApi.Error error;

        public RxClientException(TdApi.Error error) {
            super(error.text);
            this.error = error;
        }
    }

    //not ui thread
    public Observable<TdApi.UpdateFile> filesUpdates() {
        return globalSubject
                .filter(ONLY_FILE_UPDATES)
                .map(CAST_TO_FILE_UPDATE);
    }

    //not ui thread
    public Observable<TdApi.UpdateNewMessage> newMessageUpdate(final long chatId) {
        return globalSubject
                .filter(ONLY_NEW_MESSAGE_UPDATES)
                .map(CAST_TO_NEW_MESSAGE_UPDATE)
                .filter(new Func1<TdApi.UpdateNewMessage, Boolean>() {
                    @Override
                    public Boolean call(TdApi.UpdateNewMessage updateNewMessage) {
                        return updateNewMessage.message.chatId == chatId;
                    }
                });
    }



    public Observable<TdApi.UpdateFile> fileUpdate(final TdApi.FileEmpty file) {
        return filesUpdates()
                .filter(new Func1<TdApi.UpdateFile, Boolean>() {
                    @Override
                    public Boolean call(TdApi.UpdateFile updateFile) {
                        return file.id == updateFile.fileId;
                    }
                });
    }

    public Client getClient() {
        return client;
    }

    ////////////

    public Observable<TdApi.Chats> getChats(int offset, int limit) {
        return sendRXUI(new TdApi.GetChats(offset, limit))
                .map(CAST_TO_CHATS);
    }

    public Observable<TdApi.User> getMe() {
        return sendRXUI(new TdApi.GetMe())
                .map(CAST_TO_USER);
    }

    public Observable<TdApi.Messages> getMessages(final long chatId, final int fromId, final int offset, final int limit) {
        return sendRX(new TdApi.GetChatHistory(chatId, fromId, offset, limit))
                .map(CAST_TO_MESSAGE);
    }


}
