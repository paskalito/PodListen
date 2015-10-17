package com.einmalfel.podlisten;


import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.einmalfel.podlisten.support.PredictiveAnimatiedLayoutManager;


public class PlaylistFragment extends DebuggableFragment implements
    LoaderManager.LoaderCallbacks<Cursor>, EpisodeListAdapter.ItemClickListener,
    PlayerService.PlayerStateListener {
  private MainActivity activity;
  private static final String TAG = "PLF";
  private static final MainActivity.Pages activityPage = MainActivity.Pages.PLAYLIST;
  private final EpisodeListAdapter adapter = new EpisodeListAdapter(null, this);
  private PlayerLocalConnection conn;
  private RecyclerView rv;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    conn = new PlayerLocalConnection(this);
  }

  @Override
  public void onDestroy() {
    adapter.swapCursor(null);
    super.onDestroy();
  }

  @Override
  public void onPause() {
    super.onPause();
    conn.unbind();
  }

  @Override
  public void onResume() {
    super.onResume();
    conn.bind();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
      savedInstanceState) {
    View layout = inflater.inflate(R.layout.common_list, container, false);
    rv = (RecyclerView) layout.findViewById(R.id.recycler_view);
    activity = (MainActivity) getActivity();
    rv.setLayoutManager(new PredictiveAnimatiedLayoutManager(activity));
    rv.setItemAnimator(new DefaultItemAnimator());
    activity.getSupportLoaderManager().initLoader(activityPage.ordinal(), null, this);
    rv.setAdapter(adapter);

    return layout;
  }

  @Override
  public boolean onLongTap(long id) {
    PodcastHelper.deleteEpisodeDialog(id, activity);
    return true;
  }

  @Override
  public void onButtonTap(long id) {
    // episode button in playlist is enabled in two cases:
    // - episode is downloaded
    // - episode isn't downloaded, isn't being download (downloadId == 0)
    Cursor cursor = activity.getContentResolver().query(
        Provider.getUri(Provider.T_EPISODE, id),
        new String[]{Provider.K_EAURL, Provider.K_ENAME, Provider.K_EDID, Provider.K_EDFIN},
        null, null, null);
    if (cursor != null && cursor.moveToFirst()) {
      long dId = cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_EDID));
      int downloaded = cursor.getInt(cursor.getColumnIndexOrThrow(Provider.K_EDFIN));
      String aURL = cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_EAURL));
      String title = cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_ENAME));
      cursor.close();
      if (dId == 0 && downloaded != 100) {
        Intent bi = new Intent(DownloadReceiver.DOWNLOAD_EPISODE_ACTION);
        bi.putExtra(DownloadReceiver.URL_EXTRA_NAME, aURL);
        bi.putExtra(DownloadReceiver.TITLE_EXTRA_NAME, title);
        bi.putExtra(DownloadReceiver.ID_EXTRA_NAME, id);
        PodListenApp.getContext().sendBroadcast(bi);
        return;
      }
    } else {
      Log.e(TAG, "Episode " + id + " is absent in DB");
      return;
    }

    if (conn.service != null) {
      if (id == conn.service.getEpisodeId()) {
        conn.service.playPauseResume();
      } else {
        conn.service.playEpisode(id);
      }
    }
  }

  public void reloadList() {
    activity.getSupportLoaderManager().restartLoader(
        MainActivity.Pages.PLAYLIST.ordinal(), null, this);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(activity,
                            Provider.episodeJoinPodcastUri,
                            EpisodeListAdapter.REQUIRED_DB_COLUMNS,
                            Provider.K_ESTATE + " = ?",
                            new String[]{Integer.toString(Provider.ESTATE_IN_PLAYLIST)},
                            Preferences.getInstance().getSortingMode().toSql());
  }

  @Override
  public void onLoadFinished(Loader loader, Cursor data) {
    adapter.swapCursor(data);
  }

  @Override
  public void onLoaderReset(Loader loader) {
    adapter.swapCursor(null);
  }

  @Override
  public void progressUpdate(int position, int max) {}

  @Override
  public void stateUpdate(PlayerService.State state) {
    adapter.setCurrentIdState(conn.service.getEpisodeId(), state);
  }

  @Override
  public void episodeUpdate(long id) {
    adapter.setCurrentIdState(id, conn.service.getState());
  }

  void showEpisode(long id, boolean smoothScroll) {
    for (int pos = 0; pos < adapter.getItemCount(); pos++) {
      if (adapter.getItemId(pos) == id) {
        Log.d(TAG, "scrolling to " + pos + " id " + id);
        if (smoothScroll) {
          rv.smoothScrollToPosition(pos);
        } else {
          rv.scrollToPosition(pos);
        }
        adapter.setExpanded(id, true, pos);
        return;
      }
    }
  }
}
