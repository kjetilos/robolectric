package com.xtremelabs.robolectric.shadows;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.xtremelabs.robolectric.internal.Implements;

@Implements(FrameLayout.class)
public class ShadowFrameLayout extends ShadowViewGroup {

    {
        setLayoutParams(new ViewGroup.MarginLayoutParams(0, 0));
    }

}
