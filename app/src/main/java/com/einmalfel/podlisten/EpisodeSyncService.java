package com.einmalfel.podlisten;


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;


public class EpisodeSyncService extends Service {
  private static EpisodesSyncAdapter adapter;

  @Override
  public void onCreate() {
    super.onCreate();
    synchronized (getClass()) {
      if (adapter == null) {
        adapter = new EpisodesSyncAdapter(getApplicationContext(), true);
      }
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return adapter.getSyncAdapterBinder();
  }
}