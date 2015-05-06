package ru.korniltsev.telegram.core.rx;

import android.support.annotation.Nullable;
import android.util.Log;
import org.drinkless.td.libcore.telegram.TdApi;
import rx.Observable;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

@Singleton
public class RxDownloadManager {
    final RXClient client;

    //guarded by lock
    private final Map<Integer, BehaviorSubject<TdApi.FileLocal>> allRequests = new HashMap<>();
    //guarded by lock
    private Map<Integer, TdApi.FileLocal> allDownloadedFiles = new HashMap<>();

    final Object lock = new Object();

    @Inject
    public RxDownloadManager(RXClient client) {
        this.client = client;
        client.filesUpdates()
                .subscribe(new Action1<TdApi.UpdateFile>() {
                    @Override
                    public void call(TdApi.UpdateFile updateFile) {
                        updateFile(updateFile);
                    }
                });
    }

    private void updateFile(TdApi.UpdateFile upd) {
        synchronized (lock) {
            log("updateFile" + upd.fileId);
            TdApi.FileLocal f = new TdApi.FileLocal(upd.fileId, upd.size, upd.path);
            allDownloadedFiles.put(upd.fileId, f);
            BehaviorSubject<TdApi.FileLocal> s = allRequests.get(upd.fileId);
            if (s != null) {
                log("updateFile: s.onNext");
                s.onNext(f);
            }
        }
    }

    private int log(String msg) {
        return Log.d("RxDownloadManager", msg);
    }

    public Observable<TdApi.FileLocal> download(final TdApi.FileEmpty file) {
        synchronized (lock) {
            log("download " + file.id);
            assertTrue(file.id != 0);
//            assertFalse(isDownloading(file));
            BehaviorSubject<TdApi.FileLocal> prevRequest = allRequests.get(file.id);
            if (prevRequest != null) {
                log("return prev request");
                return prevRequest;
            }
            TdApi.FileLocal fileLocal = allDownloadedFiles.get(file.id);
            if (fileLocal != null) {
                log("already downloaded");
                BehaviorSubject<TdApi.FileLocal> newRequest = BehaviorSubject.create(fileLocal);
                allRequests.put(file.id, newRequest);
                return newRequest;
            }
            log("create new request");
            final BehaviorSubject<TdApi.FileLocal> s = BehaviorSubject.create();
            allRequests.put(file.id, s);
            client.sendSilently(new TdApi.DownloadFile(file.id));
            return s;
        }
    }

    public boolean isDownloaded(TdApi.File file) {
        if (file instanceof TdApi.FileLocal) {
            return true;
        }
        TdApi.FileEmpty e = (TdApi.FileEmpty) file;
        return getDownloadedFile(e.id) != null;
    }

    public boolean isDownloading(TdApi.FileEmpty file) {
        return nonMainThreadObservableFor(file) != null;
    }

    public Observable<TdApi.FileLocal> nonMainThreadObservableFor(TdApi.FileEmpty file) {
        synchronized (lock) {
            return allRequests.get(file.id);
        }
    }

    @Nullable
    public TdApi.FileLocal getDownloadedFile(Integer id) {
        synchronized (lock) {
            return allDownloadedFiles.get(id);
        }
    }
}
