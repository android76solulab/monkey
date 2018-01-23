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
package com.miraclehen.monkey;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.miraclehen.monkey.entity.Album;
import com.miraclehen.monkey.entity.MediaItem;
import com.miraclehen.monkey.entity.SelectionSpec;
import com.miraclehen.monkey.model.AlbumCollection;
import com.miraclehen.monkey.model.SelectedItemCollection;
import com.miraclehen.monkey.ui.AlbumPreviewActivity;
import com.miraclehen.monkey.ui.BasePreviewActivity;
import com.miraclehen.monkey.ui.MediaSelectionFragment;
import com.miraclehen.monkey.ui.PermissionExplainDialog;
import com.miraclehen.monkey.ui.SelectedPreviewActivity;
import com.miraclehen.monkey.ui.adapter.AlbumsAdapter;
import com.miraclehen.monkey.ui.widget.AlbumsSpinner;
import com.miraclehen.monkey.utils.MediaStoreCompat;

import java.io.File;
import java.util.ArrayList;

import io.reactivex.functions.Consumer;

/**
 * Main Activity to display albums and media content (images/videos) in each album
 * and also support media selecting operations.
 */
public class MatisseActivity extends AppCompatActivity implements
        AlbumCollection.AlbumCallbacks, AdapterView.OnItemSelectedListener,
        MediaSelectionFragment.SelectionProvider, View.OnClickListener,
        UICallback {

    public static final String TAG = MatisseActivity.class.getSimpleName();

    public static final int PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 0x11;
    public static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 0x12;
    public static final int PERMISSION_REQUEST_CAMERA = 0x13;

    public static final String EXTRA_RESULT_SELECTION_ITEM = "extra_result_selection_item";
    public static final String EXTRA_CONTENT_URI = "extra_content_uri";
    public static final String EXTRA_CONTENT_PATH = "extra_content_path";

    private static final int REQUEST_CODE_PREVIEW = 23;
    private static final int REQUEST_CODE_CAPTURE = 24;

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
    private FrameLayout mBottomToolbar;
    private FrameLayout mToolbarWrapperLayout;
    private TextView mAnchorView;


    private OnScanCompletedListenerImpl mOnScanCompletedCallback;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // programmatically set theme before super.onCreate()
        mSpec = SelectionSpec.getInstance();
        setTheme(mSpec.themeId);
        super.onCreate(savedInstanceState);

//        UIUtils.immersiveStatusBar(getWindow(), 0);

        setContentView(R.layout.activity_matisse);

        if (mSpec.needOrientationRestriction()) {
            setRequestedOrientation(mSpec.orientation);
        }

        if (mSpec.isCapture()) {
            mMediaStoreCompat = new MediaStoreCompat(this);
            if (mSpec.captureStrategy == null) {
                throw new RuntimeException("Don't forget to set CaptureStrategy.");
            }
            mMediaStoreCompat.setCaptureStrategy(mSpec.captureStrategy);
            mMediaStoreCompat.onRestoreInstanceState(savedInstanceState);
        }

        mButtonPreview = findViewById(R.id.button_preview);
        mButtonApply = findViewById(R.id.button_apply);
        mButtonPreview.setOnClickListener(this);
        mButtonApply.setOnClickListener(this);
        mContainer = findViewById(R.id.container);
        mEmptyView = findViewById(R.id.empty_view);
        mBottomToolbar = findViewById(R.id.bottom_toolbar);
        mToolbarWrapperLayout = findViewById(R.id.toolbar_layout);
        mAnchorView = findViewById(R.id.selected_album);

        //初始化布局头部布局
        initToolbarLayout();

        if (mSpec.singleResultModel) {
            //如果是单选模式。底部不需要显示
            mBottomToolbar.setVisibility(View.GONE);
        }

        mSelectedCollection.onCreate(savedInstanceState);
        updateBottomToolbar();

        mAlbumsAdapter = new AlbumsAdapter(this, null, false);
        mAlbumsSpinner = new AlbumsSpinner(this);
        mAlbumsSpinner.setOnItemSelectedListener(this);
        mAlbumsSpinner.setSelectedTextView(mAnchorView);
        mAlbumsSpinner.setPopupAnchorView(mAnchorView);
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

    private void initToolbarLayout() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (mSpec.toolbarLayoutId == -1) {
            setSupportActionBar(toolbar);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayShowTitleEnabled(false);
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
            Drawable navigationIcon = toolbar.getNavigationIcon();
            TypedArray ta = getTheme().obtainStyledAttributes(new int[]{R.attr.album_element_color});
            int color = ta.getColor(0, 0);
            ta.recycle();
            if (navigationIcon != null) {
                navigationIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        } else {
            toolbar.setVisibility(View.GONE);
            View rootView = LayoutInflater.from(this).inflate(mSpec.toolbarLayoutId, null);
            mToolbarWrapperLayout.addView(rootView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            View backView = rootView.findViewById(mSpec.backViewId);
            backView.setVisibility(View.VISIBLE);
            backView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MatisseActivity.this.finish();
                }
            });
            mAnchorView = (TextView) findViewById(mSpec.anchorViewId);
            mAnchorView.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    ContextCompat.getDrawable(this, R.drawable.ic_arrow_drop_down_white_24dp), null);
        }
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
        if (mOnScanCompletedCallback != null) {
            mOnScanCompletedCallback.clearCallback();
        }
        mOnScanCompletedCallback = null;
        mSpec.checkListener = null;
        try {
            mAlbumCollection.onDestroy();
            super.onDestroy();
        }catch (Exception e){
            e.printStackTrace();
        }

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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == REQUEST_CODE_PREVIEW) {
            Bundle resultBundle = data.getBundleExtra(BasePreviewActivity.EXTRA_RESULT_BUNDLE);
            ArrayList<MediaItem> selected = resultBundle.getParcelableArrayList(SelectedItemCollection.STATE_SELECTION);
            if (selected == null) {
                return;
            }

            if (data.getBooleanExtra(BasePreviewActivity.EXTRA_RESULT_APPLY, false)) {
                Intent result = new Intent();
                result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION_ITEM, selected);
                setResult(RESULT_OK, result);
                finish();
            } else {
                mSelectedCollection.overwrite(selected);
                Fragment mediaSelectionFragment = getSupportFragmentManager().findFragmentByTag(
                        MediaSelectionFragment.class.getSimpleName());
                if (mediaSelectionFragment instanceof MediaSelectionFragment) {
                    ((MediaSelectionFragment) mediaSelectionFragment).refreshMediaGrid();
                }
                updateBottomToolbar();
            }
        } else if (requestCode == REQUEST_CODE_CAPTURE) {
            //拍完照或者录制视频之后
            //文件的路径
            final String path = mMediaStoreCompat.getCurrentPhotoPath();
            File file = new File(path);
            //文件的Uri
            Uri contentUri = Uri.fromFile(file);
//            // 其次把文件插入到系统图库
//            try {
//                MediaStore.Images.Media.insertImage(getContentResolver(),
//                        file.getAbsolutePath(), file.getName(), null);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }

            //拍完照或者录制视频之后
            //通知数据库更新，不然无法显示刚刚拍摄的文件
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            intent.setData(contentUri);
            sendBroadcast(intent);

            mOnScanCompletedCallback = new OnScanCompletedListenerImpl(new Consumer<String>() {
                @Override
                public void accept(String s) throws Exception {
                    //重新加载并且处理拍摄的数据
                    loadAndProcessCaptureLoader(path);
                }
            });
            MediaScannerConnection.scanFile(getApplicationContext(), new String[]{path}, null, mOnScanCompletedCallback);
        }
    }

    /**
     * 重新加载并且处理拍摄的数据
     */
    private void loadAndProcessCaptureLoader(String capturePath) {
        MediaSelectionFragment mediaSelectionFragment = (MediaSelectionFragment) getSupportFragmentManager()
                .findFragmentByTag(MediaSelectionFragment.class.getSimpleName());
        if (mediaSelectionFragment != null) {
            mediaSelectionFragment.reloadForCapture(mSpec.captureFinishBack ? captureLaterCallback : null, capturePath);
        }
    }

    MediaSelectionFragment.OnGetTargetMediaItemLaterCallback captureLaterCallback
            = new MediaSelectionFragment.OnGetTargetMediaItemLaterCallback() {
        @Override
        public void later(MediaItem mediaItem) {
            mSelectedCollection.add(mediaItem);
            Intent result = new Intent();
            result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION_ITEM,
                    mSelectedCollection.asList(true));
            setResult(RESULT_OK, result);
            finish();
        }
    };

    private void updateBottomToolbar() {
        int selectedCount = mSelectedCollection.count() + mSpec.selectedDataList.size();
        if (selectedCount == 0) {
            mButtonPreview.setEnabled(false);
            mButtonApply.setEnabled(false);
            mButtonApply.setText(getString(R.string.button_apply_default));
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
            result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION_ITEM, mSelectedCollection.asList(true));
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
        if (cursor == null || cursor.isClosed()) {
            return;
        }
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

    /**
     * 选择一本相册，MediaSelectionFragment是这本相册的所有相片和视频
     *
     * @param album
     */
    private void onAlbumSelected(Album album) {
        //修复不能拍摄
        if (album.isAll() && album.isEmpty() && !mSpec.isCapture()) {
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
    public void updateBottomBarCount() {
        // notify bottom toolbar that check state changed.
        updateBottomToolbar();
    }

    /**
     * 启动拍摄
     *
     * @param captureType 拍摄类型
     */
    @Override
    public void startCapture(CaptureType captureType) {
        if (Build.VERSION.SDK_INT < 22) {
            mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_CAPTURE);
        } else {
            requestWriteStoragePermission(captureType);
        }
    }

    /**
     * 图像或者视频被点击
     * 进入详情页
     *
     * @param album
     * @param item
     * @param adapterPosition
     */
    @Override
    public void onMediaClick(Album album, MediaItem item, int adapterPosition) {
        if (mSpec.singleResultModel) {
            mSelectedCollection.add(item);
            //如果是单一结果模式。直接结束选择
            mButtonApply.callOnClick();
            return;
        }

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

    @Override
    public void finish() {
        //将原来的数据返回回去
        super.finish();
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }


    /**
     * 请求相机拍摄权限
     *
     * @param captureType
     */
    @RequiresApi(22)
    private void requestPhotoCameraPermission(CaptureType captureType) {
        if (ActivityCompat.checkSelfPermission(MatisseActivity.this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                PermissionExplainDialog.newInstance("应用需要访问你的相机的权限", new PermissionExplainDialog.OnDialogPositiveButtonClickListener() {
                    @Override
                    public void onClick() {
                        ActivityCompat.requestPermissions(MatisseActivity.this,
                                new String[]{Manifest.permission.CAMERA},
                                PERMISSION_REQUEST_CAMERA);
                    }
                }).show(getSupportFragmentManager(), PermissionExplainDialog.class.getSimpleName());
            } else {
                ActivityCompat.requestPermissions(MatisseActivity.this,
                        new String[]{Manifest.permission.CAMERA},
                        PERMISSION_REQUEST_CAMERA);
            }
        } else {
            if (mSpec.isCapture()) {
                mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_CAPTURE);
            }
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

    /**
     * 请求写入文件权限
     *
     * @param captureType
     */
    @RequiresApi(22)
    private void requestWriteStoragePermission(CaptureType captureType) {
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
            requestPhotoCameraPermission(captureType);
        }
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
            //请求写入权限
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_CAPTURE);
            } else {
                //被拒绝
                Toast.makeText(MatisseActivity.this, "无法获取到写入文件权限", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == PERMISSION_REQUEST_CAMERA) {
            //请求相机权限
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_CAPTURE);
            } else {
                //被拒绝
                Toast.makeText(MatisseActivity.this, "无法获取到拍摄权限", Toast.LENGTH_LONG).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
