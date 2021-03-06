/*
 * Copyright 2014 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.korniltsev.telegram.core.flow.pathview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Debug;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import com.crashlytics.android.core.CrashlyticsCore;
import dagger.ObjectGraph;
import flow.Flow;
import flow.path.Path;
import flow.path.PathContainer;
import flow.path.PathContext;
import flow.path.PathContextFactory;
import mortar.dagger1support.ObjectGraphService;
import ru.korniltsev.telegram.core.emoji.DpCalculator;
import ru.korniltsev.telegram.core.flow.utils.Utils;

import static flow.Flow.Direction.REPLACE;
import static ru.korniltsev.telegram.core.Utils.event;

/**
 * Provides basic right-to-left transitions. Saves and restores view state.
 * Uses {@link PathContext} to allow customized sub-containers.
 */
public class SimplePathContainer extends PathContainer {
  private final PathContextFactory contextFactory;

  public SimplePathContainer(int tagKey, PathContextFactory contextFactory) {
    super(tagKey);
    this.contextFactory = contextFactory;
  }

  @Override protected void performTraversal(final ViewGroup containerView,
      final TraversalState traversalState, final Flow.Direction direction,
      final Flow.TraversalCallback callback) {
//   Debug.startMethodTracing("traversal");

    final PathContext context;
    final PathContext oldPath;
    if (containerView.getChildCount() > 0) {
      oldPath = PathContext.get(containerView.getChildAt(0).getContext());
    } else {
      oldPath = PathContext.root(containerView.getContext());
    }

    BasePath to = (BasePath) traversalState.toPath();

    View newView;
    context = PathContext.create(oldPath, to, contextFactory);

//    long start = System.nanoTime();
//    Debug.startMethodTracing("create_view");
    newView = to.constructViewManually(context, (FrameLayout) containerView);
    if (newView == null){
      int layout = to.getRootLayout();
      newView = LayoutInflater.from(context)
              .cloneInContext(context)
              .inflate(layout, containerView, false);
    }
//    Debug.stopMethodTracing();
//    long end = System.nanoTime();
//    ru.korniltsev.telegram.core.Utils.logDuration(start, end, "view inflation");

    View fromView =
            null;
    if (traversalState.fromPath() != null) {
      fromView = containerView.getChildAt(0);
      traversalState.saveViewState(fromView);
    }
    traversalState.restoreViewState(newView);

    boolean skipAnimation;
    if (newView instanceof NoAnimationTraversal) {
      skipAnimation = ((NoAnimationTraversal) newView).shouldSkipAnimation();
    } else {
      skipAnimation = false;
    }

//    Debug.startMethodTracing("animation");
    event("newView is " + newView.getClass().getSimpleName());
    if (fromView == null || direction == REPLACE || skipAnimation) {
      containerView.removeAllViews();
      containerView.addView(newView);
      oldPath.destroyNotIn(context, contextFactory);
      callback.onTraversalCompleted();
//     Debug.stopMethodTracing();
    } else {
      containerView.addView(newView);
      final View finalFromView = fromView;
      Utils.waitForMeasure(newView, new Utils.OnMeasuredCallback() {
        @Override
        public void onMeasured(View view, int width, int height) {
          runAnimation(containerView, finalFromView, view, direction, new Flow.TraversalCallback() {
            @Override
            public void onTraversalCompleted() {
              containerView.removeView(finalFromView);
              oldPath.destroyNotIn(context, contextFactory);
              callback.onTraversalCompleted();
//             Debug.stopMethodTracing();
            }
          });
        }
      });
    }
  }



  private void runAnimation(final ViewGroup container, final View from, final View to,
      Flow.Direction direction, final Flow.TraversalCallback callback) {

    AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        container.removeView(from);
        callback.onTraversalCompleted();
        if (to instanceof TraversalAware){
          ((TraversalAware) to).onTraversalCompleted();
        }
      }
    };

    ObjectGraph graph = ObjectGraphService.getObjectGraph(container.getContext());
    DpCalculator calc = graph.get(DpCalculator.class);
    int dp48 = calc.dp(48);
    AnimatorSet set = new AnimatorSet();
    if (Flow.Direction.BACKWARD == direction) {
      from.bringToFront();


      set.playTogether(
              ObjectAnimator.ofFloat(from, "alpha", 1.0f, 0.0f),
              ObjectAnimator.ofFloat(from, "translationX", 0, dp48));
    } else {
//      to.setTranslationX(dp48);
//      to.setAlpha(0f);
      set.playTogether(
              ObjectAnimator.ofFloat(to, "alpha", 0.0f, 1.0f),
              ObjectAnimator.ofFloat(to, "translationX",  dp48, 0));
    }

    set.setInterpolator(new DecelerateInterpolator(1.5f));
    set.setDuration(200);
    set.addListener(listener);
    set.start();


  }

}
