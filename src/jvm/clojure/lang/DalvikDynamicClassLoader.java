package clojure.lang;

import android.util.Log;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import dalvik.system.DexFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DalvikDynamicClassLoader extends DynamicClassLoader {
    private static final CfOptions OPTIONS = new CfOptions();
    private static final Var COMPILE_PATH =
            RT.var("clojure.core", "*compile-path*");

    static {
        OPTIONS.strictNameCheck = false;
    }

    private static final String TAG = "DalvikClojureCompiler";

    public DalvikDynamicClassLoader() {
        super();
    }

    public DalvikDynamicClassLoader(final ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> defineMissingClass(final String name, final byte[] bytes,
            final Object srcForm) {
        Log.d(TAG,"defineMissingClass: "+name);
        final com.android.dx.dex.file.DexFile outDexFile =
                new com.android.dx.dex.file.DexFile();
        outDexFile.add(CfTranslator.translate("", bytes, OPTIONS));
        final File compileDir = new File((String) COMPILE_PATH.deref());
        Log.d(TAG,"compileDir= "+compileDir.getAbsolutePath());
        try {
            final File jarFile =
                    File.createTempFile("repl-", ".jar", compileDir);
            Log.d(TAG,"jarFile= "+jarFile.getAbsolutePath());
            jarFile.deleteOnExit();
            final ZipOutputStream jarOut =
                    new ZipOutputStream(new FileOutputStream(jarFile));
            jarOut.putNextEntry(new ZipEntry("classes.dex"));
            outDexFile.writeTo(jarOut, null, false);
            jarOut.close();
            final String jarPath = jarFile.getAbsolutePath();
            final String dexPath =
                    jarPath.substring(0, jarPath.lastIndexOf('.'))
                            .concat(".dex");
            Log.d(TAG,"dexPath= "+dexPath);
            final DexFile inDexFile = DexFile.loadDex(jarPath, dexPath, 0);
            Class<?> clazz = inDexFile.loadClass(name.replace(".", "/"), this);
            if (clazz == null) {
                Log.wtf(TAG,"Failed to load generated class: "+name);
                throw new RuntimeException(
                        "Failed to load generated class " + name + ".");
            }
            Log.d(TAG,"Successfully defined class "+name);
            return clazz;
        } catch (IOException e) {
            Log.e(TAG,"Failed to define class due to I/O exception.",e);
            throw new RuntimeException(e);
        }
    }
}
