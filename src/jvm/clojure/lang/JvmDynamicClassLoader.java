package clojure.lang;

public class JvmDynamicClassLoader extends DynamicClassLoader {
    public JvmDynamicClassLoader() {
        //pseudo test in lieu of hasContextClassLoader()
        super();
    }

    public JvmDynamicClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> defineMissingClass(final String name, final byte[] bytes,
            final Object srcForm) {
        return defineClass(name,bytes,0,bytes.length);
    }

    @Override
    protected void classRemoved(final String className) {
        // do nothing
    }
}
