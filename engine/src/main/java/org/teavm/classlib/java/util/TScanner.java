package org.teavm.classlib.java.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.NoSuchElementException;

/**
 * Line-oriented stand-in for java.util.Scanner, which TeaVM does not ship.
 * The randomizer only reads config files line by line, so only that part
 * of the API is provided.
 */
public class TScanner implements AutoCloseable {
    private final BufferedReader reader;
    private String nextLine;
    private boolean lookedAhead;

    public TScanner(InputStream source, String charsetName) {
        try {
            reader = new BufferedReader(new InputStreamReader(source, charsetName));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public TScanner(InputStream source) {
        this(source, "UTF-8");
    }

    public TScanner(File source, String charsetName) throws FileNotFoundException {
        this(new FileInputStream(source), charsetName);
    }

    public TScanner(String source) {
        this(new java.io.ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "UTF-8");
    }

    private void lookAhead() {
        if (!lookedAhead) {
            try {
                nextLine = reader.readLine();
            } catch (IOException e) {
                nextLine = null;
            }
            lookedAhead = true;
        }
    }

    public boolean hasNextLine() {
        lookAhead();
        return nextLine != null;
    }

    public String nextLine() {
        lookAhead();
        if (nextLine == null) {
            throw new NoSuchElementException("No line found");
        }
        lookedAhead = false;
        return nextLine;
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException e) {
            // ignore, like java.util.Scanner
        }
    }
}
