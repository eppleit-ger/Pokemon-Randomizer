package de.nuzlocke.web.build;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

/**
 * Runs inside the TeaVM compiler (not in the browser): tells it which
 * class path resources to embed so Class.getResourceAsStream works for the
 * randomizer's config tables, IPS patches and message bundle.
 */
public class UprResourceSupplier implements ResourceSupplier {
    private static final String[] DIRECTORIES = {
            "com/dabomstew/pkrandom/config/",
            "com/dabomstew/pkrandom/patches/",
    };

    private static final String[] FILES = {
            "com/dabomstew/pkrandom/newgui/Bundle.properties",
    };

    @Override
    public String[] supplyResources(ResourceSupplierContext context) {
        List<String> result = new ArrayList<>();
        for (String dir : DIRECTORIES) {
            URL url = context.getClassLoader().getResource(dir);
            if (url == null || !"file".equals(url.getProtocol())) {
                throw new IllegalStateException("Resource directory not found on class path: " + dir);
            }
            collect(new File(url.getPath()), dir, result);
        }
        for (String file : FILES) {
            result.add(file);
        }
        return result.toArray(new String[0]);
    }

    private static void collect(File dir, String prefix, List<String> result) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, prefix + child.getName() + "/", result);
            } else {
                result.add(prefix + child.getName());
            }
        }
    }
}
