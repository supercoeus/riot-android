/*
 * Copyright 2015 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.data.EventTimeline;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;

import java.util.ArrayList;
import java.util.List;

import im.vector.R;
import im.vector.activity.VectorBaseSearchActivity;
import im.vector.activity.VectorRoomActivity;

import im.vector.adapters.VectorMessagesAdapter;
import im.vector.adapters.VectorSearchMessagesListAdapter;

public class VectorSearchMessagesListFragment extends VectorMessageListFragment {
    private static final String LOG_TAG = "VSearchMsgListFrag";

    // parameters
    protected String mPendingPattern;
    protected String mSearchingPattern;
    protected ArrayList<OnSearchResultListener> mSearchListeners = new ArrayList<>();

    protected View mProgressView = null;

    protected String mRoomId;

    /**
     * static constructor
     * @param matrixId the session Id.
     * @param layoutResId the used layout.
     */
    public static VectorSearchMessagesListFragment newInstance(String matrixId, String roomId, int layoutResId) {
        VectorSearchMessagesListFragment frag = new VectorSearchMessagesListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);

        if (null != roomId) {
            args.putString(ARG_ROOM_ID, roomId);
        }

        frag.setArguments(args);
        return frag;
    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Bundle args = getArguments();

        if (null != args) {
            mRoomId = args.getString(ARG_ROOM_ID, null);
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public MessagesAdapter createMessagesAdapter() {
        return new VectorSearchMessagesListAdapter(mSession, getActivity(), (null == mRoomId), getMXMediasCache());
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mSession.isAlive()) {
            cancelSearch();

            if (mIsMediaSearch) {
                mSession.cancelSearchMediasByText();
            } else {
                mSession.cancelSearchMessagesByText();
            }
            mSearchingPattern = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mSession.isAlive()) {
            if (getActivity() instanceof VectorBaseSearchActivity.IVectorSearchActivity) {
                ((VectorBaseSearchActivity.IVectorSearchActivity) getActivity()).refreshSearch();
            }
        }
    }

    /**
     * Called when a fragment is first attached to its activity.
     * {@link #onCreate(Bundle)} will be called after this.
     *
     * @param aHostActivity parent activity
     */
    @Override
    public void onAttach(Activity aHostActivity) {
        super.onAttach(aHostActivity);
        mProgressView = getActivity().findViewById(R.id.search_load_oldest_progress);
    }

    /**
     * The user scrolls the list.
     * Apply an expected behaviour
     * @param event the scroll event
     */
    @Override
    public void onListTouch(MotionEvent event) {
    }

    /**
     * return true to display all the events.
     * else the unknown events will be hidden.
     */
    @Override
    public boolean isDisplayAllEvents() {
        return true;
    }

    /**
     * Display a global spinner or any UI item to warn the user that there are some pending actions.
     */
    @Override
    public void showLoadingBackProgress() {
        if (null != mProgressView) {
            mProgressView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Dismiss any global spinner.
     */
    @Override
    public void hideLoadingBackProgress() {
        if (null != mProgressView) {
            mProgressView.setVisibility(View.GONE);
        }
    }

    /**
     * Scroll the fragment to the bottom
     */
    @Override
    public void scrollToBottom() {
        if (0 != mAdapter.getCount()) {
            mMessageListView.setSelection(mAdapter.getCount() - 1);
        }
    }

    /**
     * Tell if the search is allowed for a dedicated pattern
     * @param pattern the searched pattern.
     * @return true if the search is allowed.
     */
    protected boolean allowSearch(String pattern) {
        // ConsoleMessageListFragment displays the list of unfiltered messages when there is no pattern
        // in the search case, clear the list and hide it
        return !TextUtils.isEmpty(pattern);
    }

    /**
     * Update the searched pattern.
     * @param pattern the pattern to find out. null to disable the search mode
     */
    @Override
    public void searchPattern(final String pattern, final OnSearchResultListener onSearchResultListener) {
        // add the listener to list to warn when the search is done.
        if (null != onSearchResultListener) {
            mSearchListeners.add(onSearchResultListener);
        }

        // wait that the fragment is displayed
        if (null == mMessageListView) {
            mPendingPattern = pattern;
            return;
        }

        // please wait
        if (TextUtils.equals(mSearchingPattern, pattern)) {
            mSearchListeners.add(onSearchResultListener);
            return;
        }

        if (!allowSearch(pattern)) {
            mPattern = null;
            mMessageListView.setVisibility(View.GONE);

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (OnSearchResultListener listener : mSearchListeners) {
                        try {
                            listener.onSearchSucceed(0);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## searchPattern() : failed " + e.getMessage());
                        }
                    }
                    mSearchListeners.clear();
                    mSearchingPattern = null;
                }
            });
        } else {
            // start a search
            mAdapter.clear();
            mSearchingPattern = pattern;

            if (mAdapter instanceof VectorSearchMessagesListAdapter) {
                ((VectorSearchMessagesListAdapter) mAdapter).setTextToHighlight(pattern);
            }

            super.searchPattern(pattern, mIsMediaSearch,  new OnSearchResultListener() {
                @Override
                public void onSearchSucceed(int nbrMessages) {
                    // the pattern has been updated while search
                    if (!TextUtils.equals(pattern, mSearchingPattern)) {
                        mAdapter.clear();
                        mMessageListView.setVisibility(View.GONE);
                    } else {

                        mIsInitialSyncing = false;
                        mMessageListView.setOnScrollListener(mScrollListener);
                        mMessageListView.setAdapter(mAdapter);

                        // scroll to the bottom
                        scrollToBottom();
                        mMessageListView.setVisibility(View.VISIBLE);

                        for (OnSearchResultListener listener : mSearchListeners) {
                            try {
                                listener.onSearchSucceed(nbrMessages);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## searchPattern() : failed " + e.getMessage());
                            }
                        }
                        mSearchListeners.clear();
                        mSearchingPattern = null;
                    }
                }

                @Override
                public void onSearchFailed() {
                    mMessageListView.setVisibility(View.GONE);

                    // clear the results list if teh search fails
                    mAdapter.clear();

                    for (OnSearchResultListener listener : mSearchListeners) {
                        try {
                            listener.onSearchFailed();
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## searchPattern() : onSearchFailed failed " + e.getMessage());
                        }
                    }
                    mSearchListeners.clear();
                    mSearchingPattern = null;
                }
            });
        }
    }

    @Override
    public boolean onRowLongClick(int position) {
        onContentClick(position);
        return true;
    }

    @Override
    public void onContentClick(int position) {
        Event event = mAdapter.getItem(position).getEvent();

        Intent intent = new Intent(getActivity(), VectorRoomActivity.class);
        intent.putExtra(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
        intent.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, event.roomId);
        intent.putExtra(VectorRoomActivity.EXTRA_EVENT_ID, event.eventId);

        getActivity().startActivity(intent);
    }

    /**
     * Called when a long click is performed on the message content
     * @param position the cell position
     * @return true if managed
     */
    @Override
    public boolean onContentLongClick(int position) {
        return false;
    }

    //==============================================================================================================
    // rooms events management : ignore any update on the adapter while searching
    //==============================================================================================================

    @Override
    public void onEvent(final Event event, final EventTimeline.Direction direction, final RoomState roomState) {
    }

    @Override
    public void onLiveEventsChunkProcessed() {
    }

    @Override
    public void onReceiptEvent(List<String> senderIds){
    }
}
