package ru.korniltsev.telegram.core.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.ImageView;
import com.crashlytics.android.core.CrashlyticsCore;
import junit.framework.Assert;
import mortar.dagger1support.ObjectGraphService;
import org.drinkless.td.libcore.telegram.TdApi;
import ru.korniltsev.telegram.core.picasso.RxGlide;
import ru.korniltsev.telegram.utils.R;


public class AvatarView extends ImageView {

    private int size;

    public final RxGlide picasso2;
    public TdApi.TLObject boundObject;

    public AvatarView(Context context, int size, RxGlide glide) {
        super(context);
        picasso2 = glide;// app.rxGlide;//ObjectGraphService.getObjectGraph(context)
                //.get(RxGlide.class);

//        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AvatarView);
//        size = a.getDimensionPixelSize(R.styleable.AvatarView_size, -1);
//        a.recycle();
        this.size = size;
        Assert.assertTrue(size != -1 && size > 0);


        setScaleType(ScaleType.CENTER_CROP);
    }

    public AvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        picasso2 = ObjectGraphService.getObjectGraph(context)
                .get(RxGlide.class);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AvatarView);
        size = a.getDimensionPixelSize(R.styleable.AvatarView_size, -1);
        a.recycle();
        Assert.assertTrue(size != -1 && size > 0);


        setScaleType(ScaleType.CENTER_CROP);

    }

    private boolean noPlaceholder = false;

    public void setNoPlaceholder(boolean noPlaceholder) {
        this.noPlaceholder = noPlaceholder;
    }

    public boolean isNoPlaceholder() {
        return noPlaceholder;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(size, size);
    }



    /**
     * @param o can be TdApi.User or TdApi.Chat
     */
    public void loadAvatarFor(@NonNull TdApi.TLObject o) {
        this.boundObject = o;
        if (o == null) {
            CrashlyticsCore.getInstance()
                    .logException(new NullPointerException());
            setImageBitmap(null);
            picasso2.getPicasso().cancelRequest(this);
            return;
        }
//        assertNotNull(o);
        if (!noPlaceholder){
            setImageBitmap(null);
        }
        if (o instanceof TdApi.User) {
            picasso2.loadAvatarForUser((TdApi.User) o, size, this);
            //                    .transform(ROUND)
            //                    .into(this);
        } else {
            picasso2.loadAvatarForChat((TdApi.Chat) o, size, this);
            //                    .transform(new RoundTransformation())
            //                    .into(this);
        }
    }

    @Override
    public void requestLayout() {
        if (getMeasuredWidth() == 0){
            super.requestLayout();
        }
    }

    public int getSize() {
        return size;
    }

    public void setStub(TdApi.MessageContact msg) {
        picasso2.getPicasso().cancelRequest(this);
        picasso2.setStub(msg, size, this);
    }
}
