package dk.aau.cs.psylog.analysis.sleepestimate;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class PsyLogService extends IntentService {

    SleepEstimator estimator;

    public PsyLogService() {
        super("sleepEstimator");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        estimator.analyse();
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        estimator = new SleepEstimator(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flag, int startid)
    {
        super.onStartCommand(intent, flag, startid);
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

}
