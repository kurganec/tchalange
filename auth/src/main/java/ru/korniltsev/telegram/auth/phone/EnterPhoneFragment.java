package ru.korniltsev.telegram.auth.phone;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.Toast;
import flow.Flow;
import junit.framework.Assert;
import mortar.MortarScope;
import mortar.ViewPresenter;
import org.drinkless.td.libcore.telegram.TdApi;
import ru.korniltsev.telegram.auth.R;
import ru.korniltsev.telegram.auth.code.EnterCode;
import ru.korniltsev.telegram.auth.country.Countries;
import ru.korniltsev.telegram.auth.country.SelectCountry;
import ru.korniltsev.telegram.core.Utils;
import ru.korniltsev.telegram.core.adapters.ObserverAdapter;
import ru.korniltsev.telegram.core.app.RootModule;
import ru.korniltsev.telegram.core.flow.pathview.BasePath;
import ru.korniltsev.telegram.core.mortar.ActivityOwner;
import ru.korniltsev.telegram.core.mortar.mortarscreen.WithModule;
import ru.korniltsev.telegram.core.rx.RXClient;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subscriptions.Subscriptions;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.Serializable;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

/**
 * Created by korniltsev on 21/04/15.
 */
@WithModule(EnterPhoneFragment.Module.class)
public class EnterPhoneFragment extends BasePath implements Serializable {

    private Countries.Entry c;

    public void setCountry(Countries.Entry c) {

        this.c = c;
    }

    @dagger.Module(injects = EnterPhoneView.class, addsTo = RootModule.class)
    public static class Module {

    }

    @Override
    public int getRootLayout() {
        return R.layout.fragment_set_phone_number;
    }

    @Singleton
    static class Presenter extends ViewPresenter<EnterPhoneView> {
        private RXClient client;
        private Observable<TdApi.TLObject> sendPhoneRequest;
        private String sentPhonenumber;

        private Subscription subscribtion = Subscriptions.empty();
        private ProgressDialog pd;

        @Inject
        public Presenter(RXClient client, ActivityOwner a) {
            this.client = client;
        }

        @Override
        protected void onLoad(Bundle savedInstanceState) {
            super.onLoad(savedInstanceState);
            EnterPhoneFragment f = get(getView().getContext());
            Countries.Entry country;//todo save selected to pref
            if (f.c != null) {
                country = f.c;
            } else {
                country = new Countries(getView().getContext())//todo new
                        .getForCode(Countries.RU_CODE);
            }
            getView().countrySelected(country);
            if (sendPhoneRequest != null) {
                subscribe();
            }
        }

        @Override
        protected void onEnterScope(MortarScope scope) {
            super.onEnterScope(scope);
        }

        @Override
        protected void onSave(Bundle outState) {
            super.onSave(outState);
        }

        @Override
        protected void onExitScope() {
            super.onExitScope();
        }

        public void selectCountry() {
            Flow.get(getView())
                    .set(new SelectCountry());
            Utils.hideKeyboard(
                    getView().getPhoneCode());
        }

        public void sendCode(final String phoneNumber) {
            assertNull(sendPhoneRequest);
            sendPhoneRequest = client.logoutHelper()
                    .filter(new Func1<TdApi.AuthState, Boolean>() {
                        @Override
                        public Boolean call(TdApi.AuthState authState) {
                            return authState instanceof TdApi.AuthStateWaitSetPhoneNumber;
                        }
                    })
                    .flatMap(new Func1<TdApi.AuthState, Observable<TdApi.TLObject>>() {
                        @Override
                        public Observable<TdApi.TLObject> call(TdApi.AuthState authState) {
                            return client.sendCachedRXUI(new TdApi.AuthSetPhoneNumber(phoneNumber));
                        }
                    });

            sentPhonenumber = phoneNumber;
            subscribe();
        }

        private void subscribe() {
            pd = new ProgressDialog(getView().getContext());
            subscribtion = sendPhoneRequest.subscribe(new ObserverAdapter<TdApi.TLObject>() {
                @Override
                public void onNext(TdApi.TLObject response) {

                    if (response instanceof TdApi.AuthStateWaitSetCode) {
                        Flow.get(Presenter.this.getView().getContext())
                                .set(new EnterCode(sentPhonenumber));
                    } else {
                        Toast.makeText(getView().getContext(), "Registration is not implemented", Toast.LENGTH_LONG).show();
                    }
                    sendPhoneRequest = null;
                    pd.dismiss();
                }

                @Override
                public void onError(Throwable th) {
                    sendPhoneRequest = null;
                    pd.dismiss();
                    getView().showError(th.getMessage());
                }
            });
            pd.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    sendPhoneRequest = null;
                    subscribtion.unsubscribe();
                }
            });
            pd.show();
        }

        @Override
        public void dropView(EnterPhoneView view) {
            super.dropView(view);
            subscribtion.unsubscribe();
            if (pd != null) {
                pd.dismiss();
                pd = null;
            }
        }
    }

    //    private EditText btnSelectCountry;
    //    private EditText phoneCode;
    //    private EditText userPhone;
    //    private Toolbar toolbar;
    //
    //    @Override
    //    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    //        return inflater.inflate(R.layout.fragment_set_phone_number, container, false);
    //    }
    //
    //    @Override
    //    public void onViewCreated(View view, Bundle savedInstanceState) {
    //        initToolbar(view)
    //                .setTitle(R.string.phone_number)
    //                .addMenuItem(R.menu.send_code, R.id.menu_send_code, new Runnable() {
    //                    @Override
    //                    public void run() {
    //                        sendCode();
    //                    }
    //                });
    //
    //        btnSelectCountry = (EditText) view.findViewById(R.id.btn_select_country);
    //        phoneCode = (EditText) view.findViewById(R.id.country_phone_code);//todo editable
    //        userPhone = (EditText) view.findViewById(R.id.user_phone);
    //        btnSelectCountry.setOnClickListener(new View.OnClickListener() {
    //            @Override
    //            public void onClick(View view) {
    //                openCountrySelection();
    //            }
    //        });
    //        Countries.Entry russia = new Countries(getActivity())//todo new
    //                .getForCode(Countries.RU_CODE);//todo save selected to pref
    //        countrySelected(russia);
    //    }
    //
    //    private void sendCode() {
    //        getRxClient().sendRXUI(new TdApi.AuthSetPhoneNumber(getPhoneNumber()))
    //        .subscribe(new ObserverAdapter<TdApi.TLObject>() {
    //            @Override
    //            public void onNext(TdApi.TLObject response) {
    //                if (response instanceof TdApi.AuthStateWaitSetCode) {
    //                    FlowLike.from(getActivity())
    //                            .push(new SetCodeFragment(), "set code");
    //                }
    //            }
    //
    //        });
    //    }
    //
    //    private String getPhoneNumber() {
    //        return textFrom(phoneCode) + textFrom(userPhone);
    //    }
    //
    //    private void countrySelected(Countries.Entry c) {
    //        btnSelectCountry.setText(c.name);
    //        phoneCode.setText(c.phoneCode);
    //    }
    //
    //    private void openCountrySelection() {
    //        FlowLike.from(getActivity())
    //                .push(new CountrySelectFragment(), "select country");
    //    }
    //
    //    @Override
    //    public void onResult(Object result) {
    //        countrySelected((Countries.Entry) result);
    //    }
}
