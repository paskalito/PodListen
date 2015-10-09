package com.einmalfel.podlisten;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/** This class is intended to encapsulate preferences names and default values */
public class Preferences implements SharedPreferences.OnSharedPreferenceChangeListener {
  enum Key {
    STORAGE_PATH,
    MAX_DOWNLOADS,
  }

  enum MaxDownloadsOption {
    ONE, TWO, THREE, FOUR, FIVE, TEN, UNLIMITED;

    public int toInt() {
      switch (this) {
        case TEN:
          return 10;
        case UNLIMITED:
          return Integer.MAX_VALUE;
        default:
          return ordinal() + 1;
      }
    }

    @Override
    public String toString() {
      if (this == UNLIMITED) {
        return PodListenApp.getContext().getString(R.string.preferences_max_downloads_unlimited);
      } else {
        return Integer.toString(toInt());
      }
    }
  }

  private static final String TAG = "PRF";
  private static final MaxDownloadsOption DEFAULT_MAX_DOWNLOADS = MaxDownloadsOption.TWO;
  private static Preferences instance = null;

  // fields below could be changed from readPreference() only
  private MaxDownloadsOption maxDownloads;
  private Storage storage;

  public static Preferences getInstance() {
    if (instance == null) {
      synchronized (Preferences.class) {
        if (instance == null) {
          instance = new Preferences();
        }
      }
    }
    return instance;
  }

  public Preferences() {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PodListenApp.getContext());
    sp.registerOnSharedPreferenceChangeListener(this);
    for (Key key : Key.values()) {
      readPreference(sp, key);
    }
  }

  /**
   * When there is some downloaded episodes on current storage and user asks to switch storage
   * - stop all running downloads
   * - stop playback if not streaming (TODO)
   * - reset download progress and download ID fields
   * - remove old files
   * - ask download manager to start downloads for all non-gone episodes
   * - request sync to re-download images
   */
  private void clearStorage() {
    Context context = PodListenApp.getContext();
    Cursor cursor = context.getContentResolver().query(Provider.episodeUri,
                                                       new String[]{Provider.K_EDID},
                                                       Provider.K_EDID + " != ?",
                                                       new String[]{"0"},
                                                       null);
    if (cursor == null) {
      throw new AssertionError("Got null cursor from podlisten provider");
    }
    DownloadManager dM = (DownloadManager) context.getSystemService(
        Context.DOWNLOAD_SERVICE);
    int downloadIdIndex = cursor.getColumnIndexOrThrow(Provider.K_EDID);
    while (cursor.moveToNext()) {
      dM.remove(cursor.getLong(downloadIdIndex));
    }
    cursor.close();

    ContentValues cv = new ContentValues(2);
    cv.put(Provider.K_EDID, 0);
    cv.put(Provider.K_EDFIN, 0);
    context.getContentResolver().update(Provider.episodeUri, cv, null, null);

    for (File file : storage.getPodcastDir().listFiles()) {
      if (!file.delete()) {
        Log.e(TAG, "Failed to delete " + file.getAbsolutePath());
      }
    }
    for (File file : storage.getImagesDir().listFiles()) {
      if (!file.delete()) {
        Log.e(TAG, "Failed to delete " + file.getAbsolutePath());
      }
    }

    context.sendBroadcast(new Intent(DownloadStartReceiver.UPDATE_QUEUE_ACTION));
    PodlistenAccount.getInstance().refresh(0);
  }

  private synchronized void readPreference(SharedPreferences sPrefs, Key key) {
    switch (key) {
      case MAX_DOWNLOADS:
        try {
          int maxDownloadsOrdinal = Integer.valueOf(sPrefs.getString(
              Key.MAX_DOWNLOADS.toString(), "-1"));
          if (maxDownloadsOrdinal == -1) {
            sPrefs.edit().putString(Key.MAX_DOWNLOADS.toString(),
                                    Integer.toString(DEFAULT_MAX_DOWNLOADS.ordinal())).commit();
          } else {
            maxDownloads = MaxDownloadsOption.values()[maxDownloadsOrdinal];
          }
        } catch (NumberFormatException exception) {
          Log.e(TAG, "Failed to parse max downloads preference, value remains " + maxDownloads);
        }
        break;
      case STORAGE_PATH:
        String storagePreferenceString = sPrefs.getString(Key.STORAGE_PATH.toString(), "");
        if (storagePreferenceString.isEmpty()) {
          // by default, if there are removable storages use first removable, otherwise use last one
          for (Storage storageOption : Storage.getAvailableStorages()) {
            storage = storageOption;
            if (storage.isRemovable()) {
              break;
            }
          }
          if (storage != null) {
            sPrefs.edit().putString(Key.STORAGE_PATH.toString(), storage.toString()).commit();
          }
        } else {
          try {
            Storage newStorage = new Storage(new File(storagePreferenceString));
            newStorage.createSubdirs();
            if (storage != null && !storage.equals(newStorage)) {
              clearStorage();
            }
            storage = newStorage;
          } catch (IOException e) {
            Log.wtf(
                TAG, "Failed to set storage " + storagePreferenceString + ". Reverting to prev", e);
            sPrefs.edit().putString(
                Key.STORAGE_PATH.toString(), storage == null ? "" : storage.toString()).commit();
          }
        }
        break;
    }
  }

  @NonNull
  public MaxDownloadsOption getMaxDownloads() {
    return maxDownloads;
  }

  @Nullable
  public Storage getStorage() {
    return storage;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    Log.i(TAG, "Preference changed " + key + ", values: " + sharedPreferences.getAll().toString());
    readPreference(sharedPreferences, Key.valueOf(key));
  }
}