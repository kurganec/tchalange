package ru.korniltsev.telegram.profile.my;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import com.crashlytics.android.core.CrashlyticsCore;
import flow.Flow;
import mortar.ViewPresenter;
import org.drinkless.td.libcore.telegram.TdApi;
import ru.korniltsev.telegram.attach_panel.AttachPanelPopup;
import ru.korniltsev.telegram.chat.R;
import ru.korniltsev.telegram.common.AppUtils;
import ru.korniltsev.telegram.core.Utils;
import ru.korniltsev.telegram.core.adapters.ObserverAdapter;
import ru.korniltsev.telegram.core.mortar.ActivityOwner;
import ru.korniltsev.telegram.core.mortar.ActivityResult;
import ru.korniltsev.telegram.core.passcode.PasscodeManager;
import ru.korniltsev.telegram.core.rx.RXClient;
import ru.korniltsev.telegram.main.passcode.PasscodePath;
import ru.korniltsev.telegram.profile.edit.name.EditNamePath;
import ru.korniltsev.telegram.profile.edit.passcode.EditPasscode;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.List;

import static ru.korniltsev.telegram.common.AppUtils.getTmpFileForCamera;
import static rx.android.schedulers.AndroidSchedulers.mainThread;

@Singleton
public class MyProfilePresenter extends ViewPresenter<MyProfileView> implements AttachPanelPopup.Callback {
    final MyProfilePath path;
    final RXClient client;
    final PasscodeManager passcodeManager;
    private final ActivityOwner owner;
    private CompositeSubscription subscription;
    final ActivityOwner activity;
    private TdApi.User me;

    @Inject
    public MyProfilePresenter(MyProfilePath path, RXClient client, PasscodeManager passcodeManager, ActivityOwner owner, ActivityOwner activity) {
        this.path = path;
        this.client = client;
        this.passcodeManager = passcodeManager;
        this.owner = owner;
        this.activity = activity;
    }

    @Override
    protected void onLoad(Bundle savedInstanceState) {
        super.onLoad(savedInstanceState);
        subscription = new CompositeSubscription();
        try {
            me = client.getMeBlocking();
            getView()
                    .bindUser(
                            me, passcodeManager.passCodeEnabled());
        } catch (Exception e) {
            CrashlyticsCore.getInstance().logException(e);
        }

        subscription.add(
                owner.activityResult().subscribe(new ObserverAdapter<ActivityResult>() {
                    @Override
                    public void onNext(ActivityResult response) {
                        onActivityResult(response);
                    }
                }));
        subscription.add(client
                .getGlobalObservableWithBackPressure()
                .compose(new RXClient.FilterAndCastToClass<>(TdApi.UpdateUser.class))
                .filter(new Func1<TdApi.UpdateUser, Boolean>() {
                    @Override
                    public Boolean call(TdApi.UpdateUser updateUser) {
                        return updateUser.user.id == me.id;
                    }
                })
                .observeOn(mainThread()).subscribe(new ObserverAdapter<TdApi.UpdateUser>() {
                    @Override
                    public void onNext(TdApi.UpdateUser response) {
                        getView().bindUserAvatar(response.user);
                    }
                }));
    }

    private void onActivityResult(ActivityResult response) {
        int result = response.result;
        int request = response.request;
        if (result != Activity.RESULT_OK) {
            return;
        }
        if (request == AppUtils.REQUEST_TAKE_PHOTO_MY_AVATAR) {
            File f = AppUtils.getTmpFileForCamera();
            if (f.exists()) {
                //                rxChat.sendImage(f.getAbsolutePath());
                setAvatarImage(f.getAbsolutePath());
                getView()
                        .hideAttachPannel();
            }
        } else if (request == AppUtils.REQUEST_CHOOS_FROM_GALLERY_MY_AVATAR) {
            String picturePath = Utils.getGalleryPickedFilePath(getView().getContext(), response.data);
            if (picturePath != null) {
                setAvatarImage(picturePath);
                getView()
                        .hideAttachPannel();
            }
        }
    }

    private void setAvatarImage(final String filePath) {
        Observable.defer(new Func0<Observable<String>>() {
            @Override
            public Observable<String> call() {
                return Observable.just(filePath);
            }
        }).subscribeOn(Schedulers.io())
                .flatMap(new Func1<String, Observable<TdApi.TLObject>>() {
                    @Override
                    public Observable<TdApi.TLObject> call(String s) {
                        return client.sendRx(new TdApi.SetProfilePhoto(s, null));
                    }
                })
                .observeOn(mainThread())
                .subscribe(new ObserverAdapter<TdApi.TLObject>() {
                    @Override
                    public void onNext(TdApi.TLObject response) {
                        System.out.println();
                    }
                });
    }

    @Override
    public void dropView(MyProfileView view) {
        super.dropView(view);
        subscription.unsubscribe();
    }

    public void logout() {
        client.logout();
    }

    public void editName() {
        getView().post(new Runnable() {
            @Override
            public void run() {
                Flow.get(getView())
                        .set(new EditNamePath());
            }
        });
    }

    public void passcodeClicked() {
        if (passcodeManager.passCodeEnabled()) {
            Flow.get(getView())
                    .set(new PasscodePath(PasscodePath.TYPE_LOCK_TO_CHANGE));
        } else {
            Flow.get(getView())
                    .set(new EditPasscode());
        }
    }

    @Override
    public void sendImages(List<String> selectedImages) {
        if (selectedImages.size() == 1) {
            final String first = selectedImages.get(0);
            setAvatarImage(first);
            getView().hideAttachPannel();
        }
    }

    @Override
    public void chooseFromGallery() {
        String title = getView().getResources().getString(R.string.select_picture);
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        activity.expose()
                .startActivityForResult(Intent.createChooser(intent, title), AppUtils.REQUEST_CHOOS_FROM_GALLERY_MY_AVATAR);
    }

    @Override
    public void takePhoto() {
        File f = getTmpFileForCamera();
        f.delete();
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        activity.expose()
                .startActivityForResult(intent, AppUtils.REQUEST_TAKE_PHOTO_MY_AVATAR);
    }
}
