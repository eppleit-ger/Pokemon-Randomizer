package com.dabomstew.pkrandom;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;
import java.util.ResourceBundle;

// [web] Added file. TeaVM cannot resolve ResourceBundle.getBundle for
// .properties files, so the GUI message bundle is loaded by hand.
public final class WebBundle {

    private static ResourceBundle instance;

    private WebBundle() {
    }

    public static synchronized ResourceBundle get() {
        if (instance == null) {
            Properties props = new Properties();
            try (InputStream in = WebBundle.class.getResourceAsStream("/com/dabomstew/pkrandom/newgui/Bundle.properties")) {
                if (in != null) {
                    props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                // fall through with whatever was loaded
            }
            instance = new ResourceBundle() {
                @Override
                protected Object handleGetObject(String key) {
                    return props.getProperty(key);
                }

                @Override
                @SuppressWarnings("unchecked")
                public Enumeration<String> getKeys() {
                    return (Enumeration<String>) (Enumeration<?>) Collections.enumeration(props.keySet());
                }
            };
        }
        return instance;
    }
}
