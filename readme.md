# MediaPicker v3.0.0

Android/iOS media picker plugin with **Photo Picker API** support - **no broad media permissions required**.

This fork removes `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_MEDIA_AUDIO` permissions by using Android's native Photo Picker API (Android 13+) with fallback to `ACTION_OPEN_DOCUMENT` for older versions.

## What's New in v3.0.0

- **No media permissions required** - Uses Android Photo Picker API (Android 13+) and `ACTION_OPEN_DOCUMENT` (Android 12 and below)
- **Google Play compliant** - Passes Google Play Store policy checks for media permissions
- **Removed external library dependency** - No longer depends on `com.github.DmcSDK:MediaPickerPoject`
- **Backward compatible** - Same JavaScript API as v2.x

## Installation

```bash
cordova plugin add cordova-plugin-media-photo-picker --variable IOS_PHOTO_LIBRARY_USAGE_DESCRIPTION="your usage message"
```

Or from local path:
```bash
cordova plugin add /path/to/cordova-plugin-media-photo-picker --variable IOS_PHOTO_LIBRARY_USAGE_DESCRIPTION="your usage message"
```

## Requirements

- **cordova-android**: >= 10.0.0
- **Android SDK**: 21+ (Android 5.0+)
- **iOS**: 9.0+

## Example

```javascript
var args = {
    'selectMode': 101, // 101=image+video, 100=image only, 102=video only
    'maxSelectCount': 10, // default 10
};

MediaPicker.getMedias(args, function(medias) {
    // medias = [{
    //   mediaType: "image",
    //   path: '/data/user/0/.../cache/photo.jpg',
    //   uri: "file:///data/user/0/.../cache/photo.jpg",
    //   size: 21993,
    //   name: "photo.jpg",
    //   index: 0
    // }]
    console.log(medias);
}, function(error) {
    console.error(error);
});
```

### Get Thumbnail

```javascript
MediaPicker.extractThumbnail(media, function(data) {
    img.src = 'data:image/jpeg;base64,' + data.thumbnailBase64;
    img.style.transform = 'rotate(' + data.exifRotate + 'deg)';
}, function(error) {
    console.error(error);
});
```

### Compress Image

```javascript
media.quality = 50; // 1-100, 100 = original
MediaPicker.compressImage(media, function(compressedData) {
    console.log('Compressed path:', compressedData.path);
    console.log('New size:', compressedData.size);
}, function(error) {
    console.error(error);
});
```

### Take Photo

```javascript
MediaPicker.takePhoto({}, function(media) {
    console.log('Photo taken:', media.path);
}, function(error) {
    console.error(error);
});
```

## API Reference

### MediaPicker.getMedias(options, successCallback, errorCallback)

Opens the system photo picker to select images and/or videos.

**Options:**
- `selectMode` (number): 100 = images only, 101 = images + videos, 102 = videos only
- `maxSelectCount` (number): Maximum number of items to select (default: 10)
- `thumbnailQuality` (number): Quality for thumbnail extraction (1-100, default: 50)
- `thumbnailW` (number): Thumbnail width in pixels (default: 200)
- `thumbnailH` (number): Thumbnail height in pixels (default: 200)

### MediaPicker.takePhoto(options, successCallback, errorCallback)

Opens the camera to take a photo.

### MediaPicker.extractThumbnail(media, successCallback, errorCallback)

Extracts a thumbnail from an image or video.

### MediaPicker.compressImage(media, successCallback, errorCallback)

Compresses an image with specified quality.

### MediaPicker.getFileInfo(pathOrUri, type, successCallback, errorCallback)

Gets file information from a path or URI.

### MediaPicker.fileToBlob(path, successCallback, errorCallback)

Converts a file to a blob/byte array.

### MediaPicker.getExifForKey(path, tag, successCallback, errorCallback)

Gets EXIF metadata for a specific tag.

## Android Permissions

This plugin requires **no media permissions** on Android. It uses:
- **Android 13+**: Native Photo Picker API (`MediaStore.ACTION_PICK_IMAGES`)
- **Android 12 and below**: Storage Access Framework (`ACTION_OPEN_DOCUMENT`)

Only the `CAMERA` permission is requested for the `takePhoto` functionality.

## iOS Permissions

On iOS, the plugin still requires photo library access. Add the usage description:

```xml
<config-file target="*-Info.plist" parent="NSPhotoLibraryUsageDescription">
    <string>This app needs access to your photo library to select photos and videos.</string>
</config-file>
```

## Migration from v2.x

The JavaScript API is fully backward compatible. Simply update the plugin:

1. Remove the old plugin: `cordova plugin rm cordova-plugin-mediapicker-dmcsdk` (or `cordova-plugin-media-photo-picker` if upgrading from a prior v3 install)
2. Add the new version: `cordova plugin add cordova-plugin-media-photo-picker`
3. Rebuild your app

**Note:** The Android UI will now use the system photo picker instead of the custom gallery UI. This provides a consistent experience across all Android apps and ensures Google Play compliance.

## License

ISC

## Credits

Original plugin by [DmcSDK](https://github.com/DmcSDK/cordova-plugin-mediaPicker)

Photo Picker API implementation for Google Play compliance.
