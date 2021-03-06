package ru.korniltsev.telegram.emoji;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import com.crashlytics.android.core.CrashlyticsCore;
import mortar.dagger1support.ObjectGraphService;
import org.drinkless.td.libcore.telegram.TdApi;
import ru.korniltsev.telegram.core.app.MyApp;
import ru.korniltsev.telegram.core.emoji.DpCalculator;
import ru.korniltsev.telegram.core.emoji.Stickers;
import ru.korniltsev.telegram.core.emoji.images.Emoji;
import ru.korniltsev.telegram.emoji.strip.EmojiPagerStripView;
import ru.korniltsev.telegram.core.picasso.RxGlide;
import ru.korniltsev.telegram.utils.R;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmojiKeyboardView extends LinearLayout implements Emoji.Listener {

    public static final String LAST_CLICK_STICKER = "sticker";
    public static final String LAST_CLICK_EMOJI = "emoji";
    public static final String PREF_LAST_CLICK = "pref_last_click";
    public final RecentSmiles recentEmoji;
    public final int displayWidth;
    public final RecentSmiles recentStickers;
    private final SharedPreferences prefs;
    private ViewPager pager;
    final Emoji emoji;
    final DpCalculator calc;
    final Stickers stickers;
    final RxGlide picasso;

    public final LayoutInflater viewFactory;
    private EmojiPagerStripView tabs;
    private Adapter adapter;

    public EmojiKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        recentEmoji = new RecentSmiles(context, "RecentEmoji", 40);
        recentStickers = new RecentSmiles(context, "RecentStickers", 15);
        prefs = context.getSharedPreferences("EmojiKeyboardView", Context.MODE_PRIVATE);


        viewFactory = LayoutInflater.from(context);
        final MyApp app = MyApp.from(context);
        displayWidth = app.displayWidth;
        stickers = app.stickers;
        picasso = app.rxGlide;
        emoji = app.emoji;
        calc = app.calc;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        pager = ((ViewPager) findViewById(R.id.pager));

        tabs = ((EmojiPagerStripView) findViewById(R.id.tabs));
        tabs.init(pager, new Runnable() {
            @Override
            public void run() {
                callback.backspaceClicked();
            }
        }, stickers.getStickers(), new Runnable() {
            @Override
            public void run() {
                if (adapter.recentEmojiIds.isEmpty()) {
                    pager.setCurrentItem(1, true);
                } else {
                    pager.setCurrentItem(0, true);
                }
            }
        });
        adapter = new Adapter(getContext());
        pager.setAdapter(adapter);

        if (LAST_CLICK_STICKER.equals(prefs.getString(PREF_LAST_CLICK, LAST_CLICK_EMOJI))) {
            pager.setCurrentItem(6, false);
        } else if (adapter.recentEmojiIds.size() == 0) {
            pager.setCurrentItem(1);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        emoji.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        emoji.removeListener(this);
    }

    public CallBack callback;

    public void setCallback(CallBack callback) {
        this.callback = callback;
    }

    @Override
    public void pageLoaded(int page) {
        for (int i = 0; i < pager.getChildCount(); ++i) {
            final View c = pager.getChildAt(i);
            if (c instanceof GridView) {
                final GridView g = (GridView) c;
                final BaseAdapter a = (BaseAdapter) g.getAdapter();
                a.notifyDataSetChanged();
            }
        }
    }

    public interface CallBack {
        void backspaceClicked();

        void emojiClicked(long code);

        void stickerCLicked(TdApi.Sticker sticker);
    }

    class Adapter extends PagerAdapter {

        private final LayoutInflater viewFactory;
        private final ArrayList<Long> recentEmojiIds;
        private Context context;

        public Adapter(Context context) {
            this.context = context;
            final List<RecentSmiles.Entry> recent = recentEmoji.getRecent();

            recentEmojiIds = new ArrayList<>();
            for (int i = 0, recentSize = recent.size(); i < recentSize; i++) {
                RecentSmiles.Entry entry = recent.get(i);
                try {
                    recentEmojiIds.add(
                            Long.parseLong(entry.code));
                } catch (NumberFormatException e) {
                    CrashlyticsCore.getInstance()
                            .logException(e);
                }
            }
            viewFactory = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return /*recent*/ 1 +
                    /*emoji*/ 5 +
                    /* stickers*/ 1;
        }

        @Override
        public View instantiateItem(ViewGroup container, int position) {
            if (position == 0) {
                final long[] longs = new long[recentEmojiIds.size()];
                for (int i = 0, idsSize = recentEmojiIds.size(); i < idsSize; i++) {
                    Long id = recentEmojiIds.get(i);
                    longs[i] = id;
                }

                return createGridPage(container, position, new EmojiPageAdapter(longs), R.dimen.emoji_size);
            } else if (position == getCount() - 1) {
                final List<TdApi.StickerSet> stickers = EmojiKeyboardView.this.stickers.getStickers();
                List<List<TdApi.Sticker>> sets = new ArrayList<>();
                for (TdApi.StickerSet s : stickers) {
                    sets.add(
                            Arrays.asList(s.stickers));
                }
                final ArrayList<TdApi.Sticker> recentStickers = getRecentStickers(stickers);
                GridView res = createGridPage(container, position, new StickerAdapter(EmojiKeyboardView.this, sets, recentStickers), R.dimen.sticker_size);
                res.setVerticalSpacing(calc.dp(16));
                res.setClipToPadding(false);
                int dip8 = calc.dp(8);
                res.setPadding(dip8 / 2, dip8, dip8 / 2, dip8);
                tabs.initStickerScroll(res);
                return res;
            } else {
                final long[] data = Emoji.data[position];
                return createGridPage(container, position, new EmojiPageAdapter(data), R.dimen.emoji_size);
            }
        }

        private GridView createGridPage(ViewGroup container, final int position1, BaseAdapter adapter, int columnSizeResId) {
            int columnWidth = getContext().getResources().getDimensionPixelSize(columnSizeResId);
            GridView view = (GridView) viewFactory.inflate(R.layout.keyboard_page_emoji, container, false);
            view.setColumnWidth(columnWidth);
            view.setAdapter(adapter);
            container.addView(view);
            view.setTag(position1);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }

    @NonNull
    private ArrayList<TdApi.Sticker> getRecentStickers(List<TdApi.StickerSet> stickers) {
        final List<RecentSmiles.Entry> recent = recentStickers.getRecent();
        final ArrayList<TdApi.Sticker> result = new ArrayList<>();
        recent:
        for (RecentSmiles.Entry entry : recent) {
            final String code = entry.code;
            for (TdApi.StickerSet set : stickers) {
                for (TdApi.Sticker s : set.stickers) {
                    if (s.sticker.persistentId.equals(code)) {
                        result.add(s);
                        continue recent;
                    }
                }
            }
        }
        return result;
    }

    class EmojiPageAdapter extends BaseAdapter {

        private long[] longs;
        ;

        public EmojiPageAdapter(long[] longs) {

            this.longs = longs;
        }

        public EmojiAdapterVH onCreateViewHolder(ViewGroup parent) {
            View v = viewFactory.inflate(R.layout.grid_item_emoji, parent, false);
            return new EmojiAdapterVH(EmojiKeyboardView.this, v);
        }

        public void onBindViewHolder(EmojiAdapterVH holder, int position) {
            holder.o = longs[position];
            Drawable d = emoji.getEmojiBigDrawable(longs[position]);
            holder.img.setImageDrawable(d);
        }

        @Override
        public int getCount() {
            return longs.length;
        }

        @Override
        public Object getItem(int position) {
            return longs[position];
        }

        @Override
        public long getItemId(int position) {
            return longs[position];
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            EmojiAdapterVH vh;
            if (convertView == null) {
                vh = onCreateViewHolder(parent);
                vh.img.setTag(vh);
            } else {
                vh = (EmojiAdapterVH) convertView.getTag();
            }
            onBindViewHolder(vh, position);
            return vh.img;
        }
    }

    public void setLastClick(String source) {
        prefs.edit()
                .putString(PREF_LAST_CLICK, source)
                .apply();
    }
}
