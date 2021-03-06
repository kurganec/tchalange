package ru.korniltsev.telegram.main.passcode;

import android.os.Bundle;
import mortar.ViewPresenter;
import ru.korniltsev.telegram.core.mortar.ActivityOwner;
import ru.korniltsev.telegram.core.passcode.PasscodeManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PasscodePresenter extends ViewPresenter<PasscodeView> {
    final PasscodePath path;
    final ActivityOwner owner;
    final PasscodeManager passcodeManager;


    @Inject
    public PasscodePresenter(PasscodePath path, ActivityOwner owner, PasscodeManager passcodeManager) {
        this.path = path;
        this.owner = owner;
        this.passcodeManager = passcodeManager;
    }

    @Override
    protected void onLoad(Bundle savedInstanceState) {
        super.onLoad(savedInstanceState);

        getView()
                .bindPasscode(path);
    }

    @Override
    public void dropView(PasscodeView view) {
        super.dropView(view);
        view.hideKeyboard();
    }


    public boolean onBackPressed() {
        if (path.actionType == PasscodePath.TYPE_LOCK){
            owner.expose().finish();
            return true;
        }
        return false;
    }

//    public void setNewPassword(@NonNull String firstPassword) {
//        passcodeManager.setPassword(firstPassword);
//        passcodeManager.setPasscodeEnabled(true);
//        Flow.get(getView())
//                .goBack();
//    }
}
