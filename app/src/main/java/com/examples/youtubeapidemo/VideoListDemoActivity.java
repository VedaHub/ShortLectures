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
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewPropertyAnimator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.youtube.player.YouTubeApiServiceUtil;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayer.OnFullscreenListener;
import com.google.android.youtube.player.YouTubePlayer.OnInitializedListener;
import com.google.android.youtube.player.YouTubePlayer.Provider;
import com.google.android.youtube.player.YouTubePlayerFragment;
import com.google.android.youtube.player.YouTubeStandalonePlayer;
import com.google.android.youtube.player.YouTubeThumbnailLoader;
import com.google.android.youtube.player.YouTubeThumbnailLoader.ErrorReason;
import com.google.android.youtube.player.YouTubeThumbnailView;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

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

  /** The request code when calling startActivityForResult to recover from an API service error. */
  private static final int RECOVERY_DIALOG_REQUEST = 1;

  private VideoListFragment listFragment;

  private boolean isFullscreen;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.video_list_demo);

    listFragment = (VideoListFragment) getFragmentManager().findFragmentById(R.id.list_fragment);
  //TODO: add listener for standalone player
    checkYouTubeApi();
  }

  static void performLayout() {

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

  /**
   * Sets up the layout programatically for the three different states. Portrait, landscape or
   * fullscreen+landscape. This has to be done programmatically because we handle the orientation
   * changes ourselves in order to get fluent fullscreen transitions, so the xml layout resources
   * do not get reloaded.
   */
//  private void layout() {
//    boolean isPortrait =
//            getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
//    System.out.println("object: " + this);
//
//    listFragment.getView().setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
//    listFragment.setLabelVisibility(isPortrait);
////    closeButton.setVisibility(isPortrait ? View.VISIBLE : View.GONE);
//
////    if (isFullscreen) {
////      videoBox.setTranslationY(0); // Reset any translation that was applied in portrait.
////      setLayoutSize(videoFragment.getView(), MATCH_PARENT, MATCH_PARENT);
////      setLayoutSizeAndGravity(videoBox, MATCH_PARENT, MATCH_PARENT, Gravity.TOP | Gravity.LEFT);
////    } else if (isPortrait) {
//      setLayoutSize(listFragment.getView(), MATCH_PARENT, MATCH_PARENT);
//      setLayoutSize(videoFragment.getView(), MATCH_PARENT, WRAP_CONTENT);
//      setLayoutSizeAndGravity(videoBox, MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM);
////    } else {
////      videoBox.setTranslationY(0); // Reset any translation that was applied in portrait.
////      int screenWidth = dpToPx(getResources().getConfiguration().screenWidthDp);
////      setLayoutSize(listFragment.getView(), screenWidth / 4, MATCH_PARENT);
////      int videoWidth = screenWidth - screenWidth / 4 - dpToPx(LANDSCAPE_VIDEO_PADDING_DP);
////      setLayoutSize(videoFragment.getView(), videoWidth, WRAP_CONTENT);
////      setLayoutSizeAndGravity(videoBox, videoWidth, WRAP_CONTENT,
////              Gravity.RIGHT | Gravity.CENTER_VERTICAL);
////    }
//  }


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
   * A fragment that shows a static list of videos.
   */
  public static final class VideoListFragment extends ListFragment {

    private List<VideoEntry> videoList = null;
    private static final String CLIENT_SECRETS= "client_secret.json";
    private static final Collection<String> SCOPES =
            Arrays.asList("https://www.googleapis.com/auth/youtube.readonly");

    private static final String APPLICATION_NAME = "Short Kripaluji Pravachans";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    /**
     * Create an authorized Credential object.
     *
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize(final NetHttpTransport httpTransport) throws IOException {
      // Load client secrets.
      InputStream in = VideoListFragment.class.getResourceAsStream(CLIENT_SECRETS);
      GoogleClientSecrets clientSecrets =
              GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
      // Build flow and trigger user authorization request.
      GoogleAuthorizationCodeFlow flow =
              new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                      .build();
      Credential credential =
              new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
      return credential;
    }


    private PageAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState)  {
      super.onCreate(savedInstanceState);
      PlaylistItemListResponse response = null;
      try {
        YouTube youtube = new YouTube.Builder(Auth.HTTP_TRANSPORT, Auth.JSON_FACTORY, new HttpRequestInitializer() {
          @Override
          public void initialize(HttpRequest request) throws IOException {
          }
        }).setApplicationName(APPLICATION_NAME).build();

        // Define and execute the API request
        YouTube.PlaylistItems.List request = youtube.playlistItems().list("snippet");
        request.setKey(BuildConfig.YOUTUBE_API_KEY);
        AsyncTask<?, ?, ?> runningTask = new PlaylistRequest(request, response, this, videoList, youtube).execute();

//        response = request.setMaxResults(25L)
//                .setPlaylistId("PLByq9Ggy7VYe5mkjoh6aeN21EzpHwpj2j")
//                .execute();
      } catch (Exception ex){
        ex.printStackTrace();

      }

    }
    class SortPlaylist implements Comparator<VideoEntry>
    {

      @Override
      public int compare(VideoEntry t1, VideoEntry t2) {
        return t1.lastWatched.compareTo(t2.lastWatched);
      }
    }
    private final class PlaylistRequest extends AsyncTask<Void, Void, String> {
      YouTube.PlaylistItems.List req; PlaylistItemListResponse res;
      VideoListFragment vListFrag; List<VideoEntry> vList;
      YouTube youtube;
      public PlaylistRequest(YouTube.PlaylistItems.List request, PlaylistItemListResponse response, VideoListFragment vf, List<VideoEntry> videoList, YouTube youtube ){
        super();
        req = request;
        res = response;
        vListFrag = vf;
        vList = videoList;
        this.youtube = youtube;
      }
      @Override
      protected String doInBackground(Void... v) {
        try {
          String pageToken = "";
          int newVideosCount = 0;
          ArrayList<VideoEntry> list = new ArrayList<VideoEntry>();

          do {
            res = req.setMaxResults(49L)
                    .setPlaylistId("PLByq9Ggy7VYe5mkjoh6aeN21EzpHwpj2j").setPageToken(pageToken)
                    .execute();
            //          System.out.println("response count:"+res.getItems().size());
            Activity activity = getActivity();
            SharedPreferences sharedPreferences = activity.getPreferences(MODE_PRIVATE);
            pageToken = res.getNextPageToken();
            String videoIdStr = "";
            for (PlaylistItem item: res.getItems()) {
                videoIdStr += item.getSnippet().getResourceId().getVideoId() + ",";
            }
              YouTube.Videos.List request = youtube.videos()
                      .list("status, snippet, contentDetails");
              request.setKey(BuildConfig.YOUTUBE_API_KEY);
              VideoListResponse response = request.setId(videoIdStr).execute();

              for (Video vid : response.getItems()) {
                if(vid.getStatus().getEmbeddable()) {
                  String lastWatched = sharedPreferences.getString(vid.getId(), "");
                  if(lastWatched == "") {
                    newVideosCount++;
                  }
                  list.add(new VideoEntry(vid.getSnippet().getTitle(), vid.getId(), lastWatched, vid.getContentDetails().getDuration().replace("PT", "").replace('M', 'm').replace('S', 's')));
                }
              }
          } while(newVideosCount < 50);
          Collections.shuffle(list);
          Collections.sort(list, new SortPlaylist());
          vListFrag.videoList = Collections.unmodifiableList(list);
          vListFrag.adapter = new PageAdapter(vListFrag.getActivity(), vListFrag.videoList);
        }
        catch (Exception ex) {
          ex.printStackTrace();
        }
        return "Success";
      }

      @Override
      protected void onPostExecute(String result) {
//        ctxt.findViewById(R.id.list_fragment).invalidate();
//        vListFrag.getActivity().findViewById(R.id.video_box).invalidate();
//        vListFrag.getActivity().findViewById(R.id.list_fragment).invalidate();
        System.out.println("onPostExecute");
        setListAdapter(adapter);
//        vListFrag.getActivity().
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
      super.onActivityCreated(savedInstanceState);

      getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);

    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {

      String videoId = videoList.get(position).videoId;
      Intent intent = null;
      intent = YouTubeStandalonePlayer.createVideoIntent(
               getActivity(), BuildConfig.YOUTUBE_API_KEY, videoId,0, true, false);
      if (intent != null) {
        if (canResolveIntent(intent)) {
          startActivityForResult(intent, 1);
        } else {
          // Could not resolve the intent - must need to install or update the YouTube API service.
          YouTubeInitializationResult.SERVICE_MISSING
                  .getErrorDialog(getActivity(), 2).show();
        }
      }
    }
    private boolean canResolveIntent(Intent intent) {
      List<ResolveInfo> resolveInfo = getActivity().getPackageManager().queryIntentActivities(intent, 0);
      return resolveInfo != null && !resolveInfo.isEmpty();
    }
    @Override
    public void onDestroyView() {
      super.onDestroyView();

      if(adapter != null)
        adapter.releaseLoaders();
    }

    public void setLabelVisibility(boolean visible) {
      if(adapter != null)
        adapter.setLabelVisibility(visible);
    }

  }
  /**
   * Adapter for the video list. Manages a set of YouTubeThumbnailViews, including initializing each
   * of them only once and keeping track of the loader of each one. When the ListFragment gets
   * destroyed it releases all the loaders.
   */
  private static final class PageAdapter extends BaseAdapter {

    private final List<VideoEntry> entries;
    private final List<View> entryViews;
    private final Map<YouTubeThumbnailView, YouTubeThumbnailLoader> thumbnailViewToLoaderMap;
    private final LayoutInflater inflater;
    private final ThumbnailListener thumbnailListener;

    private boolean labelsVisible;

    public PageAdapter(Context context, List<VideoEntry> entries) {
      this.entries = entries;

      entryViews = new ArrayList<View>();
      thumbnailViewToLoaderMap = new HashMap<YouTubeThumbnailView, YouTubeThumbnailLoader>();
      inflater = LayoutInflater.from(context);
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
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      VideoEntry entry = entries.get(position);

      // There are three cases here
      if (view == null) {
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

//  public static final class VideoFragment extends YouTubePlayerFragment
//          implements OnInitializedListener {
//
//    private YouTubePlayer player;
//    private String videoId;
//
//    public static VideoFragment newInstance() {
//      return new VideoFragment();
//    }
//    private final YouTubePlayer.PlaybackEventListener mPlaybackEventListener = new YouTubePlayer.PlaybackEventListener() {
//      @Override
//      public void onPlaying() {
//        SharedPreferences prefs = getActivity().getPreferences(MODE_PRIVATE);
//        Date dNow = new Date( );
//        SimpleDateFormat ft =
//                new SimpleDateFormat ("yyyy-MM-dd");
//        prefs.edit().putString(videoId, ft.format(dNow)).commit();
//      }
//
//      @Override
//      public void onPaused() {
//
//      }
//
//      @Override
//      public void onStopped() {
//
//      }
//
//      @Override
//      public void onBuffering(boolean b) {
//
//      }
//
//      @Override
//      public void onSeekTo(int i) {
//
//      }
//    };
//
//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//      super.onCreate(savedInstanceState);
//      initialize(BuildConfig.YOUTUBE_API_KEY, this);
//    }
//
//    @Override
//    public void onDestroy() {
//      if (player != null) {
//        player.release();
//      }
//      super.onDestroy();
//    }
//
//    public void setVideoId(String videoId) {
//      if (videoId != null && !videoId.equals(this.videoId)) {
//        this.videoId = videoId;
//        if (player != null) {
//          player.cueVideo(videoId);
//        }
//      }
//    }
//
//    public void pause() {
//      if (player != null) {
//        player.pause();
//      }
//    }
//
//    @Override
//    public void onInitializationSuccess(Provider provider, YouTubePlayer player, boolean restored) {
//      this.player = player;
//      player.addFullscreenControlFlag(YouTubePlayer.FULLSCREEN_FLAG_CUSTOM_LAYOUT);
//      player.setOnFullscreenListener((VideoListDemoActivity) getActivity());
//      if (!restored && videoId != null) {
//        player.cueVideo(videoId);
//      }
//      player.setPlaybackEventListener(mPlaybackEventListener);
//    }
//
//    @Override
//    public void onInitializationFailure(Provider provider, YouTubeInitializationResult result) {
//      this.player = null;
//    }
//
//  }

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

  // Utility methods for layouting.

//  private int dpToPx(int dp) {
//    return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
//  }
//
//  private static void setLayoutSize(View view, int width, int height) {
//    LayoutParams params = view.getLayoutParams();
//    params.width = width;
//    params.height = height;
//    view.setLayoutParams(params);
//  }
//
//  private static void setLayoutSizeAndGravity(View view, int width, int height, int gravity) {
//    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
//    params.width = width;
//    params.height = height;
//    params.gravity = gravity;
//    view.setLayoutParams(params);
//  }

}
