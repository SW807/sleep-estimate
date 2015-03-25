package dk.aau.cs.psylog.analysis.sleepestimate;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class PsyLogService extends Service {

    SleepEstimator estimator;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
        estimator.analysisParameters(intent);
        estimator.startAnalysis();
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        estimator.stopAnalysis();
    }

}
