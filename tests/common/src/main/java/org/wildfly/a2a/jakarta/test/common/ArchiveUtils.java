package org.wildfly.a2a.jakarta.test.common;

import java.io.File;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

public class ArchiveUtils {

    private ArchiveUtils() {
    }

    public static JavaArchive getJarForClass(Class<?> clazz) throws Exception {
        File f = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!f.exists()) {
            throw new IllegalArgumentException(
                    "Archive not found for class " + clazz.getName() + " at " + f.getAbsolutePath());
        }
        return ShrinkWrap.createFromZipFile(JavaArchive.class, f);
    }
}
