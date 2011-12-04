/* Copyright © 2011 Sattvik Software & Technology Resources, Ltd. Co.
 * All rights reserved.
 *
 * The use and distribution terms for this software are covered by the Eclipse
 * Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
 * can be found in the file epl-v10.html at the root of this distribution.  By
 * using this software in any fashion, you are agreeing to be bound by the
 * terms of this license.  You must not remove this notice, or any other, from
 * this software.
 */
package clojure.lang;

import android.util.Log;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.DexOptions;
import dalvik.system.DexFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Dynamic class loader for the Dalvik VM.
 *
 * @since 1.2.0
 * @author Daniel Solano Gómez
 */
public class DalvikDynamicClassLoader extends DynamicClassLoader {
    /** Options for translation. */
    private static final CfOptions OPTIONS = new CfOptions();
    /** Reference to compile path var, used for generated jar files. */
    private static final Var COMPILE_PATH =
            RT.var("clojure.core", "*compile-path*");
    /** Configure whether or not to use extended op codes. */
    private static final DexOptions DEX_OPTIONS = new DexOptions();

    static {
        // disable name checks
        OPTIONS.strictNameCheck = false;
	// ensure generation of compatible DEX files
	DEX_OPTIONS.targetApiLevel = android.os.Build.VERSION.SDK_INT;
    }

    /** Tag used for logging. */
    private static final String TAG = "DalvikClojureCompiler";

    public DalvikDynamicClassLoader() {
        super();
    }

    public DalvikDynamicClassLoader(final ClassLoader parent) {
        super(parent);
    }

    /**
     * Dalvik-specific method for dynamically loading a class from JVM byte
     * codes.  As there is no easy way to translate a class from the JVM to
     * Dalvik in-memory, this method takes a slow route through disk.  This
     * involves using a DexFile from the dx tool to translate the JVM class
     * into a Dalvik executable.  The contents of the executable must be
     * written to disk as an entry in a zip file.  This zip file is then loaded
     * and the requested class is instantiated using the Android runtime's
     * DexFile.
     *
     * @param name the name of the class to define
     * @param bytes the JVM bytecodes for the class
     * @param srcForm the Clojure form for the class
     */
    @Override
    protected Class<?> defineMissingClass(final String name, final byte[] bytes,
            final Object srcForm) {
        // create dx DexFile and add translated class into it
        final com.android.dx.dex.file.DexFile outDexFile =
                new com.android.dx.dex.file.DexFile(DEX_OPTIONS);
        outDexFile.add(CfTranslator.translate("", bytes, OPTIONS, DEX_OPTIONS));

        // get compile directory
        final File compileDir = new File((String) COMPILE_PATH.deref());
        try {
            // write Dalvik executable into a temporary jar
            final File jarFile =
                    File.createTempFile("repl-", ".jar", compileDir);
            jarFile.deleteOnExit();
            final ZipOutputStream jarOut =
                    new ZipOutputStream(new FileOutputStream(jarFile));
            jarOut.putNextEntry(new ZipEntry("classes.dex"));
            outDexFile.writeTo(jarOut, null, false);
            jarOut.close();

            // open the jar and create an optimized dex file
            final String jarPath = jarFile.getAbsolutePath();
            final String dexPath =
                    jarPath.substring(0, jarPath.lastIndexOf('.'))
                            .concat(".dex");
            final DexFile inDexFile = DexFile.loadDex(jarPath, dexPath, 0);

            // load the class
            Class<?> clazz = inDexFile.loadClass(name.replace(".", "/"), this);
            if (clazz == null) {
                Log.e(TAG,"Failed to load generated class: "+name);
                throw new RuntimeException(
                        "Failed to load generated class " + name + ".");
            }
            return clazz;
        } catch (IOException e) {
            Log.e(TAG,"Failed to define class due to I/O exception.",e);
            throw new RuntimeException(e);
        }
    }
}
