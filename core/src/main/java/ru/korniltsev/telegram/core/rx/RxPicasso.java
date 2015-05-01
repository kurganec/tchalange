package ru.korniltsev.telegram.core.rx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.RequestHandler;
import com.squareup.picasso.Transformation;
import org.drinkless.td.libcore.telegram.TdApi;
import ru.korniltsev.telegram.core.views.AvatarStubColors;
import ru.korniltsev.telegram.utils.R;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RxPicasso {

    //    private final RXClient client;
    //    private final RXAuthState auth;
    private final Picasso picasso;
    private final LruCache memoryCache;

    public RxPicasso(Context ctx, RXClient client, RXAuthState auth) {

        memoryCache = new LruCache(ctx);
        picasso = new Picasso.Builder(ctx)
                .memoryCache(memoryCache)
                .addRequestHandler(new RXRequestHandler(ctx, client))
                .build();
        auth.listen()
                .filter(new Func1<RXAuthState.AuthState, Boolean>() {
                    @Override
                    public Boolean call(RXAuthState.AuthState s) {
                        return s == RXAuthState.AuthState.LOGOUT;
                    }
                })
                .subscribe(new Action1<RXAuthState.AuthState>() {
                    @Override
                    public void call(RXAuthState.AuthState s) {
                        memoryCache.clear();
                    }
                });
    }

    public Picasso getPicasso() {
        return picasso;
    }

    private static final RxPicasso.StubAware STUB_AWARE_GROUP_CHAT = new StubAware() {
        @Override
        public String needStub(TdApi.TLObject o) {
            TdApi.GroupChat chat = (TdApi.GroupChat) o;
            String title = chat.title;
            if (title.length() > 0) {
                return String.valueOf(
                        Character.toUpperCase(title.charAt(0)));
            }
            return "";
        }
    };

    private static final RxPicasso.StubAware STUB_AWARE_USER = new StubAware() {
        @Override
        public String needStub(TdApi.TLObject o) {
            TdApi.User user = (TdApi.User) o;
            StringBuilder sb = new StringBuilder();
            if (user.firstName.length() > 0) {
                sb.append(
                        Character.toUpperCase(
                                user.firstName.charAt(0)));
            }
            if (user.lastName.length() > 0) {
                sb.append(
                        Character.toUpperCase(
                                user.lastName.charAt(0)));
            }
            return sb.toString();
        }
    };

    /**
     * @param o    can badge TdApi.Chat or TdApi.User
     * @param size size of target view. The loaded bitmap will be resized to the square of that size
     * @return request builder that can be loaded into some view or target
     */
    public RequestCreator loadAvatar(TdApi.TLObject o, int size) {
        int colorId;
        TdApi.File f;
        TdApi.TLObject stubTarget;
        StubAware stubFactory;
        if (o instanceof TdApi.Chat) {
            TdApi.ChatInfo type = ((TdApi.Chat) o).type;
            if (type instanceof TdApi.GroupChatInfo) {
                TdApi.GroupChatInfo groupChatInfo = (TdApi.GroupChatInfo) type;
                f = groupChatInfo.groupChat.photoSmall;
                colorId = groupChatInfo.groupChat.id;
                stubFactory = STUB_AWARE_GROUP_CHAT;
                stubTarget = groupChatInfo.groupChat;
            } else if (type instanceof TdApi.PrivateChatInfo) {
                TdApi.PrivateChatInfo privateChatInfo = (TdApi.PrivateChatInfo) type;
                f = privateChatInfo.user.photoSmall;
                colorId = privateChatInfo.user.id;
                stubFactory = STUB_AWARE_USER;
                stubTarget = privateChatInfo.user;
            } else {
                throw new IllegalArgumentException();
            }
        } else if (o instanceof TdApi.User) {
            TdApi.User user = (TdApi.User) o;
            f = user.photoSmall;
            colorId = user.id;
            stubFactory = STUB_AWARE_USER;
            stubTarget = user;
        } else {
            throw new IllegalArgumentException();
        }

        RequestCreator res;
        if (f instanceof TdApi.FileEmpty) {
            TdApi.FileEmpty fileEmpty = (TdApi.FileEmpty) f;
            int id = fileEmpty.id;
            Uri uri;
            if (id == 0) {
                uri = RXRequestHandler.create(stubFactory.needStub(stubTarget), colorId);
                res = picasso.load(uri);
            } else {
                uri = RXRequestHandler.create(fileEmpty);
                res = picasso.load(uri)
                        .transform(new CircleTransformation());
            }
        } else {
            TdApi.FileLocal local = (TdApi.FileLocal) f;
            File file = new File(local.path);
            res = picasso.load(file)
                    .transform(new CircleTransformation());
        }
        res.resize(size, size);
        return res;
    }

    private static class RXRequestHandler extends RequestHandler {

        public static final String URI_SCHEME = "telegram";
        public static final String URI_PARAM_ID = "id";
        public static final String URI_PARAM_STUB = "stub";
        public static final int TIMEOUT = 30000;
        private final int stubTextSize;

        public static Uri create(TdApi.FileEmpty f) {
            return new Uri.Builder()
                    .scheme(URI_SCHEME)
                    .appendQueryParameter(URI_PARAM_ID, String.valueOf(f.id))
                    .build();
        }

        public static Uri create(String stub, int id) {
            return new Uri.Builder()
                    .scheme(URI_SCHEME)
                    .appendQueryParameter(URI_PARAM_ID, String.valueOf(id))
                    .appendQueryParameter(URI_PARAM_STUB, stub)
                    .build();
        }

        final RXClient client;
        final ThreadLocal<TextPaint> textPaints = new ThreadLocal<TextPaint>() {
            @Override
            protected TextPaint initialValue() {
                TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setTextSize(stubTextSize);
                return textPaint;
            }
        };

        final ThreadLocal<Paint> paints = new ThreadLocal<Paint>() {
            @Override
            protected Paint initialValue() {
                return new Paint(Paint.ANTI_ALIAS_FLAG);
            }
        };

        final ThreadLocal<RectF> rects = new ThreadLocal<RectF>() {
            @Override
            protected RectF initialValue() {
                return new RectF();
            }
        };

        public RXRequestHandler(final Context ctx, RXClient client) {
            this.client = client;
            stubTextSize = ctx.getResources().getDimensionPixelSize(R.dimen.avatar_text_size);
        }

        @Override
        public boolean canHandleRequest(Request data) {
            return URI_SCHEME.equals(data.uri.getScheme());
        }

        @Override
        public Result load(Request request, int networkPolicy) throws IOException {
            String strId = request.uri.getQueryParameter(URI_PARAM_ID);
            if (strId == null) {
                throw new IllegalArgumentException("wrong uri");
            }
            int id = Integer.parseInt(strId);

            String stub = request.uri.getQueryParameter(URI_PARAM_STUB);
            if (stub == null) {
                return loadFileEmpty(id);
            } else {
                return loadStub(request, id, stub);
            }
        }

        private Result loadStub(Request request, int id, String stub) {
            int size = request.targetWidth;
            int colorFor = AvatarStubColors.getColorFor(id);

            TextPaint stubTextPaint = textPaints.get();
            stubTextPaint.setColor(Color.WHITE);//todo!!!!
            Paint paint = paints.get();
            paint.setColor(colorFor);

            StaticLayout staticLayout = new StaticLayout(stub, stubTextPaint, size, Layout.Alignment.ALIGN_CENTER, 1, 0, false);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);//todo mb less?
            Canvas canvas = new Canvas(bmp);
            RectF r = rects.get();
            r.set(0, 0, size, size);
            canvas.drawOval(r, paint);
            int height = staticLayout.getHeight();
            int p = (size - height) / 2;
            canvas.save();
            canvas.translate(0, p);
            staticLayout.draw(canvas);
            canvas.restore();
            return new Result(bmp, Picasso.LoadedFrom.NETWORK);
        }

        private Result loadFileEmpty(final int id) throws IOException {
            TdApi.UpdateFile first;
            try {
                Observable<TdApi.UpdateFile> specificFileUpdate = client.filesUpdates()
                        .filter(new Func1<TdApi.UpdateFile, Boolean>() {
                            @Override
                            public Boolean call(TdApi.UpdateFile u) {
                                return u.fileId == id;
                            }
                        })
                        .first();
                client.sendSilently(new TdApi.DownloadFile(id));
                first = specificFileUpdate.toBlocking()
                        .toFuture()
                        .get(TIMEOUT, TimeUnit.MILLISECONDS);
                FileInputStream res = new FileInputStream(new File(first.path));
                return new Result(res, Picasso.LoadedFrom.NETWORK);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException();
            } catch (ExecutionException | TimeoutException e) {
                throw new IOException();
            }
        }
    }

    private static class CircleTransformation implements Transformation {
        @Override
        public Bitmap transform(Bitmap source) {
            int width = source.getWidth();
            int height = source.getHeight();
            Bitmap transformed = Bitmap.createBitmap(width, height, source.getConfig());
            Canvas canvas = new Canvas(transformed);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            canvas.drawCircle(width / 2, height / 2, width / 2, p);
            source.recycle();
            return transformed;
        }

        @Override
        public String key() {
            return "rounded";
        }
    }

    public interface StubAware {
        String needStub(TdApi.TLObject o);
    }
}
