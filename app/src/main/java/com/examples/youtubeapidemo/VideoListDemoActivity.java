/*
 * Copyright 2012 Google Inc. All Rights Reserved.
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
package com.examples.youtubeapidemo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.youtube.player.YouTubeApiServiceUtil;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer.OnFullscreenListener;
import com.google.android.youtube.player.YouTubeStandalonePlayer;
import com.google.android.youtube.player.YouTubeThumbnailLoader;
import com.google.android.youtube.player.YouTubeThumbnailLoader.ErrorReason;
import com.google.android.youtube.player.YouTubeThumbnailView;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.weixiao.widget.InfiniteScrollListAdapter;
import ca.weixiao.widget.InfiniteScrollListView;

/**
 * A sample Activity showing how to manage multiple YouTubeThumbnailViews in an adapter for display
 * in a List. When the list items are clicked, the video is played by using a YouTubePlayerFragment.
 * <p>
 * The demo supports custom fullscreen and transitioning between portrait and landscape without
 * rebuffering.
 */
@TargetApi(30)
public final class VideoListDemoActivity extends Activity implements OnFullscreenListener {

  /** The duration of the animation sliding up the video in portrait. */
  private static final int ANIMATION_DURATION_MILLIS = 300;
  /** The padding between the video list and the video in landscape orientation. */
  private static final int LANDSCAPE_VIDEO_PADDING_DP = 5;

  private static final int LOAD_VIDEOS_BATCH_SIZE = 10;

  /** The request code when calling startActivityForResult to recover from an API service error. */
  private static final int RECOVERY_DIALOG_REQUEST = 1;

  private static final String APPLICATION_NAME = "Short Kripaluji Pravachans";

  private InfiniteScrollListView scrollListView;

  private boolean isFullscreen;
  private PageAdapter pageAdapter;
  private List<VideoEntry> videoList = new ArrayList<VideoEntry>();
  private AsyncTask<Void, Void, List<VideoEntry>> fetchAsyncTask;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.video_list_demo);
    LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    scrollListView = (InfiniteScrollListView) this.findViewById(R.id.infinite_listview_infinitescrolllistview);
    scrollListView.setLoadingMode(InfiniteScrollListView.LoadingMode.SCROLL_TO_BOTTOM);
    scrollListView.setLoadingView(layoutInflater.inflate(R.layout.loading_view, null));

    final VideoListDemoActivity currActivity = this;
    scrollListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
        String videoId = videoList.get(position).videoId;
        Intent intent = null;
        intent = YouTubeStandalonePlayer.createVideoIntent(
                currActivity, BuildConfig.YOUTUBE_API_KEY, videoId,0, true, false);
        if (intent != null) {
          if (canResolveIntent(intent)) {
            startActivityForResult(intent, 1);
          } else {
            // Could not resolve the intent - must need to install or update the YouTube API service.
            YouTubeInitializationResult.SERVICE_MISSING
                    .getErrorDialog(currActivity, 2).show();
          }
        }
      }
    });

    final YouTube youtube = new YouTube.Builder(Auth.HTTP_TRANSPORT, Auth.JSON_FACTORY, new HttpRequestInitializer() {
      @Override
      public void initialize(HttpRequest request) throws IOException {
      }
    }).setApplicationName(APPLICATION_NAME).build();
    final StringBuilder pageToken = new StringBuilder();
    pageAdapter = new PageAdapter(currActivity, videoList, new PageAdapter.NewPageListener(){
      @Override
      public void onScrollNext() {
         fetchAsyncTask = new PlaylistRequest(currActivity, videoList,youtube, pageToken).execute();
      }
    });

    scrollListView.setAdapter(pageAdapter);
    checkYouTubeApi();
  }
  protected void onResume() {
    super.onResume();
    // Load the first page of videos
    pageAdapter.onScrollNext();
  }
  private static final class PlaylistRequest extends AsyncTask<Void, Void, List<VideoEntry>> {
    private final StringBuilder pageToken;
    YouTube.PlaylistItems.List request; PlaylistItemListResponse response;
    YouTube youtube;
    private WeakReference<VideoListDemoActivity> activity;

    public PlaylistRequest(VideoListDemoActivity activity, List<VideoEntry> videoList, YouTube youtube, StringBuilder pageToken){
      super();
      this.youtube = youtube;
      this.activity = new WeakReference<>(activity);
      PlaylistItemListResponse response = null;
      this.youtube = youtube;
      this.pageToken = pageToken;
    }
    @Override
    protected void onPreExecute() {
      // Loading lock to allow only one instance of loading
      activity.get().pageAdapter.lock();
    }
    @Override
    protected List<VideoEntry> doInBackground(Void... v) {
      List<VideoEntry> result = null;
      try {
        int newVideosCount = 0;
        ArrayList<VideoEntry> list = new ArrayList<VideoEntry>();

//        do {
          // Define and execute the API request
          YouTube.PlaylistItems.List requestPlayList = youtube.playlistItems().list("snippet");
          requestPlayList.setKey(BuildConfig.YOUTUBE_API_KEY);
          response = requestPlayList.setMaxResults((long) LOAD_VIDEOS_BATCH_SIZE)
                  .setPlaylistId("PLByq9Ggy7VYe5mkjoh6aeN21EzpHwpj2j").setPageToken(pageToken.toString())
                  .execute();
          //          System.out.println("response count:"+res.getItems().size());
          SharedPreferences sharedPreferences = activity.get().getPreferences(MODE_PRIVATE);
          pageToken.setLength(0);
          String token = response.getNextPageToken();
          pageToken.append(token != null ? token : "");
          String videoIdStr = "";
          for (PlaylistItem item: response.getItems()) {
//            list.add(new VideoEntry(item.getSnippet().getTitle(), item.getSnippet().getResourceId().getVideoId(), "", ""));

            videoIdStr += item.getSnippet().getResourceId().getVideoId() + ",";
          }
          YouTube.Videos.List requestVideoList = youtube.videos()
                  .list("status, contentDetails");
          requestVideoList.setKey(BuildConfig.YOUTUBE_API_KEY);
          VideoListResponse responseVideoList = requestVideoList.setId(videoIdStr).execute();

//          for (Video vid : responseVideoList.getItems()) {
        for (int i = 0; i < responseVideoList.getItems().size(); i++) {
          Video vid = responseVideoList.getItems().get(i);
            if(vid.getStatus().getEmbeddable()) {
              String lastWatched = sharedPreferences.getString(vid.getId(), "");
              if(lastWatched == "") {
                newVideosCount++;
              }
              list.add(new VideoEntry(response.getItems().get(i).getSnippet().getTitle(), vid.getId(), lastWatched, vid.getContentDetails().getDuration().replace("PT", "").replace('M', 'm').replace('S', 's')));
            }
          }
//        } while(newVideosCount < LOAD_VIDEOS_BATCH_SIZE);
//        Collections.shuffle(list);
//        Collections.sort(list, new VideoListFragment.SortPlaylist());
        result = Collections.unmodifiableList(list);
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }
      return result;
    }

    @Override
    protected void onPostExecute(List<VideoEntry> result) {
      PageAdapter adapter = activity.get().pageAdapter;
        if (isCancelled() || result == null || result.isEmpty()) {
          activity.get().pageAdapter.notifyEndOfList();
        } else {
          // Add data to the placeholder
            adapter.addEntriesToBottom(result);

          // Add or remove the loading view depend on if there might be more to load
          if (pageToken.toString() == "") {
            adapter.notifyEndOfList();
          } else {
            adapter.notifyHasMore();
          }
          // Get the focus to the specified position when loading completes
//          activity.get().scrollListView.smoothScrollToPosition((result.size() < LOAD_VIDEOS_BATCH_SIZE ? adapter.getCount() : adapter.getCount() - 2));
        }
    }
    @Override
    protected void onCancelled() {
      // Tell the adapter it is end of the list when task is cancelled
      activity.get().pageAdapter.notifyEndOfList();
    }
  }

  private boolean canResolveIntent(Intent intent) {
    List<ResolveInfo> resolveInfo = getPackageManager().queryIntentActivities(intent, 0);
    return resolveInfo != null && !resolveInfo.isEmpty();
  }

  private void checkYouTubeApi() {
    YouTubeInitializationResult errorReason =
            YouTubeApiServiceUtil.isYouTubeApiServiceAvailable(this);
    if (errorReason.isUserRecoverableError()) {
      errorReason.getErrorDialog(this, RECOVERY_DIALOG_REQUEST).show();
    } else if (errorReason != YouTubeInitializationResult.SUCCESS) {
      String errorMessage =
              String.format(getString(R.string.error_player), errorReason.toString());
      Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == RECOVERY_DIALOG_REQUEST) {
      // Recreate the activity if user performed a recovery action
      recreate();
    }
  }

  @Override
  public void onFullscreen(boolean isFullscreen) {
    this.isFullscreen = isFullscreen;
  }


  @TargetApi(30)
  private void runOnAnimationEnd(ViewPropertyAnimator animator, final Runnable runnable) {
    if (Build.VERSION.SDK_INT >= 16) {
      animator.withEndAction(runnable);
    } else {
      animator.setListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          runnable.run();
        }
      });
    }
  }

  /**
   * Adapter for the video list. Manages a set of YouTubeThumbnailViews, including initializing each
   * of them only once and keeping track of the loader of each one. When the ListFragment gets
   * destroyed it releases all the loaders.
   */
  private static final class PageAdapter extends InfiniteScrollListAdapter {

    private List<VideoEntry> entries = new ArrayList<VideoEntry>();
    private final List<View> entryViews;
    private final Map<YouTubeThumbnailView, YouTubeThumbnailLoader> thumbnailViewToLoaderMap;
    private final ThumbnailListener thumbnailListener;
    private final VideoListDemoActivity activity;
    private boolean labelsVisible;
    private NewPageListener newPageListener;

    public void addEntriesToBottom(List<VideoEntry> newEntries) {
      // Add entries to the bottom of the list
      this.entries.addAll(newEntries);
      notifyDataSetChanged();
    }
    // A demo listener to pass actions from view to adapter
    public static abstract class NewPageListener {
      public abstract void onScrollNext();
//      public abstract View getInfiniteScrollListView(int position, View convertView, ViewGroup parent);
    }

    public PageAdapter(VideoListDemoActivity activity, List<VideoEntry> entries, NewPageListener newPageListener) {
      this.entries = entries;
      this.activity = activity;
      this.newPageListener = newPageListener;
      entryViews = new ArrayList<View>();
      thumbnailViewToLoaderMap = new HashMap<YouTubeThumbnailView, YouTubeThumbnailLoader>();
      thumbnailListener = new ThumbnailListener();

      labelsVisible = true;
    }

    public void releaseLoaders() {
      for (YouTubeThumbnailLoader loader : thumbnailViewToLoaderMap.values()) {
        loader.release();
      }
    }

    public void setLabelVisibility(boolean visible) {
      labelsVisible = visible;
      for (View view : entryViews) {
        view.findViewById(R.id.text).setVisibility(visible ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.last_watched).setVisibility(visible ? View.VISIBLE : View.GONE);
      }
    }

    @Override
    public int getCount() {
      return entries.size();
    }

    @Override
    public VideoEntry getItem(int position) {
      return entries.get(position);
    }

    @Override
    public long getItemId(int position) {
      return 0;
    }

    @Override
    protected void onScrollNext() {
      if (newPageListener != null) {
        newPageListener.onScrollNext();
      }
    }

    @Override
    public View getInfiniteScrollListView(int position, View convertView, ViewGroup parent) {

      View view = convertView;
      VideoEntry entry = entries.get(position);

      // There are three cases here
      if (view == null) {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        // 1) The view has not yet been created - we need to initialize the YouTubeThumbnailView.
        view = inflater.inflate(R.layout.video_list_item, parent, false);
        YouTubeThumbnailView thumbnail = (YouTubeThumbnailView) view.findViewById(R.id.thumbnail);
        thumbnail.setTag(entry.videoId);
        thumbnail.initialize(BuildConfig.YOUTUBE_API_KEY, thumbnailListener);
      } else {
        YouTubeThumbnailView thumbnail = (YouTubeThumbnailView) view.findViewById(R.id.thumbnail);
        YouTubeThumbnailLoader loader = thumbnailViewToLoaderMap.get(thumbnail);
        if (loader == null) {
          // 2) The view is already created, and is currently being initialized. We store the
          //    current videoId in the tag.
          thumbnail.setTag(entry.videoId);
        } else {
          // 3) The view is already created and already initialized. Simply set the right videoId
          //    on the loader.
          thumbnail.setImageResource(R.drawable.loading_thumbnail);
//          loader.setVideo(entry.videoId);
//          Picasso.get()
//                  .load(getYoutubeThumbnailUrlFromVideoUrl(entry.videoId))
//                  .into(thumbnail);
//          thumbnail.setScaleType(ImageView.ScaleType.FIT_XY);
        }
      }
      YouTubeThumbnailView thumbnail = (YouTubeThumbnailView) view.findViewById(R.id.thumbnail);

      ImageView thumbnailImage = (ImageView)  view.findViewById(R.id.thumbnail_image);
      Picasso.get().load(getYoutubeThumbnailUrlFromVideoUrl(entry.videoId)).into(thumbnailImage);
      TextView label = ((TextView) view.findViewById(R.id.text));
      TextView lastWatched = ((TextView) view.findViewById(R.id.last_watched));
      TextView duration = ((TextView) view.findViewById(R.id.duration));
      label.setText(entry.text);
      lastWatched.setText(entry.lastWatched);
      duration.setText(entry.duration);
      label.setVisibility(labelsVisible ? View.VISIBLE : View.GONE);
      lastWatched.setVisibility(labelsVisible ? View.VISIBLE : View.GONE);
      duration.setVisibility(labelsVisible ? View.VISIBLE : View.GONE);
      return view;
    }

    public static String getYoutubeThumbnailUrlFromVideoUrl(String videoId) {
      return "http://img.youtube.com/vi/"+ videoId + "/mqdefault.jpg";
    }

    private final class ThumbnailListener implements
            YouTubeThumbnailView.OnInitializedListener,
            YouTubeThumbnailLoader.OnThumbnailLoadedListener {

      @Override
      public void onInitializationSuccess(
              YouTubeThumbnailView view, YouTubeThumbnailLoader loader) {
        loader.setOnThumbnailLoadedListener(this);
        thumbnailViewToLoaderMap.put(view, loader);
        view.setImageResource(R.drawable.loading_thumbnail);
        String videoId = (String) view.getTag();
//        loader.setVideo(videoId);
//        view.setScaleType(ImageView.ScaleType.FIT_XY);
      }

      @Override
      public void onInitializationFailure(
              YouTubeThumbnailView view, YouTubeInitializationResult loader) {
        view.setImageResource(R.drawable.no_thumbnail);
      }

      @Override
      public void onThumbnailLoaded(YouTubeThumbnailView view, String videoId) {
      }

      @Override
      public void onThumbnailError(YouTubeThumbnailView view, ErrorReason errorReason) {
        view.setImageResource(R.drawable.no_thumbnail);
      }
    }

  }

  private static final class VideoEntry {
    private final String text;
    private final String videoId;
    private final String lastWatched;
    private final String duration;

    public VideoEntry(String text, String videoId, String lastWatched, String duration) {
      this.text = text;
      this.videoId = videoId;
      this.lastWatched = lastWatched;
      this.duration = duration;
    }
  }
}
