package ru.korniltsev.telegram.core.flow.pathview;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.FrameLayout;
import flow.path.Path;
import ru.korniltsev.telegram.core.mortar.mortarscreen.ModuleFactory2;

public abstract class BasePath extends Path implements ModuleFactory2 {
    public abstract int getRootLayout();

    public int getBackgroundColor() {
        return Color.WHITE;
    }

    @Nullable
    public View constructViewManually(Context ctx, FrameLayout root) {
        return null;
    }
}
