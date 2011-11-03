package com.xtremelabs.robolectric.res;

import com.xtremelabs.robolectric.R;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ColorResourceLoaderErrorTest {
    private ColorResourceLoader colorResourceLoader;
    private ResourceExtractor resourceExtractor;

    @Before public void setUp() throws Exception {
        resourceExtractor = new ResourceExtractor();
        resourceExtractor.addSystemRClass(android.R.class);
        colorResourceLoader = new ColorResourceLoader(resourceExtractor);
    }

    @Test
    public void shouldReturnMinusOneWhenNotExistingResourceId() {
        assertThat(-1, equalTo(colorResourceLoader.getValue(R.color.grey42)));
    }

    @Test
    public void shouldThrowHelpfulExplanationWhenMissingResource() {
        final int id = android.R.color.black;
        final String name = resourceExtractor.getResourceName(id);
        try {
            colorResourceLoader.getValue(id);
            fail("Expected exception");
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString(name));
            assertThat(e.getMessage(), containsString("" + id));
        }
    }

}
