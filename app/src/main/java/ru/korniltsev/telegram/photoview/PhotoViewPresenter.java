package ru.korniltsev.telegram.photoview;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;
import com.squareup.picasso.LruCache;
import flow.Flow;
import mortar.ViewPresenter;
import org.drinkless.td.libcore.telegram.TdApi;
import ru.korniltsev.telegram.core.adapters.ObserverAdapter;
import ru.korniltsev.telegram.core.picasso.RxGlide;
import ru.korniltsev.telegram.core.rx.ChatDB;
import ru.korniltsev.telegram.core.rx.GalleryService;
import ru.korniltsev.telegram.core.rx.RXClient;
import ru.korniltsev.telegram.core.rx.RxChat;
import ru.korniltsev.telegram.core.utils.PhotoUtils;
import rx.Observable;
import rx.subscriptions.CompositeSubscription;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.File;

import static junit.framework.Assert.assertTrue;

@Singleton
public class PhotoViewPresenter extends ViewPresenter<PhotoViewView> {

    final PhotoView path;
    final RXClient client;
    final ChatDB chats;
    final GalleryService galleryService;
    private final LruCache cache;

    private Observable<TdApi.TLObject> deleteRequest;
    private CompositeSubscription subs;

    @Inject
    public PhotoViewPresenter(PhotoView path, RXClient client, ChatDB chats, GalleryService galleryService, RxGlide glide) {
        this.path = path;
        this.client = client;
        this.chats = chats;
        this.galleryService = galleryService;
        cache = glide.getCache();
    }

    @Override
    protected void onLoad(Bundle savedInstanceState) {
        super.onLoad(savedInstanceState);
        Context ctx = getView().getContext();
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();
        if (path.profilePhoto != null) {
            String key = RxGlide.stableKeyForTdApiFile(path.profilePhoto.small) + "\n"
                    + RxGlide.ROUND.key() + "\n";
            final Bitmap smallRoundBitmap = cache.get(key);
            if (smallRoundBitmap != null) {
                getView()
                        .setStub(smallRoundBitmap);
            }
            getView().show(path.profilePhoto.big);
        } else if (path.photo != null){
            Bitmap stub = null;
            for (TdApi.PhotoSize s: path.photo.photos) {
                String key = RxGlide.stableKeyForTdApiFile(s.photo) + "\n";
                stub = cache.get(key);
                if (stub != null){
                    break;
                }
            }
            if (stub != null){
                getView().setStub(stub);
            }
            TdApi.File f = PhotoUtils.findSmallestBiggerThan(path.photo, width, height);
            getView()
                    .show(f);
        }
        if (PhotoView.NO_MESSAGE == path.messageId) {
            getView()
                    .hideDeleteMessageMenuItem();
        }
        subscribe();
    }

    private void subscribe() {
        subs = new CompositeSubscription();
        subscribeForDeletition();
    }

    public void deleteMessage() {
        assertTrue(path.messageId != PhotoView.NO_MESSAGE);
        RxChat chat = chats.getRxChat(path.chatId);
        deleteRequest = chat.deleteMessage(path.messageId);
        subscribeForDeletition();
    }

    private void subscribeForDeletition() {
        if (deleteRequest != null) {
            subs.add(
                    deleteRequest.subscribe(new ObserverAdapter<TdApi.TLObject>() {
                        @Override
                        public void onNext(TdApi.TLObject response) {
                            Flow.get(getView())
                                    .goBack();
                        }
                    }));
        }
    }

    @Override
    public void dropView(PhotoViewView view) {
        super.dropView(view);
        subs.unsubscribe();
    }

    public void saveToGallery() {
        if (path.photo != null){
            galleryService.saveToGallery(path.photo)
                    .subscribe(new ObserverAdapter<File>());
        } else if (path.profilePhoto != null){
            galleryService.saveToGallery(path.profilePhoto.big)
                    .subscribe(new ObserverAdapter<File>());
        }
    }
}
