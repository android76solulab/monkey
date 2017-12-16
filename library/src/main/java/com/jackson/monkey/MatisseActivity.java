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
package com.jackson.monkey;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;


import com.jackson.monkey.entity.Album;
import com.jackson.monkey.entity.MediaItem;
import com.jackson.monkey.entity.SelectionSpec;
import com.jackson.monkey.model.AlbumCollection;
import com.jackson.monkey.model.SelectedItemCollection;
import com.jackson.monkey.ui.AlbumPreviewActivity;
import com.jackson.monkey.ui.BasePreviewActivity;
import com.jackson.monkey.ui.MediaSelectionFragment;
import com.jackson.monkey.ui.SelectedPreviewActivity;
import com.jackson.monkey.ui.adapter.AlbumMediaAdapter;
import com.jackson.monkey.ui.adapter.AlbumsAdapter;
import com.jackson.monkey.ui.widget.AlbumsSpinner;
import com.jackson.monkey.ui.widget.PermissionExplainDialog;
import com.jackson.monkey.utils.MediaStoreCompat;

import java.io.File;
import java.util.ArrayList;

/**
 * Main Activity to display albums and media content (images/videos) in each album
 * and also support media selecting operations.
 */
public class MatisseActivity extends AppCompatActivity implements
        AlbumCollection.AlbumCallbacks, AdapterView.OnItemSelectedListener,
        MediaSelectionFragment.SelectionProvider, View.OnClickListener,
        AlbumMediaAdapter.CheckStateListener, AlbumMediaAdapter.OnMediaClickListener,
        AlbumMediaAdapter.OnPhotoCapture, ActivityCompat.OnRequestPermissionsResultCallback {

    public static final String TAG = MatisseActivity.class.getSimpleName();

    public static final int PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 0x11;
    public static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 0x12;

    public static final String EXTRA_RESULT_SELECTION = "extra_result_selection";
    public static final String EXTRA_RESULT_SELECTION_ITEM = "extra_result_selection_item";
    public static final String EXTRA_RESULT_SELECTION_PATH = "extra_result_selection_path";
    public static final String EXTRA_CONTENT_URI = "extra_content_uri";
    public static final String EXTRA_CONTENT_PATH = "extra_content_path";
    private static final int REQUEST_CODE_PREVIEW = 23;
    private static final int REQUEST_CODE_CAPTURE = 24;
    private static final int REQUEST_CODE_VIDEO = 25;
    private final AlbumCollection mAlbumCollection = new AlbumCollection();
    private MediaStoreCompat mMediaStoreCompat;
    //已选择的Item
    private SelectedItemCollection mSelectedCollection = new SelectedItemCollection(this);
    private SelectionSpec mSpec;

    private AlbumsSpinner mAlbumsSpinner;
    private AlbumsAdapter mAlbumsAdapter;
    private TextView mButtonPreview;
    private TextView mButtonApply;
    private View mContainer;
    private View mEmptyView;
    private Album mCurrentAlbum;

    private boolean capterLater = false;

    private int mCaptureType = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // programmatically set theme before super.onCreate()
        mSpec = SelectionSpec.getInstance();
        setTheme(mSpec.themeId);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_matisse);

        if (mSpec.needOrientationRestriction()) {
            setRequestedOrientation(mSpec.orientation);
        }

        if (mSpec.isCapture()) {
            mMediaStoreCompat = new MediaStoreCompat(this);
            if (mSpec.captureStrategy == null)
                throw new RuntimeException("Don't forget to set CaptureStrategy.");
            mMediaStoreCompat.setCaptureStrategy(mSpec.captureStrategy);
            mMediaStoreCompat.onRestoreInstanceState(savedInstanceState);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        Drawable navigationIcon = toolbar.getNavigationIcon();
        TypedArray ta = getTheme().obtainStyledAttributes(new int[]{R.attr.album_element_color});
        int color = ta.getColor(0, 0);
        ta.recycle();
        navigationIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN);

        mButtonPreview = (TextView) findViewById(R.id.button_preview);
        mButtonApply = (TextView) findViewById(R.id.button_apply);
        mButtonPreview.setOnClickListener(this);
        mButtonApply.setOnClickListener(this);
        mContainer = findViewById(R.id.container);
        mEmptyView = findViewById(R.id.empty_view);

        mSelectedCollection.onCreate(savedInstanceState);
        updateBottomToolbar();

        mAlbumsAdapter = new AlbumsAdapter(this, null, false);
        mAlbumsSpinner = new AlbumsSpinner(this);
        mAlbumsSpinner.setOnItemSelectedListener(this);
        mAlbumsSpinner.setSelectedTextView((TextView) findViewById(R.id.selected_album));
        mAlbumsSpinner.setPopupAnchorView(findViewById(R.id.toolbar));
        mAlbumsSpinner.setAdapter(mAlbumsAdapter);
        mAlbumCollection.onCreate(this, this);
        mAlbumCollection.onRestoreInstanceState(savedInstanceState);

        if (Build.VERSION.SDK_INT < 22) {
            loadAlbums();
        } else {
            requestReadStoragePermission();
        }

    }

    private void loadAlbums() {
        mAlbumCollection.loadAlbums();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mSelectedCollection.onSaveInstanceState(outState);
        mAlbumCollection.onSaveInstanceState(outState);
        if (mMediaStoreCompat != null) {
            mMediaStoreCompat.onSaveInstanceState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAlbumCollection.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(final int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK)
            return;

        if (requestCode == REQUEST_CODE_PREVIEW) {
            Bundle resultBundle = data.getBundleExtra(BasePreviewActivity.EXTRA_RESULT_BUNDLE);
            ArrayList<MediaItem> selected = resultBundle.getParcelableArrayList(SelectedItemCollection.STATE_SELECTION);
            int collectionType = resultBundle.getInt(SelectedItemCollection.STATE_COLLECTION_TYPE,
                    SelectedItemCollection.COLLECTION_UNDEFINED);
            if (data.getBooleanExtra(BasePreviewActivity.EXTRA_RESULT_APPLY, false)) {
                Intent result = new Intent();
                result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION_ITEM, selected);
                setResult(RESULT_OK, result);
                finish();
            } else {
                mSelectedCollection.overwrite(selected, collectionType);
                Fragment mediaSelectionFragment = getSupportFragmentManager().findFragmentByTag(
                        MediaSelectionFragment.class.getSimpleName());
                if (mediaSelectionFragment instanceof MediaSelectionFragment) {
                    ((MediaSelectionFragment) mediaSelectionFragment).refreshMediaGrid();
                }
                updateBottomToolbar();
            }
        } else if (requestCode == REQUEST_CODE_CAPTURE || requestCode == REQUEST_CODE_VIDEO) {
            capterLater = true;
            //文件的路径
            final String path = mMediaStoreCompat.getCurrentPhotoPath();
            //文件的Uri
            Uri contentUri = Uri.fromFile(new File(path));
            //拍完照或者录制视频之后
            //通知数据库更新，不然无法显示刚刚拍摄的文件
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            intent.setData(contentUri);
            sendBroadcast(intent);

            MediaSelectionFragment mediaSelectionFragment = (MediaSelectionFragment) getSupportFragmentManager().findFragmentByTag(
                    MediaSelectionFragment.class.getSimpleName());
            mediaSelectionFragment.captureLater(new MediaSelectionFragment.OnGetTargetMediaItemLaterCallback() {
                @Override
                public void later(MediaItem mediaItem) {
                    ArrayList<MediaItem> selected = new ArrayList<MediaItem>();
                    selected.add(mediaItem);
                    Intent result = new Intent();
                    result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION_ITEM, selected);
                    setResult(RESULT_OK, result);
                    finish();
                }
            });

        }

    }

    @RequiresApi(22)
    private void requestReadStoragePermission() {
        if (ActivityCompat.checkSelfPermission(MatisseActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                //show tip
                PermissionExplainDialog.newInstance("应用需要访问你的相册的权限,来展示你手机中的相册数据", new PermissionExplainDialog.OnDialogPositiveButtonClickListener() {
                    @Override
                    public void onClick() {
                        ActivityCompat.requestPermissions(MatisseActivity.this,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
                    }
                }).show(getSupportFragmentManager(), PermissionExplainDialog.class.getSimpleName());
            } else {
                ActivityCompat.requestPermissions(MatisseActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
            }
        } else {
            //已有权限
            loadAlbums();
        }
    }

    @RequiresApi(22)
    private void requestWriteStoragePermission() {
        if (ActivityCompat.checkSelfPermission(MatisseActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PermissionExplainDialog.newInstance("应用需要访问你的相册的权限，来存储你的拍照文件", new PermissionExplainDialog.OnDialogPositiveButtonClickListener() {
                    @Override
                    public void onClick() {
                        ActivityCompat.requestPermissions(MatisseActivity.this,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
                    }
                }).show(getSupportFragmentManager(), PermissionExplainDialog.class.getSimpleName());
            } else {
                ActivityCompat.requestPermissions(MatisseActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
            }

        } else {
            if (mSpec.isCapture()) {
                if (mSpec.captureType == CaptureType.Image) {
                    mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_CAPTURE, CaptureType.Image);
                }else {
                    mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_VIDEO,CaptureType.Video);
                }
            }
        }
    }

    private void updateBottomToolbar() {
        int selectedCount = mSelectedCollection.count();
        if (selectedCount == 0) {
            mButtonPreview.setEnabled(false);
            mButtonApply.setEnabled(false);
            mButtonApply.setText(getString(R.string.button_apply_default));
        } else if (selectedCount == 1 && mSpec.singleSelectionModeEnabled()) {
            mButtonPreview.setEnabled(true);
            mButtonApply.setText(R.string.button_apply_default);
            mButtonApply.setEnabled(true);
        } else {
            mButtonPreview.setEnabled(true);
            mButtonApply.setEnabled(true);
            mButtonApply.setText(getString(R.string.button_apply, selectedCount));
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_preview) {
            //预览被点击
            Intent intent = new Intent(this, SelectedPreviewActivity.class);
            intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
            startActivityForResult(intent, REQUEST_CODE_PREVIEW);
        } else if (v.getId() == R.id.button_apply) {
            //使用按钮被点击
            Intent result = new Intent();
            ArrayList<Uri> selectedUris = (ArrayList<Uri>) mSelectedCollection.asListOfUri();
            result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selectedUris);
            ArrayList<String> selectedPaths = (ArrayList<String>) mSelectedCollection.asListOfString();
            result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPaths);
            result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION_ITEM, new ArrayList<Parcelable>(mSelectedCollection.asList()));
            setResult(RESULT_OK, result);
            finish();
        }
    }

    /**
     * 顶部相册被选择
     *
     * @param parent
     * @param view
     * @param position
     * @param id
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mAlbumCollection.setStateCurrentSelection(position);
        mAlbumsAdapter.getCursor().moveToPosition(position);
        Album album = Album.valueOf(mAlbumsAdapter.getCursor());
        if (album.isAll() && SelectionSpec.getInstance().isCapture()) {
            album.addCaptureCount();
        }
        onAlbumSelected(album);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    /**
     * 相册加载完毕
     *
     * @param cursor
     */
    @Override
    public void onAlbumLoad(final Cursor cursor) {
        mAlbumsAdapter.swapCursor(cursor);
        // select default album.
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                cursor.moveToPosition(mAlbumCollection.getCurrentSelection());
                mAlbumsSpinner.setSelection(MatisseActivity.this,
                        mAlbumCollection.getCurrentSelection());
                mCurrentAlbum = Album.valueOf(cursor);
                if (mCurrentAlbum.isAll() && SelectionSpec.getInstance().isCapture()) {
                    mCurrentAlbum.addCaptureCount();
                }
                onAlbumSelected(mCurrentAlbum);
            }
        });
    }

    @Override
    public void onAlbumReset() {
        mAlbumsAdapter.swapCursor(null);
    }

    @Override
    public void onCaptureLoad(Cursor cursor) {
        if (cursor != null && cursor.getCount() > 0) {

        }
    }

    @Override
    public void onCaptureReset() {

    }

    /**
     * 选择一本相册，MediaSelectionFragment是这本相册的所有相片和视频
     *
     * @param album
     */
    private void onAlbumSelected(Album album) {
        if (album.isAll() && album.isEmpty()) {
            mContainer.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mContainer.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
            Fragment fragment = MediaSelectionFragment.newInstance(album);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, fragment, MediaSelectionFragment.class.getSimpleName())
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public void onUpdate() {
        // notify bottom toolbar that check state changed.
        updateBottomToolbar();
    }

    /**
     * 图像或者视频被点击
     *
     * @param album
     * @param item
     * @param adapterPosition
     */
    @Override
    public void onMediaClick(Album album, MediaItem item, int adapterPosition) {
        //当图片或者视频被点击
        Intent intent = new Intent(this, AlbumPreviewActivity.class);
        intent.putExtra(AlbumPreviewActivity.EXTRA_ALBUM, album);
        intent.putExtra(AlbumPreviewActivity.EXTRA_ITEM, item);
        //传递已经选择的数据
        intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
        startActivityForResult(intent, REQUEST_CODE_PREVIEW);
    }

    @Override
    public SelectedItemCollection provideSelectedItemCollection() {
        return mSelectedCollection;
    }

    /**
     * 启动拍照
     */
    @Override
    public void capture() {
        if (Build.VERSION.SDK_INT < 22) {
            mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_CAPTURE, CaptureType.Image);
        } else {
            requestWriteStoragePermission();
        }

    }

    /**
     * 启动摄像
     */
    @Override
    public void record() {
        if (Build.VERSION.SDK_INT < 22) {
            mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_VIDEO, CaptureType.Video);
        } else {
            requestWriteStoragePermission();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //权限允许
                loadAlbums();
            } else {
                //权限被拒绝
                Toast.makeText(MatisseActivity.this, "无法获取到访问你的相册权限", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_VIDEO, CaptureType.Video);
            } else {
                //被拒绝
                Toast.makeText(MatisseActivity.this, "无法获取到写入文件权限", Toast.LENGTH_LONG).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

    }
}
