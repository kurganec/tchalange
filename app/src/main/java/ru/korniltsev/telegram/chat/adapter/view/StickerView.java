package ru.korniltsev.telegram.chat.adapter.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import mortar.dagger1support.ObjectGraphService;
import org.drinkless.td.libcore.telegram.TdApi;
import ru.korniltsev.telegram.core.app.MyApp;
import ru.korniltsev.telegram.core.emoji.DpCalculator;
import ru.korniltsev.telegram.core.emoji.Stickers;
import ru.korniltsev.telegram.core.picasso.RxGlide;

import javax.inject.Inject;

public class StickerView extends ImageView {
    private final int MAX_SIZE;
    final RxGlide picasso;
    final DpCalculator calc;
    final Stickers stickersInfo;

    private int height;
    private int width;

    public StickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final MyApp app = MyApp.from(context);
        calc = app.calc;
        picasso = app.rxGlide;
        stickersInfo = app.stickers;

            MAX_SIZE = Math.min(512, calc.dp(126 + 32));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    public void bind(final TdApi.Sticker s) {
        setImageBitmap(null);
//        height = MAX_HEIGHT;
        float ratio;
        if (s.width == 0 || s.height == 0){
            if (s.sticker .isLocal()){
                TdApi.Sticker mapped = stickersInfo.getMappedSticker(s.sticker.persistentId);
                if (mapped != null){
                    if (mapped.width == 0 || mapped.height == 0) {
                        ratio = 1f;
                    } else {
                        ratio = (float) mapped.width / mapped.height;
                    }
                } else {
                    ratio = 1f;
                }
            } else {
                ratio = 1f;
            }
        } else {
            ratio = (float) s.width / s.height;
        }
        if (ratio > 1) {
            width = MAX_SIZE;
            height = (int) (width / ratio);
        } else {
            height = MAX_SIZE;
            width = (int) (height * ratio);
        }


//        width = (int) (ratio * height);
        if (isValidThumb(s)){
            picasso.loadPhoto(s.thumb.photo, true)
//                    .resize(width, height)
                    .priority(Picasso.Priority.HIGH)
                    .into(this, new Callback() {
                        @Override
                        public void onSuccess() {
                            picasso.loadPhoto(s.sticker, true)
                                    .placeholder(getDrawable())
//                                    .resize(width, height)
                                    .into(StickerView.this);
                        }

                        @Override
                        public void onError() {

                        }
                    });
        } else {
            picasso.loadPhoto(s.sticker, true)
//                    .resize(width, height)
                    .into(StickerView.this);

        }

    }

    private boolean isValidThumb(TdApi.Sticker s) {
        return s.thumb.photo.id != 0;
    }


}
