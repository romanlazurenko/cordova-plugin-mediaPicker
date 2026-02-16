package com.dmc.mediaPickerPlugin;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * MediaPicker plugin using Android Photo Picker API
 * No READ_MEDIA_IMAGES/VIDEO permissions required
 */
public class MediaPicker extends CordovaPlugin {
    private static final String TAG = "MediaPicker";
    private static final int REQUEST_PICK_MEDIA = 200;
    private static final int REQUEST_TAKE_PHOTO = 201;

    private CallbackContext callback;
    private int thumbnailQuality = 50;
    private int quality = 100;
    private int thumbnailW = 200;
    private int thumbnailH = 200;
    private int maxSelectCount = 10;
    private int selectMode = 0; // 0 = image+video, 1 = image only, 2 = video only

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        getPublicArgs(args);

        if (action.equals("getMedias") || action.equals("photoLibrary")) {
            this.getMedias(args, callbackContext);
            return true;
        } else if (action.equals("takePhoto")) {
            this.takePhoto(args, callbackContext);
            return true;
        } else if (action.equals("extractThumbnail")) {
            this.extractThumbnail(args, callbackContext);
            return true;
        } else if (action.equals("compressImage")) {
            this.compressImage(args, callbackContext);
            return true;
        } else if (action.equals("fileToBlob")) {
            this.fileToBlob(args.getString(0), callbackContext);
            return true;
        } else if (action.equals("getExifForKey")) {
            this.getExifForKey(args.getString(0), args.getString(1), callbackContext);
            return true;
        } else if (action.equals("getFileInfo")) {
            this.getFileInfo(args, callbackContext);
            return true;
        }
        return false;
    }

    private void takePhoto(JSONArray args, CallbackContext callbackContext) {
        this.callback = callbackContext;
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(cordova.getActivity().getPackageManager()) != null) {
            cordova.startActivityForResult(this, intent, REQUEST_TAKE_PHOTO);
        } else {
            callbackContext.error("No camera app available");
        }
    }

    private void getMedias(JSONArray args, CallbackContext callbackContext) {
        this.callback = callbackContext;

        // Parse options
        if (args != null && args.length() > 0) {
            try {
                JSONObject jsonObject = args.getJSONObject(0);
                if (jsonObject.has("selectMode")) {
                    selectMode = jsonObject.getInt("selectMode");
                }
                if (jsonObject.has("maxSelectCount")) {
                    maxSelectCount = jsonObject.getInt("maxSelectCount");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing options", e);
            }
        }

        Intent intent;

        // Use Photo Picker on Android 11+ (API 30+), fall back to ACTION_OPEN_DOCUMENT on older versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - Use native Photo Picker
            if (maxSelectCount > 1) {
                intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
                intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, Math.min(maxSelectCount, MediaStore.getPickImagesMaxLimit()));
            } else {
                intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            }

            // Set media type filter
            if (selectMode == 1) {
                intent.setType("image/*");
            } else if (selectMode == 2) {
                intent.setType("video/*");
            } else {
                // Both image and video - use EXTRA_PICK_IMAGES_IN_ORDER for mixed selection
                intent.setType("*/*");
                String[] mimeTypes = {"image/*", "video/*"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            }
        } else {
            // Android 12 and below - Use ACTION_OPEN_DOCUMENT (no permissions needed)
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            if (maxSelectCount > 1) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }

            // Set media type filter
            if (selectMode == 1) {
                intent.setType("image/*");
            } else if (selectMode == 2) {
                intent.setType("video/*");
            } else {
                intent.setType("*/*");
                String[] mimeTypes = {"image/*", "video/*"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            }
        }

        cordova.startActivityForResult(this, intent, REQUEST_PICK_MEDIA);
    }

    public void getPublicArgs(JSONArray args) {
        JSONObject jsonObject = new JSONObject();
        if (args != null && args.length() > 0) {
            try {
                jsonObject = args.getJSONObject(0);
            } catch (Exception e) {
                // ignore
            }
            try {
                thumbnailQuality = jsonObject.getInt("thumbnailQuality");
            } catch (Exception e) {
                // use default
            }
            try {
                thumbnailW = jsonObject.getInt("thumbnailW");
            } catch (Exception e) {
                // use default
            }
            try {
                thumbnailH = jsonObject.getInt("thumbnailH");
            } catch (Exception e) {
                // use default
            }
            try {
                quality = jsonObject.getInt("quality");
            } catch (Exception e) {
                // use default
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode != Activity.RESULT_OK) {
            if (callback != null) {
                callback.success(new JSONArray()); // Return empty array on cancel
            }
            return;
        }

        if (requestCode == REQUEST_PICK_MEDIA) {
            handleMediaPickerResult(intent);
        } else if (requestCode == REQUEST_TAKE_PHOTO) {
            handleTakePhotoResult(intent);
        }
    }

    private void handleMediaPickerResult(Intent intent) {
        final JSONArray jsonArray = new JSONArray();
        final ArrayList<Uri> selectedUris = new ArrayList<>();

        // Handle multiple selection
        if (intent.getClipData() != null) {
            int count = intent.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri uri = intent.getClipData().getItemAt(i).getUri();
                selectedUris.add(uri);
            }
        } else if (intent.getData() != null) {
            // Single selection
            selectedUris.add(intent.getData());
        }

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    int index = 0;
                    for (Uri uri : selectedUris) {
                        // Take persistable permission for the URI
                        try {
                            cordova.getActivity().getContentResolver().takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            // Permission might not be persistable, that's OK
                            Log.w(TAG, "Could not take persistable permission", e);
                        }

                        JSONObject object = new JSONObject();

                        // Copy file to app's cache directory to get a file path
                        File cachedFile = copyUriToCache(uri);
                        if (cachedFile != null) {
                            object.put("path", cachedFile.getAbsolutePath());
                            object.put("uri", Uri.fromFile(cachedFile).toString());
                            object.put("size", cachedFile.length());
                            object.put("name", cachedFile.getName());
                        } else {
                            object.put("path", "");
                            object.put("uri", uri.toString());
                            object.put("size", getFileSize(uri));
                            object.put("name", getFileName(uri));
                        }

                        object.put("index", index);

                        String mimeType = getMimeType(uri);
                        if (mimeType != null && mimeType.startsWith("video")) {
                            object.put("mediaType", "video");
                        } else {
                            object.put("mediaType", "image");
                        }

                        jsonArray.put(object);
                        index++;
                    }
                    MediaPicker.this.callback.success(jsonArray);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing selected media", e);
                    MediaPicker.this.callback.error("Error processing media: " + e.getMessage());
                }
            }
        });
    }

    private void handleTakePhotoResult(Intent intent) {
        final JSONArray jsonArray = new JSONArray();

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Uri photoUri = intent.getData();
                    if (photoUri != null) {
                        JSONObject object = new JSONObject();

                        File cachedFile = copyUriToCache(photoUri);
                        if (cachedFile != null) {
                            object.put("path", cachedFile.getAbsolutePath());
                            object.put("uri", Uri.fromFile(cachedFile).toString());
                            object.put("size", cachedFile.length());
                            object.put("name", cachedFile.getName());
                        } else {
                            object.put("path", "");
                            object.put("uri", photoUri.toString());
                            object.put("size", 0);
                            object.put("name", "photo.jpg");
                        }

                        object.put("index", 0);
                        object.put("mediaType", "image");
                        jsonArray.put(object);
                    }
                    MediaPicker.this.callback.success(jsonArray);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing photo", e);
                    MediaPicker.this.callback.error("Error processing photo: " + e.getMessage());
                }
            }
        });
    }

    private File copyUriToCache(Uri uri) {
        try {
            ContentResolver resolver = cordova.getActivity().getContentResolver();
            String fileName = getFileName(uri);
            if (fileName == null || fileName.isEmpty()) {
                String mimeType = getMimeType(uri);
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                fileName = "media_" + System.currentTimeMillis() + "." + (extension != null ? extension : "tmp");
            }

            File cacheDir = cordova.getActivity().getCacheDir();
            File outputFile = new File(cacheDir, fileName);

            InputStream inputStream = resolver.openInputStream(uri);
            if (inputStream == null) return null;

            OutputStream outputStream = new FileOutputStream(outputFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outputStream.close();

            return outputFile;
        } catch (Exception e) {
            Log.e(TAG, "Error copying URI to cache", e);
            return null;
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = cordova.getActivity().getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private long getFileSize(Uri uri) {
        long size = 0;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = cordova.getActivity().getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIndex >= 0) {
                        size = cursor.getLong(sizeIndex);
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        return size;
    }

    private String getMimeType(Uri uri) {
        String mimeType = null;
        if (uri.getScheme().equals("content")) {
            mimeType = cordova.getActivity().getContentResolver().getType(uri);
        } else {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }
        return mimeType;
    }

    public void extractThumbnail(JSONArray args, CallbackContext callbackContext) {
        JSONObject jsonObject = new JSONObject();
        if (args != null && args.length() > 0) {
            try {
                jsonObject = args.getJSONObject(0);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            try {
                thumbnailQuality = jsonObject.getInt("thumbnailQuality");
            } catch (JSONException e) {
                // use default
            }
            try {
                String path = jsonObject.getString("path");
                jsonObject.put("exifRotate", getBitmapRotate(path));
                int mediatype = "video".equals(jsonObject.getString("mediaType")) ? 3 : 1;
                jsonObject.put("thumbnailBase64", extractThumbnail(path, mediatype, thumbnailQuality));
            } catch (Exception e) {
                e.printStackTrace();
            }
            callbackContext.success(jsonObject);
        }
    }

    public String extractThumbnail(String path, int mediaType, int quality) {
        String encodedImage = null;
        try {
            Bitmap thumbImage;
            if (mediaType == 3) {
                thumbImage = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND);
            } else {
                thumbImage = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(path), thumbnailW, thumbnailH);
            }
            if (thumbImage != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                thumbImage.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                byte[] imageBytes = baos.toByteArray();
                encodedImage = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
                baos.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encodedImage;
    }

    public void compressImage(JSONArray args, CallbackContext callbackContext) {
        this.callback = callbackContext;
        try {
            JSONObject jsonObject = args.getJSONObject(0);
            String path = jsonObject.getString("path");
            int quality = jsonObject.getInt("quality");
            if (quality < 100) {
                File file = compressImageFile(path, quality);
                jsonObject.put("path", file.getPath());
                jsonObject.put("uri", Uri.fromFile(new File(file.getPath())));
                jsonObject.put("size", file.length());
                jsonObject.put("name", file.getName());
                callbackContext.success(jsonObject);
            } else {
                callbackContext.success(jsonObject);
            }
        } catch (Exception e) {
            callbackContext.error("compressImage error: " + e);
            e.printStackTrace();
        }
    }

    public void getFileInfo(JSONArray args, CallbackContext callbackContext) {
        this.callback = callbackContext;
        try {
            String pathOrUri = args.getString(0);
            String type = args.getString(1);

            File file;
            if ("uri".equals(type)) {
                Uri uri = Uri.parse(pathOrUri);
                if (uri.getScheme() != null && uri.getScheme().equals("content")) {
                    // Copy content URI to cache to get file info
                    file = copyUriToCache(uri);
                    if (file == null) {
                        callbackContext.error("Could not access file");
                        return;
                    }
                } else {
                    file = new File(FileHelper.getRealPath(pathOrUri, cordova));
                }
            } else {
                file = new File(pathOrUri);
            }

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("path", file.getPath());
            jsonObject.put("uri", Uri.fromFile(new File(file.getPath())));
            jsonObject.put("size", file.length());
            jsonObject.put("name", file.getName());
            String mimeType = FileHelper.getMimeType(jsonObject.getString("uri"), cordova);
            String mediaType = mimeType != null && mimeType.contains("video") ? "video" : "image";
            jsonObject.put("mediaType", mediaType);
            callbackContext.success(jsonObject);
        } catch (Exception e) {
            callbackContext.error("getFileInfo error: " + e);
            e.printStackTrace();
        }
    }

    public File compressImageFile(String path, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String compFileName = "dmcMediaPickerCompress" + System.currentTimeMillis() + ".jpg";
        File file = new File(cordova.getActivity().getCacheDir(), compFileName);
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap != null) {
            rotatingImage(getBitmapRotate(path), bitmap).compress(Bitmap.CompressFormat.JPEG, quality, baos);
            try {
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(baos.toByteArray());
                fos.flush();
                fos.close();
            } catch (Exception e) {
                MediaPicker.this.callback.error("compressImage error: " + e);
                e.printStackTrace();
            }
        }
        return file;
    }

    public int getBitmapRotate(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return degree;
    }

    private static Bitmap rotatingImage(int angle, Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public byte[] extractThumbnailByte(String path, int mediaType, int quality) {
        try {
            Bitmap thumbImage;
            if (mediaType == 3) {
                thumbImage = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND);
            } else {
                thumbImage = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(path), thumbnailW, thumbnailH);
            }
            if (thumbImage != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                thumbImage.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void getExifForKey(String path, String tag, CallbackContext callbackContext) {
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            String object = exifInterface.getAttribute(tag);
            callbackContext.success(object);
        } catch (Exception e) {
            callbackContext.error("getExifForKey error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String fileToBase64(String path) {
        byte[] data = null;
        try {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(path));
            data = new byte[in.available()];
            in.read(data);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public void fileToBlob(String path, CallbackContext callbackContext) {
        byte[] data = null;
        try {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(path));
            data = new byte[in.available()];
            in.read(data);
            in.close();
        } catch (IOException e) {
            callbackContext.error("fileToBlob " + e);
            e.printStackTrace();
        }
        callbackContext.success(data);
    }
}
