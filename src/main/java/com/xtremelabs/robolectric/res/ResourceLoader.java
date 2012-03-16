package com.xtremelabs.robolectric.res;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.xtremelabs.robolectric.Robolectric;
import com.xtremelabs.robolectric.RobolectricConfig;
import com.xtremelabs.robolectric.shadows.ShadowContextWrapper;
import com.xtremelabs.robolectric.util.I18nException;
import com.xtremelabs.robolectric.util.PropertiesHelper;

import android.R;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

public class ResourceLoader {
    private static final FileFilter MENU_DIR_FILE_FILTER = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return isMenuDirectory(file.getPath());
        }
    };
    private static final FileFilter LAYOUT_DIR_FILE_FILTER = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return isLayoutDirectory(file.getPath());
        }
    };
    private static final FileFilter DRAWABLE_DIR_FILE_FILTER = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return isDrawableDirectory(file.getPath());
        }
    };

    private static class Resources {

        private final Object lock = new Object();
        private boolean isInitialized = false;

        private int sdkVersion;

        private boolean strictI18n = false;

        private final List<File> resourceDirs = new ArrayList<File>();
        private final List<File> assetsDirs = new ArrayList<File>();

        private final ResourceExtractor resourceExtractor;
        private ViewLoader viewLoader;
        private MenuLoader menuLoader;
        private PreferenceLoader preferenceLoader;
        private final StringResourceLoader stringResourceLoader;
        private final PluralResourceLoader pluralResourceLoader;
        private final StringArrayResourceLoader stringArrayResourceLoader;
        private final AttrResourceLoader attrResourceLoader;
        private final ColorResourceLoader colorResourceLoader;
        private final DrawableResourceLoader drawableResourceLoader;
        private final List<RawResourceLoader> rawResourceLoaders = new ArrayList<RawResourceLoader>();

        public Resources(Class rClass, int sdkVersion, File resourceDir, File assetsDir,
                         List<File> libResourceDirs, List<File> libAssetDirs) throws Exception {
            this.sdkVersion = sdkVersion;
            resourceExtractor = new ResourceExtractor();
            resourceExtractor.addLocalRClass(rClass);
            resourceExtractor.addSystemRClass(R.class);
            assetsDirs.add(assetsDir);
            for (File libAssetDir : libAssetDirs) {
                assetsDirs.add(libAssetDir);
            }
            resourceDirs.add(resourceDir);
            for (File libResourceDir : libResourceDirs) {
                resourceDirs.add(libResourceDir);
            }
            for (File rDir : this.resourceDirs) {
                rawResourceLoaders.add(new RawResourceLoader(resourceExtractor, rDir));
            }

            stringResourceLoader = new StringResourceLoader(resourceExtractor);
            pluralResourceLoader = new PluralResourceLoader(resourceExtractor, stringResourceLoader);
            stringArrayResourceLoader = new StringArrayResourceLoader(resourceExtractor, stringResourceLoader);
            colorResourceLoader = new ColorResourceLoader(resourceExtractor);
            attrResourceLoader = new AttrResourceLoader(resourceExtractor);
            drawableResourceLoader = new DrawableResourceLoader(resourceExtractor);
        }

        public Resources(StringResourceLoader stringResourceLoader) {
            resourceExtractor = new ResourceExtractor();
            this.stringResourceLoader = stringResourceLoader;
            pluralResourceLoader = null;
            viewLoader = null;
            stringArrayResourceLoader = null;
            attrResourceLoader = null;
            colorResourceLoader = null;
            drawableResourceLoader = null;
        }

        public void init() {
            synchronized (lock) {
                if (!isInitialized) {
                    doInitialize();
                    isInitialized = true;
                }
            }
        }

        private void doInitialize() {
            try {
                if (getResourceDir() != null) {
                    viewLoader = new ViewLoader(resourceExtractor, attrResourceLoader);
                    menuLoader = new MenuLoader(resourceExtractor, attrResourceLoader);
                    preferenceLoader = new PreferenceLoader(resourceExtractor);

                    viewLoader.setStrictI18n(strictI18n);
                    menuLoader.setStrictI18n(strictI18n);
                    preferenceLoader.setStrictI18n(strictI18n);

                    File systemResourceDir = getSystemResourceDir(getPathToAndroidResources());
                    File systemValueResourceDir = getValueResourceDir(systemResourceDir);
                    loadSystemStringResources(systemValueResourceDir);
                    loadSystemPluralsResources(systemValueResourceDir);
                    loadSystemValueResources(systemValueResourceDir);
                    loadSystemViewResources(systemResourceDir);

                    for (File resourceDir : resourceDirs) {
                        File localValueResourceDir = getValueResourceDir(resourceDir);
                        File preferenceDir = getPreferenceResourceDir(resourceDir);

                        loadStringResources(localValueResourceDir);
                        loadPluralsResources(localValueResourceDir);
                        loadValueResources(localValueResourceDir);
                        loadViewResources(resourceDir);
                        loadMenuResources(resourceDir);
                        loadDrawableResources(resourceDir);
                        loadPreferenceResources(preferenceDir);
                    }
                } else {
                    viewLoader = null;
                    menuLoader = null;
                    preferenceLoader = null;
                }
            } catch (I18nException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void loadStringResources(File localResourceDir) throws Exception {
            DocumentLoader stringResourceDocumentLoader = new DocumentLoader(this.stringResourceLoader);
            loadLocalValueResourcesFromDirs(stringResourceDocumentLoader, localResourceDir);
        }

        private void loadSystemStringResources(File systemValueResourceDir) throws Exception {
            DocumentLoader stringResourceDocumentLoader = new DocumentLoader(this.stringResourceLoader);
            loadSystemValueResourcesFromDirs(stringResourceDocumentLoader, systemValueResourceDir);
        }

        private void loadPluralsResources(File localResourceDir) throws Exception {
            DocumentLoader stringResourceDocumentLoader = new DocumentLoader(this.pluralResourceLoader);
            loadLocalValueResourcesFromDirs(stringResourceDocumentLoader, localResourceDir);
        }

        private void loadSystemPluralsResources(File systemValueResourceDir) throws Exception {
            DocumentLoader stringResourceDocumentLoader = new DocumentLoader(this.pluralResourceLoader);
            loadSystemValueResourcesFromDirs(stringResourceDocumentLoader, systemValueResourceDir);
        }

        private void loadValueResources(File localResourceDir) throws Exception {
            DocumentLoader valueResourceLoader =
                new DocumentLoader(stringArrayResourceLoader, colorResourceLoader, attrResourceLoader);
            loadLocalValueResourcesFromDirs(valueResourceLoader, localResourceDir);
        }

        private void loadSystemValueResources(File systemValueResourceDir) throws Exception {
            DocumentLoader valueResourceLoader =
                new DocumentLoader(stringArrayResourceLoader, colorResourceLoader, attrResourceLoader);
            loadSystemValueResourcesFromDirs(valueResourceLoader, systemValueResourceDir);
        }

        private void loadViewResources(File localResourceDir) throws Exception {
            DocumentLoader viewDocumentLoader = new DocumentLoader(viewLoader);
            loadLayoutResourceXmlSubDirs(viewDocumentLoader, localResourceDir, false);
        }

        private void loadSystemViewResources(File systemResourceDir) throws Exception {
            DocumentLoader viewDocumentLoader = new DocumentLoader(viewLoader);
            loadLayoutResourceXmlSubDirs(viewDocumentLoader, systemResourceDir, true);
        }

        private void loadMenuResources(File xmlResourceDir) throws Exception {
            DocumentLoader menuDocumentLoader = new DocumentLoader(menuLoader);
            loadMenuResourceXmlDirs(menuDocumentLoader, xmlResourceDir);
        }

        private void loadDrawableResources(File xmlResourceDir) throws Exception {
            DocumentLoader drawableDocumentLoader = new DocumentLoader(drawableResourceLoader);
            loadDrawableResourceXmlDirs(drawableDocumentLoader, xmlResourceDir);
        }

        private void loadPreferenceResources(File xmlResourceDir) throws Exception {
            if (xmlResourceDir.exists()) {
                DocumentLoader preferenceDocumentLoader = new DocumentLoader(preferenceLoader);
                preferenceDocumentLoader.loadResourceXmlDir(xmlResourceDir);
            }
        }

        private void loadLayoutResourceXmlSubDirs(DocumentLoader layoutDocumentLoader, File xmlResourceDir, boolean isSystem)
            throws Exception {
            if (xmlResourceDir != null) {
                layoutDocumentLoader.loadResourceXmlDirs(isSystem, xmlResourceDir.listFiles(LAYOUT_DIR_FILE_FILTER));
            }
        }

        private void loadMenuResourceXmlDirs(DocumentLoader menuDocumentLoader, File xmlResourceDir) throws Exception {
            if (xmlResourceDir != null) {
                menuDocumentLoader.loadResourceXmlDirs(xmlResourceDir.listFiles(MENU_DIR_FILE_FILTER));
            }
        }

        private void loadDrawableResourceXmlDirs(DocumentLoader drawableResourceLoader, File xmlResourceDir) throws Exception {
            if (xmlResourceDir != null) {
                drawableResourceLoader.loadResourceXmlDirs(xmlResourceDir.listFiles(DRAWABLE_DIR_FILE_FILTER));
            }
        }

        private void loadSystemValueResourcesFromDirs(DocumentLoader documentLoader, File systemValueResourceDir)
            throws Exception {
            loadSystemResourceXmlDir(documentLoader, systemValueResourceDir);
        }

        private void loadLocalValueResourcesFromDirs(DocumentLoader documentLoader, File localValueResourceDir) throws Exception {
            if (localValueResourceDir.exists()) {
                loadValueResourcesFromDir(documentLoader, localValueResourceDir);
            }
        }

        private void loadValueResourcesFromDir(DocumentLoader documentloader, File xmlResourceDir) throws Exception {
            if (xmlResourceDir != null) {
                documentloader.loadResourceXmlDir(xmlResourceDir);
            }
        }

        private void loadSystemResourceXmlDir(DocumentLoader documentLoader, File stringResourceDir) throws Exception {
            if (stringResourceDir != null) {
                documentLoader.loadSystemResourceXmlDir(stringResourceDir);
            }
        }

        private File getValueResourceDir(File xmlResourceDir) {
            return xmlResourceDir != null ? new File(xmlResourceDir, "values") : null;
        }

        private File getPreferenceResourceDir(File xmlResourceDir) {
            return xmlResourceDir != null ? new File(xmlResourceDir, "xml") : null;
        }

        private String getPathToAndroidResources() {
            String resourcePath;
            if ((resourcePath = getAndroidResourcePathFromLocalProperties()) != null) {
                return resourcePath;
            } else if ((resourcePath = getAndroidResourcePathFromSystemEnvironment()) != null) {
                return resourcePath;
            } else if ((resourcePath = getAndroidResourcePathFromSystemProperty()) != null) {
                return resourcePath;
            } else if ((resourcePath = getAndroidResourcePathByExecingWhichAndroid()) != null) {
                return resourcePath;
            }

            System.out.println("WARNING: Unable to find path to Android SDK");
            return null;
        }

        private String getAndroidResourcePathFromLocalProperties() {
            // Hand tested
            // This is the path most often taken by IntelliJ
            File rootDir = getResourceDir().getParentFile();
            String localPropertiesFileName = "local.properties";
            File localPropertiesFile = new File(rootDir, localPropertiesFileName);
            if (!localPropertiesFile.exists()) {
                localPropertiesFile = new File(localPropertiesFileName);
            }
            if (localPropertiesFile.exists()) {
                Properties localProperties = new Properties();
                try {
                    localProperties.load(new FileInputStream(localPropertiesFile));
                    PropertiesHelper.doSubstitutions(localProperties);
                    String sdkPath = localProperties.getProperty("sdk.dir");
                    if (sdkPath != null) {
                        return getResourcePathFromSdkPath(sdkPath);
                    }
                } catch (IOException e) {
                    // fine, we'll try something else
                }
            }
            return null;
        }

        private File getResourceDir() {
            return resourceDirs.isEmpty() ? null : resourceDirs.iterator().next();
        }

        private String getAndroidResourcePathFromSystemEnvironment() {
            // Hand tested
            String resourcePath = System.getenv().get("ANDROID_HOME");
            if (resourcePath != null) {
                return new File(resourcePath, getAndroidResourceSubPath()).toString();
            }
            return null;
        }

        private String getAndroidResourcePathFromSystemProperty() {
            // this is used by the android-maven-plugin
            String resourcePath = System.getProperty("android.sdk.path");
            if (resourcePath != null) {
                return new File(resourcePath, getAndroidResourceSubPath()).toString();
            }
            return null;
        }

        private String getAndroidResourcePathByExecingWhichAndroid() {
            // Hand tested
            // Should always work from the command line. Often fails in IDEs because
            // they don't pass the full PATH in the environment
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"which", "android"});
                String sdkPath = new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
                if (sdkPath != null && sdkPath.endsWith("tools/android")) {
                    return getResourcePathFromSdkPath(sdkPath.substring(0, sdkPath.indexOf("tools/android")));
                }
            } catch (IOException e) {
                // fine we'll try something else
            }
            return null;
        }

        private String getResourcePathFromSdkPath(String sdkPath) {
            File androidResourcePath = new File(sdkPath, getAndroidResourceSubPath());
            return androidResourcePath.exists() ? androidResourcePath.toString() : null;
        }

        private String getAndroidResourceSubPath() {
            return "platforms/android-" + sdkVersion + "/data/res";
        }

        public void setStrictIl8n(boolean strict) {
            this.strictI18n = strict;
            if (viewLoader != null) {
                viewLoader.setStrictI18n(strict);
            }
            if (menuLoader != null) {
                menuLoader.setStrictI18n(strict);
            }
            if (preferenceLoader != null) {
                preferenceLoader.setStrictI18n(strict);
            }
        }

        public boolean getStrictI18n() {
            return strictI18n;
        }

        public ResourceExtractor getResourceExtractor() {
            return resourceExtractor;
        }

        private File getSystemResourceDir(String pathToAndroidResources) {
            return pathToAndroidResources != null ? new File(pathToAndroidResources) : null;
        }

        public String getNameForId(int viewId) {
            return resourceExtractor.getResourceName(viewId);
        }

        public View inflateView(Context context, int resource, ViewGroup viewGroup) {
            return viewLoader.inflateView(context, resource, viewGroup);
        }

        public int getColorValue(int id) {
            return colorResourceLoader.getValue(id);
        }

        public String getStringValue(int id) {
            return stringResourceLoader.getValue(id);
        }

        public String getPluralStringValue(int id, int quantity) {
            return pluralResourceLoader.getValue(id, quantity);
        }

        public boolean isDrawableXml(int resourceId) {
            return drawableResourceLoader.isXml(resourceId);
        }

        public int[] getDrawableIds(int resourceId) {
            return drawableResourceLoader.getDrawableIds(resourceId);
        }

        public Drawable getXmlDrawable(int resourceId) {
            return drawableResourceLoader.getXmlDrawable(resourceId);
        }

        public InputStream getRawValue(int id) {
            for (RawResourceLoader rawResourceLoader : rawResourceLoaders) {
                InputStream value = rawResourceLoader.getValue(id);
                if (value != null) {
                    return value;
                }
            }
            //throw new IllegalArgumentException("No such value: " + id + " in " + rawResourceLoaders);
            return null;
        }

        public String[] getStringArrayValue(int id) {
            return stringArrayResourceLoader.getArrayValue(id);
        }

        public void inflateMenu(Context context, int resource, Menu root) {
            menuLoader.inflateMenu(context, resource, root);
        }

        public PreferenceScreen inflatePreferences(Context context, int resourceId) {
            return preferenceLoader.inflatePreferences(context, resourceId);
        }

        public File getAssetsBase() {
            return assetsDirs.isEmpty() ? null : assetsDirs.iterator().next();
        }

        public ViewLoader.ViewNode getLayoutViewNode(String layoutName) {
            return viewLoader.viewNodesByLayoutName.get(layoutName);
        }

        public void setLayoutQualifierSearchPath(String... locations) {
            viewLoader.setLayoutQualifierSearchPath(locations);
        }

    }

    private Class rClass;

    private static final Map<Integer, Resources> systemResourcesBySdkVersion = new HashMap<Integer, Resources>();
    private static final Map<Class, Resources> resourcesByRClass = new HashMap<Class, Resources>();

    private final Resources resources;

    // TODO: get these value from the xml resources instead [xw 20101011]
    public final Map<Integer, Integer> dimensions = new HashMap<Integer, Integer>();

    public ResourceLoader(int sdkVersion, Class rClass, File resourceDir, File assetsDir,
                          List<File> libResourceDirs, List<File> libAssetDirs) throws Exception {
        this.rClass = rClass;

        synchronized (resourcesByRClass) {
            Resources resources = resourcesByRClass.get(rClass);
            if (resources == null) {
                resources = new Resources(rClass, sdkVersion, resourceDir, assetsDir, libResourceDirs, libAssetDirs);
                resourcesByRClass.put(rClass, resources);
            }
            this.resources = resources;
        }
    }

    public ResourceLoader(int sdkVersion, Class rClass, File resourceDir, File assetsDir) throws Exception {
        this(sdkVersion, rClass, resourceDir, assetsDir, Collections.<File>emptyList(), Collections.<File>emptyList());
    }

    public ResourceLoader(RobolectricConfig config, List<RobolectricConfig> libConfigs) throws Exception {
        this(getSdkVersion(config), getRClass(config), getResourceDir(config), getAssetsDir(config),
             getResourceDirs(libConfigs), getAssetsDirs(libConfigs));
    }

    private static List<File> getAssetsDirs(List<RobolectricConfig> configs) {
        List<File> assetsDirs = new ArrayList<File>();
        for (RobolectricConfig config : configs) {
            assetsDirs.add(getAssetsDir(config));
        }
        return assetsDirs;
    }

    private static List<File> getResourceDirs(List<RobolectricConfig> configs) {
        List<File> resourceDirs = new ArrayList<File>();
        for (RobolectricConfig config : configs) {
            resourceDirs.add(getResourceDir(config));
        }
        return resourceDirs;
    }

    private static int getSdkVersion(RobolectricConfig config) {
        return config.getRealSdkVersion();
    }

    private static Class getRClass(RobolectricConfig config) throws Exception {
        String rClassName = config.getRClassName();
        return Class.forName(rClassName);
    }

    private static File getResourceDir(RobolectricConfig config) {
        return config.getResourceDirectory();
    }

    private static File getAssetsDir(RobolectricConfig config) {
        return config.getAssetsDirectory();
    }

    private Resources getInitializedResources() {
        resources.init();
        return resources;
    }

    public void setStrictI18n(boolean strict) {
        getInitializedResources().setStrictIl8n(strict);
    }

    public boolean getStrictI18n() {
        return getInitializedResources().getStrictI18n();
    }

    static boolean isLayoutDirectory(String path) {
        return path.contains(File.separator + "layout");
    }

    static boolean isDrawableDirectory(String path) {
        return path.contains(File.separator + "drawable");
    }

    static boolean isMenuDirectory(String path) {
        return path.contains(File.separator + "menu");
    }

    /*
     * For tests only...
     */
    protected ResourceLoader(StringResourceLoader stringResourceLoader) {
        try {
            this.resources = new Resources(stringResourceLoader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ResourceLoader getFrom(Context context) {
        ResourceLoader resourceLoader = Robolectric.shadowOf(context.getApplicationContext()).getResourceLoader();
        resourceLoader.init();
        return resourceLoader;
    }

    private void init() {
        resources.init();
    }

    public String getNameForId(int viewId) {
        return getInitializedResources().getNameForId(viewId);
    }

    public View inflateView(Context context, int resource, ViewGroup viewGroup) {
        return getInitializedResources().inflateView(context, resource, viewGroup);
    }

    public int getColorValue(int id) {
        return getInitializedResources().getColorValue(id);
    }

    public String getStringValue(int id) {
        return getInitializedResources().getStringValue(id);
    }

    public String getPluralStringValue(int id, int quantity) {
        return getInitializedResources().getPluralStringValue(id, quantity);
    }

    public boolean isDrawableXml(int resourceId) {
        return getInitializedResources().isDrawableXml(resourceId);
    }

    public int[] getDrawableIds(int resourceId) {
        return getInitializedResources().getDrawableIds(resourceId);
    }

    public Drawable getXmlDrawable(int resourceId) {
        return getInitializedResources().getXmlDrawable(resourceId);
    }

    public InputStream getRawValue(int id) {
        return getInitializedResources().getRawValue(id);
    }

    public String[] getStringArrayValue(int id) {
        return getInitializedResources().getStringArrayValue(id);
    }

    public void inflateMenu(Context context, int resource, Menu root) {
        getInitializedResources().inflateMenu(context, resource, root);
    }

    public PreferenceScreen inflatePreferences(Context context, int resourceId) {
        return getInitializedResources().inflatePreferences(context, resourceId);
    }

    public File getAssetsBase() {
        return getInitializedResources().getAssetsBase();
    }

    public ViewLoader.ViewNode getLayoutViewNode(String layoutName) {
        return getInitializedResources().getLayoutViewNode(layoutName);
    }

    public void setLayoutQualifierSearchPath(String... locations) {
        getInitializedResources().setLayoutQualifierSearchPath(locations);
    }

    @SuppressWarnings("rawtypes")
    public Class getLocalRClass() {
        return rClass;
    }

    public void setLocalRClass(Class clazz) {
        rClass = clazz;
    }

    public ResourceExtractor getResourceExtractor() {
        return resources.getResourceExtractor();
    }

    public Drawable getAnimDrawable(int resourceId) {
        return getInnerRClassDrawable(resourceId, "$anim", AnimationDrawable.class);
    }

    public Drawable getColorDrawable(int resourceId) {
        return getInnerRClassDrawable(resourceId, "$color", ColorDrawable.class);
    }

    @SuppressWarnings("rawtypes")
    private Drawable getInnerRClassDrawable(int drawableResourceId, String suffix, Class returnClass) {
        ShadowContextWrapper shadowApp = Robolectric.shadowOf(Robolectric.application);
        Class rClass = shadowApp.getResourceLoader().getLocalRClass();

        // Check to make sure there is actually an R Class, if not
        // return just a BitmapDrawable
        if (rClass == null) {
            return null;
        }

        // Load the Inner Class for interrogation
        Class animClass = null;
        try {
            animClass = Class.forName(rClass.getCanonicalName() + suffix);
        } catch (ClassNotFoundException e) {
            return null;
        }

        // Try to find the passed in resource ID
        try {
            for (Field field : animClass.getDeclaredFields()) {
                if (field.getInt(animClass) == drawableResourceId) {
                    return (Drawable) returnClass.newInstance();
                }
            }
        } catch (Exception e) {
        }

        return null;
    }

}
