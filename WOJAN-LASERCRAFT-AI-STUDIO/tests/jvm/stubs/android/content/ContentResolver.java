package android.content;

import android.net.Uri;

/** Stub testowy. */
public class ContentResolver {
    public java.io.InputStream openInputStream(Uri uri) throws java.io.FileNotFoundException {
        throw new java.io.FileNotFoundException("stub");
    }

    public java.io.OutputStream openOutputStream(Uri uri, String mode) throws java.io.FileNotFoundException {
        throw new java.io.FileNotFoundException("stub");
    }

    public Uri insert(Uri uri, android.content.ContentValues values) {
        return null;
    }

    public android.database.Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }
}
