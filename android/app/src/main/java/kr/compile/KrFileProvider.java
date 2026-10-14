package kr.compile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class KrFileProvider extends ContentProvider {
    public static final String AUTHORITY = "kr.compile.fileprovider";

    public static Uri getUriForFile(Context context, File file) {
        String path = file.getAbsolutePath();
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .path(path)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File getFileForUri(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException("Invalid URI: " + uri);
        File file = new File(path);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + path);
        }
        return file;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = getFileForUri(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getPath();
        if (path == null) return "application/octet-stream";
        String lower = path.toLowerCase();
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".cpp") || lower.endsWith(".h")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File file = getFileForUri(uri);
            if (projection == null) {
                projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            }
            MatrixCursor cursor = new MatrixCursor(projection);
            MatrixCursor.RowBuilder row = cursor.newRow();
            for (String col : projection) {
                if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                    row.add(file.getName());
                } else if (OpenableColumns.SIZE.equals(col)) {
                    row.add(file.length());
                } else {
                    row.add(null);
                }
            }
            return cursor;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
