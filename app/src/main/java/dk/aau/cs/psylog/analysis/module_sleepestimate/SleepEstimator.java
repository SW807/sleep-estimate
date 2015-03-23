package dk.aau.cs.psylog.analysis.module_sleepestimate;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import dk.aau.cs.psylog.module_lib.DBAccessContract;

public class SleepEstimator {

    private ContentResolver resolver;
    private Uri read;
    private Uri write;

    private Timer timer;
    private TimerTask timerTask;
    private long delay;
    private long period;

    public SleepEstimator(Context context)
    {
        resolver = context.getApplicationContext().getContentResolver();
        read = Uri.parse(DBAccessContract.DBACCESS_CONTENTPROVIDER + "accelerometer_accelerations");
        write = Uri.parse(DBAccessContract.DBACCESS_CONTENTPROVIDER + "sleepestimate_sleepEstimates");

        timerTask = new TimerTask() {
            @Override
            public void run() {
                String[] projection = {"accX", "accY", "accZ", "time"};
                Cursor cursor = resolver.query(read, projection, null, null, null);

                List<Estimations> evaluation = extract(cursor);

                List<Estimations> tostore = compress(evaluation);

                ContentValues contentValues = new ContentValues();
                for (Estimations e: tostore)
                {
                    contentValues.put("estimation", e.prediction);
                    contentValues.put("startTime", e.startTime);
                    contentValues.put("endTime", e.endTime);
                    resolver.insert(write, contentValues);
                }

            }
        };

    }

    public List<Estimations> extract(Cursor cursor)
    {
        List<AccelData> content = new ArrayList<>();
        if (cursor.moveToFirst())
        {
            do {
                AccelData toAdd = new AccelData(cursor.getFloat(0), cursor.getFloat(1), cursor.getFloat(2), cursor.getString(3));
                content.add(toAdd);
            }while (cursor.moveToNext());
        }
        if (!content.isEmpty()){
            return estimate(normalizer(content));
        }
        return null;
    }

    //metode til at normalisere skal implementeres
    public List<NormalizedWithTime> normalizer(List<AccelData> input){
        List<NormalizedWithTime> normalized = new ArrayList<>();
        List<AccelData> diffTable = new ArrayList<>();

        float diffXup = 0;
        float diffXbot = 100;
        float diffYup = 0;
        float diffYbot = 100;
        float diffZup = 0;
        float diffZbot = 100;

        int i = 5;
        while (i < input.size())
        {
            float diffX = findDiff5(input.get(i).x, input.get(i - 1).x, input.get(i - 2).x, input.get(i - 3).x, input.get(i - 4).x, input.get(i - 5).x);
            float diffY = findDiff5(input.get(i).y, input.get(i - 1).y, input.get(i - 2).y, input.get(i - 3).y, input.get(i - 4).y, input.get(i - 5).y);
            float diffZ = findDiff5(input.get(i).z, input.get(i - 1).z, input.get(i - 2).z, input.get(i - 3).z, input.get(i - 4).z, input.get(i - 5).z);

            if (diffX > diffXup)
                diffXup = diffX;
            if (diffX < diffXbot)
                diffXbot = diffX;
            if (diffY > diffYup)
                diffYup = diffY;
            if (diffY < diffYbot)
                diffYbot = diffY;
            if (diffZ > diffZup)
                diffZup = diffZ;
            if (diffZ < diffZbot)
                diffZbot = diffZ;

            diffTable.add(new AccelData(diffX, diffY, diffZ, input.get(i).time));
            i++;
        }
        float varX = diffXup - diffXbot;
        float varY = diffYup - diffYbot;
        float varZ = diffZup - diffZbot;

        for (AccelData a:diffTable)
        {
            float x,y,z, intense;
            x = findIntense(a.x, varX);
            y = findIntense(a.y, varY);
            z = findIntense(a.z, varZ);
            intense = (x + y + z) / 3;

            normalized.add(new NormalizedWithTime(intense, a.time));

        }
        return normalized;
    }

    public float findDiff5(float curr, float x1, float x2, float x3, float x4, float x5)
    {
        float diff1, diff2, diff3, diff4, diff5;
        diff1 = Math.abs(curr-x1);
        diff2 = Math.abs(curr-x2);
        diff3 = Math.abs(curr-x3);
        diff4 = Math.abs(curr-x4);
        diff5 = Math.abs(curr-x5);

        return (diff1+diff2+diff3+diff4+diff5)/5;
    }

    public float findIntense(float currDiff, float varDiff)
    {
        if (currDiff != 0)
            return currDiff / varDiff;
        else return 0;
    }

    public List<Estimations> estimate(List<NormalizedWithTime> input){
        List<Estimations> state = new ArrayList<>();
        int count = 1;
        int len = input.size();
        int num = getFourMinWindown(input);
        int j = 1;
        int i = 1;
        int k = 1;
        String startTime, endTime;
        int pred;
        while (j <= len - num)
        {
            startTime = input.get(j).time;
            endTime = input.get(j+num).time;
            float thres = threshhold(input);
            while (i <= num)
            {
                if (input.get(i+j).intensity < thres)
                    count ++;
                i++;
            }
            if (count < 0.4 * num)
                pred = 2;
            else pred = 1;

            state.add(k, new Estimations(pred, startTime, endTime));

            count = 1;
            i = 1;
            k ++;
            j = j + num;

        }

        return state;

    }

    public float stdDeviation(List<NormalizedWithTime> input){
        float lowest = (float) Double.MAX_VALUE;
        float highest= (float) Double.MIN_VALUE;

        for (NormalizedWithTime n:input)
        {
            if (n.intensity > highest)
                highest = n.intensity;
            if (n.intensity < lowest)
                lowest = n.intensity;
        }

        return highest - lowest;
    }


    public float mean(List<NormalizedWithTime> input){
        float sum = 0;

        for (NormalizedWithTime n: input)
        {
            sum += n.intensity;
        }

        return sum / input.size();
    }

    public float threshhold(List<NormalizedWithTime> input)
    {
        float mean = mean(input);
        float std = stdDeviation(input);

        return (mean + std)/2;
    }

    //skal bruge en måde at extract time på
    public int getFourMinWindown (List<NormalizedWithTime> input)
    {
        int i = 1;
        Date startTime = convertTimeString(input.get(0).time);




        Boolean condition = true;
        while (condition)
        {
            Date startTime2 = convertTimeString(input.get(i).time);
            if (startTime.getTime()+240000 < startTime2.getTime())
                condition = false;
            else i++;
        }
        return i;
    }

    public List<Estimations> compress (List<Estimations> input)
    {
        List<Estimations> compressed = new ArrayList<>();
        String currentStart = input.get(0).startTime;
        int i = 0;
        while (i < input.size())
        {
            if (i+1 < input.size()) {
                Estimations current = input.get(i);
                if (!(current.prediction.equals(input.get(i + 1).prediction))) {
                    compressed.add(new Estimations(current.prediction, currentStart, current.endTime));
                    currentStart = input.get(i + 1).startTime;
                }
            }else compressed.add(new Estimations(input.get(i).prediction, currentStart, input.get(i).endTime));
            i++;
        }
        return compressed;
    }

    public Date convertTimeString(String s){
        SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-DD hh:mm:ss");
        Date convertedTime = new Date();
        try {
            convertedTime = dateFormat.parse(s);
        }catch (ParseException e){
            e.printStackTrace();
        }
        return convertedTime;
    }

    public void startAnalysis() {
        if (timer == null) {
            timer = new Timer();
        }
        // skal muligvis stoppes inden reschedule
        timer.schedule(timerTask, delay, period);
    }

    public void stopAnalysis() {
        timer.cancel();
        timer.purge();
    }

    public void analysisParameters(Intent intent) {
        period = intent.getIntExtra("period", 1000);
        delay = intent.getIntExtra("delay", 0);
    }


}
