package ru.korniltsev.telegram.chat.adapter.view;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.crashlytics.android.core.CrashlyticsCore;
import junit.framework.Assert;
import mortar.dagger1support.ObjectGraphService;
import org.drinkless.td.libcore.telegram.TdApi;
import ru.korniltsev.telegram.chat.R;
import ru.korniltsev.telegram.common.AppUtils;
import ru.korniltsev.telegram.core.app.MyApp;
import ru.korniltsev.telegram.core.rx.RxDownloadManager;
import ru.korniltsev.telegram.core.picasso.RxGlide;
import ru.korniltsev.telegram.core.views.DownloadView;

import javax.inject.Inject;
import java.io.File;

import static ru.korniltsev.telegram.core.views.DownloadView.Config.FINAL_ICON_EMPTY;

public class DocumentView extends LinearLayout{

//    private ImageView btnPlay;
    private ImageView documentThumb;
    private TextView documentName;
    private TextView documentProgress;
//    private View clicker;
//    private Subscription subscription = Subscriptions.empty();
    public final RxDownloadManager downloader;
    private final RxGlide picasso;
    private TdApi.Document document;
    private DownloadView downloadView;
    private final BlurTransformation blur;

    public DocumentView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final MyApp app = MyApp.from(context);
        picasso = app.rxGlide;
        downloader = app.downloadManager;
        blur = new BlurTransformation(12);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        documentName = ((TextView) findViewById(R.id.document_name));
        documentProgress = ((TextView) findViewById(R.id.document_progress));
        documentThumb = (ImageView) findViewById(R.id.image_document_thumb);
        downloadView = (DownloadView) findViewById(R.id.download_view);
//        clicker = findViewById(R.id.thumb_and_btn_root);
//        clicker.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (downloader.isDownloaded(document.document)) {
//                    open();
//                } else {
//                    downloader.download((TdApi.FileEmpty) document.document);
//                    set(document);//update ui
//                }
//            }
//        });
    }

//    private void open() {
//        Assert.fail();
//    }

    public void set(TdApi.Document d) {
        this.document = d;
        final boolean image = document.mimeType.startsWith("image") && document.thumb.photo.id != 0;
//        boolean shouldClearPlaceHolder = true;
        if (image) {
            documentThumb.setVisibility(View.VISIBLE);
            //check if downloaded
//            if (!downloader.isDownloaded(document.document)){
                picasso.loadPhoto(document.thumb.photo, false)
                        .transform(blur)
                        .into(documentThumb);
//                shouldClearPlaceHolder = false;
//            } else {
                //we load original image in onFinished
//            }
        } else {
            documentThumb.setVisibility(View.GONE);
        }
        documentName.setText(document.fileName);
        documentProgress.setText("");
        DownloadView.Config cfg;
        if (image){
            cfg = new DownloadView.Config(FINAL_ICON_EMPTY, FINAL_ICON_EMPTY, false, false, 48);
        } else {
            cfg = new DownloadView.Config(R.drawable.ic_file, FINAL_ICON_EMPTY, true, false, 38);
        }
//        final boolean finalShouldClearPlaceHolder = shouldClearPlaceHolder;
        downloadView.bind(d.document, cfg, new DownloadView.CallBack() {
            @Override
            public void onProgress(TdApi.UpdateFileProgress p) {
                documentProgress.setText(getResources().getString(R.string.downloading_kb, AppUtils.kb(p.ready), AppUtils.kb(p.size)));
            }

            @Override
            public void onFinished(TdApi.File e, boolean b) {
                documentProgress.setText(getResources().getString(R.string.downloaded_kb, AppUtils.kb(e.size)));
//                if (image) {
//                    if (finalShouldClearPlaceHolder){
//                        picasso.loadPhoto(e, false)
//                                .into(documentThumb);
//                    } else {
//                        picasso.loadPhoto(e, false)
//                                .noPlaceholder()
//                                .into(documentThumb);
//                    }
//                }
            }

            @Override
            public void play(TdApi.File e) {
                openDocument(e);
            }
        }, this);

    }

    private void openDocument(TdApi.File e) {
        Assert.assertTrue(e.isLocal());
        File f = new File(e.path);
        String name = document.fileName;
        if (name != null && name.equals("")) {
            name = null;
        }
        File target = downloader.exposeFile(f, Environment.DIRECTORY_DOWNLOADS, name);

        String type = document.mimeType;
        if (target.getName().endsWith(".apk")
                && "application/octet-stream".equals(type)){
            type = "application/vnd.android.package-archive";
        }
//        "application/vnd.android.package-archive"
        Intent intent = new Intent(Intent.ACTION_VIEW);

        if (type.startsWith("image")){
            final Uri uri = Uri.parse("file://" + target.getAbsolutePath());
            intent.setDataAndType(uri, "image/*");
        } else {
            Uri data = Uri.fromFile(target);
            intent.setDataAndType(data, type);
        }

        try {
            getContext()
                    .startActivity(intent);
        } catch (ActivityNotFoundException e1) {
            AppUtils.showNoActivityError(getContext());
            CrashlyticsCore.getInstance().logException(e1);
        }
    }
}
