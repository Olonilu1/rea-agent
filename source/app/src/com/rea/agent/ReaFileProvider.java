package com.rea.agent;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** Serves files from the app's external files dir to the package installer. */
public class ReaFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        String n = uri == null ? "" : uri.getLastPathSegment();
        return n != null && n.toLowerCase().endsWith(".apk")
            ? "application/vnd.android.package-archive" : "application/octet-stream";
    }

    private File fileFor(Uri uri) {
        String name = uri == null ? null : uri.getLastPathSegment();
        if (name == null) return null;
        File dir = getContext().getExternalFilesDir(null);
        File f = new File(dir, name);
        try {
            if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath())) return null;
        } catch (Exception e) { return null; }
        return f;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        if (f == null || !f.exists()) throw new FileNotFoundException(String.valueOf(uri));
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        String[] cols = proj == null ? new String[]{"_display_name", "_size"} : proj;
        MatrixCursor c = new MatrixCursor(cols);
        File f = fileFor(uri);
        if (f != null && f.exists()) {
            MatrixCursor.RowBuilder b = c.newRow();
            for (String col : cols) {
                if ("_display_name".equals(col)) b.add(f.getName());
                else if ("_size".equals(col)) b.add(f.length());
                else b.add(null);
            }
        }
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String sel, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String sel, String[] args) { return 0; }
}
