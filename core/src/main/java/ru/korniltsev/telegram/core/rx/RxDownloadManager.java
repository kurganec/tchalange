package ru.korniltsev.telegram.core.rx;

import android.content.Context;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import com.crashlytics.android.core.CrashlyticsCore;
import org.drinkless.td.libcore.telegram.TdApi;
import ru.korniltsev.telegram.core.Utils;
import ru.korniltsev.telegram.core.adapters.ObserverAdapter;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;


public class RxDownloadManager {
    public static final OnlyResult ONLY_RESULT = new OnlyResult();
    private Context ctx;
    final RXClient client;

    //guarded by lock
    private final Map<Integer, BehaviorSubject<FileState>> allRequests = new HashMap<>();
    //guarded by lock
    private Map<Integer, TdApi.File> allDownloadedFiles = new HashMap<>();

    final Object lock = new Object();

    //keep in memory names of files which we have copied to the external storage
    final Set<String> exposedFiles = new HashSet<>();


    public RxDownloadManager(Context ctx, RXClient client, RXAuthState auth) {
        this.ctx = ctx;
        this.client = client;
//        client
        client.filesUpdates()
                .subscribe(new ObserverAdapter<TdApi.UpdateFile>() {
                    @Override
                    public void onNext(TdApi.UpdateFile updateFile) {
                        updateFile(updateFile);
                    }
                });

        client.fileProgress().subscribe(new ObserverAdapter<TdApi.UpdateFileProgress>() {
            @Override
            public void onNext(TdApi.UpdateFileProgress upd) {
                updateFileProgress(upd);
            }
        });
        auth.listen().subscribe(new ObserverAdapter<RXAuthState.AuthState>() {
            @Override
            public void onNext(RXAuthState.AuthState authState) {
                if (authState instanceof RXAuthState.StateLogout) {
                    cleanup();
                }
            }
        });
    }

    private void cleanup() {
        synchronized (lock) {
            allRequests.clear();
            allDownloadedFiles.clear();
            exposedFiles.clear();
            fileIdToHook.clear();
        }
    }

    private void updateFileProgress(TdApi.UpdateFileProgress upd) {
        synchronized (lock){
            BehaviorSubject<FileState> s = allRequests.get(upd.fileId);
            if (s != null){
                s.onNext(new FileProgress(upd));
            } else {
                //update is for uploaded file
            }
        }
    }

    private void updateFile(TdApi.UpdateFile upd) {
        Action1<TdApi.UpdateFile> hook = fileIdToHook.get(upd.file.id);
        if (hook != null) {
            try {
                hook.call(upd);
            } catch (Exception e) {
                CrashlyticsCore.getInstance()
                        .logException(e);
            }
        }

        synchronized (lock) {
//            TdApi.FileLocal f = new TdApi.FileLocal(upd.fileId, upd.size, upd.path);
            allDownloadedFiles.put(upd.file.id, upd.file);
            BehaviorSubject<FileState> s = allRequests.get(upd.file.id);
            if (s != null){
                s.onNext(new FileDownloaded(upd.file));
            }
        }
    }

    private void log(String msg) {
        Log.e("RxDownloadManager", msg);
    }


    public Observable<FileState> download(final TdApi.File f){
        if (f.isEmpty()) {
            return download(f.id);
        } else {

            return Observable.<FileState>just(new FileDownloaded(f));
        }
    }

    public Observable<TdApi.File> downloadWithoutProgress(TdApi.File f) {
        return download(f)
                .compose(ONLY_RESULT);

    }



//    public Observable<FileState> download(final TdApi.File file) {
//        int id = file.id;
//        return download(id);
//    }

    /**
     *
     * @param id of EmptyFile
     * @return
     */
    @NonNull public Observable<FileState> download(int id) {
        synchronized (lock) {
//            log("download " + RXClient.coolTagForFileId(id));
            assertTrue(id != 0);
            //            assertFalse(isDownloading(file));
            BehaviorSubject<FileState> prevRequest = allRequests.get(id);
            if (prevRequest != null) {
//                log("return prev request" + RXClient.coolTagForFileId(id));
                return prevRequest;
            }
            TdApi.File fileLocal = allDownloadedFiles.get(id);
            if (fileLocal != null) {
//                log("already downloaded " + RXClient.coolTagForFileId(id));
                //                BehaviorSubject<FileState> newRequest = BehaviorSubject.create(fileLocal);
                //                allRequests.put(id, newRequest);
                return Observable.<FileState>just(new FileDownloaded(fileLocal));//newRequest;
            }
//            log("create new request" + RXClient.coolTagForFileId(id));
            final BehaviorSubject<FileState> s = BehaviorSubject.create();
            allRequests.put(id, s);
            client.sendSilently(new TdApi.DownloadFile(id));
            return s;
        }
    }

    public boolean isDownloaded(TdApi.File file) {
        if (!file.isEmpty()){
            return true;
        }
//        if (file instanceof TdApi.FileLocal) {
//            return true;
//        }
//        TdApi.FileEmpty e = (TdApi.FileEmpty) file;
        return getDownloadedFile(file.id) != null;
    }



    public boolean isDownloading(TdApi.File file) {
        return nonMainThreadObservableFor(file) != null;
    }

    @Nullable
    public Observable<FileState> nonMainThreadObservableFor(TdApi.File file) {
        synchronized (lock) {
            return allRequests.get(file.id);
        }
    }

    @Nullable
    public TdApi.File getDownloadedFile(Integer id) {
        synchronized (lock) {
            return allDownloadedFiles.get(id);
        }
    }

    @Nullable public TdApi.File getDownloadedFile(TdApi.File f){
        if (!f.isEmpty()){
            return f;
        } else {
            return getDownloadedFile(f.id);
        }
    }

    public File exposeFile(File src, String type, @Nullable String originalFileName) {
        File dstDir = ctx.getExternalFilesDir(type);
        String name = src.getName();
        File dst = new File(dstDir, originalFileName == null? name: originalFileName);
        if (exposedFiles.contains(name)) {
        } else {
            try {
                Utils.copyFile(src, dst);
            } catch (IOException e) {
                return dst;
            }
        }
        return dst;

    }

    public void decode(TdApi.UpdateFile updateFile) {

    }

    public class FileState {//todo delete this class

    }

    public class FileDownloaded extends FileState {
        public final TdApi.File f;

        public FileDownloaded(TdApi.File f) {
            this.f = f;
        }
    }

    public class FileProgress extends FileState{
        public final TdApi.UpdateFileProgress p;

        public FileProgress(TdApi.UpdateFileProgress p) {
            this.p = p;
        }
    }

    public static class OnlyResult implements Observable.Transformer<FileState, TdApi.File> {
        @Override
        public Observable<TdApi.File> call(Observable<FileState> fileStateObservable) {
            return fileStateObservable.filter(new Func1<FileState, Boolean>() {
                @Override
                public Boolean call(FileState fileState) {
                    return fileState instanceof FileDownloaded;
                }
            }).map(new Func1<FileState, TdApi.File>() {
                @Override
                public TdApi.File call(FileState fileState) {
                    return ((FileDownloaded) fileState).f;
                }
            });
        }
    }


    private Map<Integer, Action1<TdApi.UpdateFile>> fileIdToHook = new ConcurrentHashMap<>();

    public void hook(TdApi.File fileId, Action1<TdApi.UpdateFile> a) {
        fileIdToHook.put(fileId.id, a);
    }
}
