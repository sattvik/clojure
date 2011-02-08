/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* rich Aug 21, 2007 */
package clojure.lang;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class DynamicClassLoader extends URLClassLoader {
    private static ConcurrentHashMap<String, SoftReference<Class>> classCache =
            new ConcurrentHashMap<String, SoftReference<Class>>();
    private static final ReferenceQueue<Class> rq = new ReferenceQueue<Class>();
    private static final URL[] EMPTY_URLS = new URL[]{};
    HashMap<Integer, Object[]> constantVals = new HashMap<Integer, Object[]>();

    public DynamicClassLoader() {
        //pseudo test in lieu of hasContextClassLoader()
        super(EMPTY_URLS,
              (Thread.currentThread().getContextClassLoader() == null
               || Thread.currentThread().getContextClassLoader() == ClassLoader
                      .getSystemClassLoader()) ? Compiler.class.getClassLoader()
                                               : Thread.currentThread()
                                                       .getContextClassLoader());
    }

    public DynamicClassLoader(final ClassLoader parent) {
        super(EMPTY_URLS, parent);
    }

    public final Class defineClass(String name, byte[] bytes, Object srcForm) {
        //cleanup any dead entries
        if (rq.poll() != null) {
            while (rq.poll() != null) {
                System.out.println("Spinning like crazy!");
            }
            for (Map.Entry<String, SoftReference<Class>> e : classCache
                    .entrySet()) {
                if (e.getValue().get() == null) {
                    final String className=e.getKey();
                    classCache.remove(className, e.getValue());
                    classRemoved(className);
                }
            }
        }
        Class c = defineMissingClass(name, bytes, srcForm);
        classCache.put(name, new SoftReference<Class>(c, rq));
        return c;
    }

    protected abstract Class<?> defineMissingClass(final String name,
            final byte[] bytes, final Object srcForm);

    public final void registerConstants(int id, Object[] val) {
        constantVals.put(id, val);
    }

    public final Object[] getConstants(int id) {
        return constantVals.get(id);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        SoftReference<Class> cr = classCache.get(name);
        if (cr != null) {
            Class c = cr.get();
            if (c != null) {
                return c;
            } else {
                classCache.remove(name, cr);
                classRemoved(name);
            }
        }
        return super.findClass(name);
    }

    /**
     * Notifies a child class loader that a given class name is no longer used.
     *
     * @param className the name of the class that has been removed
     */
    protected abstract void classRemoved(final String className);

    @Override
    public final void addURL(URL url) {
        super.addURL(url);
    }
}
