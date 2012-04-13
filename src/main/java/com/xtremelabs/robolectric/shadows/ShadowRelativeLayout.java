package com.xtremelabs.robolectric.shadows;

import com.xtremelabs.robolectric.internal.Implements;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

@Implements(RelativeLayout.class)
public class ShadowRelativeLayout extends ShadowViewGroup {

    {
        setLayoutParams(new ViewGroup.MarginLayoutParams(0, 0));
    }

}
