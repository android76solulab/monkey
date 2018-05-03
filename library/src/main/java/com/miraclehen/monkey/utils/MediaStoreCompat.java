/*
 * Copyright 2017 Zhihu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.miraclehen.monkey.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;
import android.support.v4.os.EnvironmentCompat;
import android.text.TextUtils;

import com.miraclehen.monkey.CaptureType;
import com.miraclehen.monkey.entity.CaptureStrategy;
import com.miraclehen.monkey.entity.SelectionSpec;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MediaStoreCompat {

    private static final String BUNDLE_KEY_MEDIA_STORE_COMPAT_URI = "bundle_key_media_store_compat";
    private static final String BUNDLE_KEY_MEDIA_STORE_COMPAT_PATH = "bundle_key_media_store_compat_path";

    private final WeakReference<Activity> mContext;
    private final WeakReference<Fragment> mFragment;
    private CaptureStrategy mCaptureStrategy;
    private Uri mCurrentPhotoUri;
    private String mCurrentCapturePath;

    private SelectionSpec mSpec;


    public MediaStoreCompat(Activity activity) {
        mContext = new WeakReference<>(activity);
        mFragment = null;
        mSpec = SelectionSpec.getInstance();
    }

    public MediaStoreCompat(Activity activity, Fragment fragment) {
        mContext = new WeakReference<>(activity);
        mFragment = new WeakReference<>(fragment);
        mSpec = SelectionSpec.getInstance();
    }

    /**
     * Checks whether the device has a camera feature or not.
     *
     * @param context a context to check for camera feature.
     * @return true if the device has a camera feature. false otherwise.
     */
    public static boolean hasCameraFeature(Context context) {
        PackageManager pm = context.getApplicationContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    public void setCaptureStrategy(CaptureStrategy strategy) {
        mCaptureStrategy = strategy;
    }

    /**
     * 启动拍照或者录像功能
     *
     * @param activity
     * @param requestCode
     */
    public void dispatchCaptureIntent(Activity activity, int requestCode) {
        if (mSpec.captureType == CaptureType.Image) {
            Intent imageIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (imageIntent.resolveActivity(activity.getPackageManager()) != null) {
                File targetFile = null;
                try {
                    targetFile = createFile(mSpec.captureType);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (targetFile != null) {
                    try {
                        //获取文件绝对路径
                        mCurrentCapturePath = targetFile.getAbsolutePath();
                        //获取文件对应的Uri
                        mCurrentPhotoUri = FileProvider.getUriForFile(mContext.get(),
                                mCaptureStrategy.authority, targetFile);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
//                mCurrentPhotoUri = Uri.fromFile(new File(mCurrentCapturePath));
                    imageIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCurrentPhotoUri);
                    imageIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        List<ResolveInfo> resInfoList = activity.getPackageManager()
                                .queryIntentActivities(imageIntent, PackageManager.MATCH_DEFAULT_ONLY);
                        for (ResolveInfo resolveInfo : resInfoList) {
                            String packageName = resolveInfo.activityInfo.packageName;
                            activity.grantUriPermission(packageName, mCurrentPhotoUri,
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                    }
                    activity.startActivityForResult(imageIntent, requestCode);
                }
            }
        } else if (mSpec.captureType == CaptureType.Video) {
            Intent videoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            if (videoIntent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivityForResult(videoIntent, requestCode);
            }
        }
    }


    /**
     * 创建文件
     *
     * @return
     * @throws IOException
     */
    private File createFile(CaptureType type) throws IOException {
        // Create an image file name
        String timeStamp =
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "";
        if (type == CaptureType.Image) {
            fileName = String.format("JPEG_%s.jpg", timeStamp);
        } else {
            fileName = String.format("VIDEO_%s.mp4", timeStamp);
        }
        File storageDir;
        if (mCaptureStrategy.isPublic) {
            if (type == CaptureType.Image) {
                storageDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES);
            } else {
                storageDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MOVIES);
            }

        } else {
            storageDir = mContext.get().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        }

        // Avoid joining path components manually
        File tempFile = new File(storageDir, fileName);

        // Handle the situation that user's external storage is not ready
        if (!Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(tempFile))) {
            return null;
        }

        return tempFile;
    }


    public Uri getCurrentPhotoUri() {
        return mCurrentPhotoUri;
    }

    public String getCurrentCapturePath() {
        return mCurrentCapturePath;
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(BUNDLE_KEY_MEDIA_STORE_COMPAT_URI, mCurrentPhotoUri);
        outState.putString(BUNDLE_KEY_MEDIA_STORE_COMPAT_PATH, mCurrentCapturePath);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        if (savedInstanceState.getParcelable(BUNDLE_KEY_MEDIA_STORE_COMPAT_URI) != null) {
            mCurrentPhotoUri = savedInstanceState.getParcelable(BUNDLE_KEY_MEDIA_STORE_COMPAT_URI);
        }
        if (!TextUtils.isEmpty(savedInstanceState.getString(BUNDLE_KEY_MEDIA_STORE_COMPAT_PATH))) {
            mCurrentCapturePath = savedInstanceState.getString(BUNDLE_KEY_MEDIA_STORE_COMPAT_PATH);
        }
    }
}
