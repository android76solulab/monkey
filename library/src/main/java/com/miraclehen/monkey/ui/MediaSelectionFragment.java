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
package com.miraclehen.monkey.ui;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.miraclehen.monkey.R;
import com.miraclehen.monkey.UICallback;
import com.miraclehen.monkey.entity.Album;
import com.miraclehen.monkey.entity.IncapableCause;
import com.miraclehen.monkey.entity.MediaItem;
import com.miraclehen.monkey.entity.SelectionSpec;
import com.miraclehen.monkey.model.AlbumMediaCollection;
import com.miraclehen.monkey.model.SelectedItemCollection;
import com.miraclehen.monkey.ui.adapter.AlbumMediaAdapter;


/**
 * 一本相册的内容
 */
public class MediaSelectionFragment extends Fragment implements
        AlbumMediaCollection.AlbumMediaCallbacks, UICallback.CheckStateListener,
        UICallback.OnMediaClickListener {

    public static final String EXTRA_ALBUM = "extra_album";

    private final AlbumMediaCollection mAlbumMediaCollection = new AlbumMediaCollection();
    private RecyclerView mRecyclerView;
    private AlbumMediaAdapter mAdapter;
    private SelectionProvider mSelectionProvider;

    private UICallback.CheckStateListener mCheckStateListener;
    private UICallback.OnMediaClickListener mOnMediaClickListener;
    private UICallback.LoadingDialogCallback mLoadingDialogCallback;

    private Album mAlbum;
    private SelectionSpec mSelectionSpec;
    private SelectedItemCollection mSelectedItemCollection;

    /**
     * 拍摄之后的回调
     * 仅当你SelectionSpec.finishBack = true时候，才会执行
     */
    private OnGetTargetMediaItemLaterCallback mCaptureLaterCallback;
    /**
     * 标志位
     * 是否是拍摄之后的重新加载
     */
    private boolean isCaptureLater = false;

    /**
     * 拍摄的路径
     */
    private String mCapturePath;

    public static MediaSelectionFragment newInstance(Album album) {
        MediaSelectionFragment fragment = new MediaSelectionFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_ALBUM, album);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof SelectionProvider) {
            mSelectionProvider = (SelectionProvider) context;
        } else {
            throw new IllegalStateException("Context must implement SelectionProvider.");
        }
        if (context instanceof UICallback.CheckStateListener) {
            mCheckStateListener = (UICallback.CheckStateListener) context;
        }
        if (context instanceof UICallback.OnMediaClickListener) {
            mOnMediaClickListener = (UICallback.OnMediaClickListener) context;
        }
        if (context instanceof UICallback.LoadingDialogCallback) {
            mLoadingDialogCallback = (UICallback.LoadingDialogCallback) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_media_selection, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRecyclerView = view.findViewById(R.id.recyclerview);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mAlbum = getArguments().getParcelable(EXTRA_ALBUM);
        mSelectionSpec = SelectionSpec.getInstance();

        mSelectedItemCollection = mSelectionProvider.provideSelectedItemCollection();
        mAdapter = new AlbumMediaAdapter(getContext(), mAlbum,
                mSelectedItemCollection, mRecyclerView, mSelectionSpec.selectedDataList);
        mAdapter.registerCheckStateListener(this);
        mAdapter.registerOnMediaClickListener(this);
        mRecyclerView.setHasFixedSize(true);

        mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 4));
//        mRecyclerView.addItemDecoration(new SpacesItemDecoration(UIUtils.convertDIPToPixels(getContext(), 2)));

        mRecyclerView.setAdapter(mAdapter);
        mAlbumMediaCollection.onCreate(getActivity(), this);
        mAlbumMediaCollection.load(mAlbum, mSelectionSpec.captureType);

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mAlbumMediaCollection.onDestroy();
    }

    public void refreshMediaGrid() {
        mAdapter.notifyDataSetChanged();
    }

    /**
     * 数据加载结束
     * <p>
     * mCaptureLaterCallback 不为空说明是拍摄回来，并且拍摄之后直接返回数据给client
     *
     * @param cursor
     */
    @Override
    public void onAlbumMediaLoad(Cursor cursor) {
        if (isCaptureLater) {
            //拍摄之后回来的
            //先获取到该条数据cursor
            Cursor captureCursor = obtainCaptureCursor(cursor);
            MediaItem mediaItem = MediaItem.valueOf(cursor);
            if (mSelectionSpec.finishBack) {
                //直接返回该图片或者视频数据
                if (captureCursor != null) {
                    mCaptureLaterCallback.later(mediaItem);
                }
                return;
            }else {
                //不直接返回，并且勾选此数据. 在页面上刷新
                if (assertAddSelection(getContext(), mediaItem)) {
                    mSelectedItemCollection.add(mediaItem);
                    //更新底部工具栏个数
                    onUpdate();
                }
            }
            //消费掉此事件
            consumeCaptureEvent();
        }

        cursor.moveToPosition(-1);
        processData(cursor);
        cursor.moveToPosition(-1);
        mAdapter.swapCursor(cursor);

    }

    /**
     * 是否能添加
     * @param context
     * @param item
     * @return
     */
    private boolean assertAddSelection(Context context, MediaItem item) {
        IncapableCause cause = mSelectedItemCollection.isAcceptable(item);
        IncapableCause.handleCause(context, cause);
        return cause == null;
    }

    /**
     * 消费掉此次拍摄事件
     */
    private void consumeCaptureEvent(){
        //给相关变量赋值
        mCaptureLaterCallback = null;
        mCapturePath = "";
        isCaptureLater = false;
    }

    /**
     * 获取到拍摄文件的cursor
     *
     * @return
     */
    private Cursor obtainCaptureCursor(Cursor cursor) {
        if (!mAlbum.isAll() || !mSelectionSpec.isCapture() || TextUtils.isEmpty(mCapturePath.trim())) {
            return null;
        }
        int existCount = 1;
        if (cursor != null && cursor.getCount() > existCount) {
            cursor.moveToPosition(existCount);
            do {
                if (mCapturePath.equals(cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)))) {
                    return cursor;
                }
            } while (cursor.moveToNext());
        }
        return null;
    }


    /**
     * 处理数据。
     * 将外部传进来的selectedUris与本地的数据的uri对比。如果一致。将添加到mSelectedItemCollection中。
     */
    private void processData(Cursor cursor) {
        if (!mSelectedItemCollection.isEmpty()) {
            return;
        }
        while (cursor.moveToNext()) {
            final MediaItem item = MediaItem.valueOf(cursor);
            if (mSelectionSpec.selectedDataList.contains(item)) {
                mSelectedItemCollection.add(item);
                mSelectionSpec.selectedDataList.remove(item);
            }
            if (mSelectionSpec.selectedDataList.size() == 0) {
                break;
            }
        }
        if (mCheckStateListener != null) {
            mCheckStateListener.onUpdate();
        }
    }

    @Override
    public void onAlbumMediaReset() {
        mAdapter.swapCursor(null);
    }

    @Override
    public void onUpdate() {
        // notify outer Activity that check state changed
        if (mCheckStateListener != null) {
            mCheckStateListener.onUpdate();
        }
    }

    @Override
    public void onMediaClick(Album album, MediaItem item, int adapterPosition) {
        if (mOnMediaClickListener != null) {
            mOnMediaClickListener.onMediaClick((Album) getArguments().getParcelable(EXTRA_ALBUM),
                    item, adapterPosition);
        }
    }

    public interface SelectionProvider {
        SelectedItemCollection provideSelectedItemCollection();
    }

    /**
     * 拍摄之后的重新加载数据
     * @param callback
     * @param capturePath
     */
    public void reloadForCapture(OnGetTargetMediaItemLaterCallback callback, String capturePath) {
        //重新加载数据
        mAlbumMediaCollection.restart(mAlbum, mSelectionSpec.captureType);

        //给相关变量赋值
        mCaptureLaterCallback = callback;
        mCapturePath = capturePath;
        isCaptureLater = true;
    }

    public interface OnGetTargetMediaItemLaterCallback {
        void later(MediaItem mediaItem);
    }

}
