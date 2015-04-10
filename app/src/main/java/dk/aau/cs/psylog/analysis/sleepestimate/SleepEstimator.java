package dk.aau.cs.psylog.analysis.sleepestimate;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

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
        write = Uri.parse(DBAccessContract.DBACCESS_CONTENTPROVIDER + "sleepestimate_sleepestimates");

        timerTask = new TimerTask() {
            @Override
            public void run() {
                analyse();

            }
        };

    }

    public void analyse()
    {
        String[] projection = {"accX", "accY", "accZ", "time"};
        Cursor cursor = resolver.query(read, projection, null, null, null);
        Log.e("LARS", "read complete, size "+ cursor.getCount());

        List<AccelData> data = extract(cursor);
        Log.e("LARS","extraction size " + data.size()+ " moving to movAVG");
        List<AccelData> movAvg = movingAvg(data);
        Log.e("LARS","starting normalize");
        List<NormalizedWithTime> normalizedData = normalizer(movAvg);

        Log.e("LARS", "starting evaluation");
        List<Estimations> evaluation = estimate(normalizedData);
        Log.e("LARS", "compressions");
        List<Estimations> toStore = compress(evaluation);

        Log.e("LARS", "starting store");
        ContentValues contentValues = new ContentValues();
        for (Estimations e: toStore)
        {
            contentValues.put("estimation", e.prediction);
            contentValues.put("startTime", e.startTime);
            contentValues.put("endTime", e.endTime);
            resolver.insert(write, contentValues);
        }
        Log.e("LARS", "Finished");
    }

    public List<AccelData> extract(Cursor cursor)
    {
        List<AccelData> content = new ArrayList<>();
        int i = 1;
        if (cursor.moveToFirst())
        {
            do {
                float accX = cursor.getFloat(cursor.getColumnIndex("accX"));
                float accY = cursor.getFloat(cursor.getColumnIndex("accY"));
                float accZ = cursor.getFloat(cursor.getColumnIndex("accZ"));
                String time = cursor.getString(cursor.getColumnIndex("time"));
                content.add(new AccelData(accX, accY, accZ, time));
            }while (cursor.moveToNext());
        }
        if (!content.isEmpty()){
            return content;
        }
        return null;
    }


    public List<AccelData> movingAvg(List<AccelData> input){
        float alpha = 0.1f;
        List<AccelData> movAvg = new ArrayList<>();
        AccelData maiminus1 = input.get(0);
        for(AccelData datum : input)
        {
            AccelData toadd = new AccelData(0, 0, 0, datum.time);
            toadd.x = alpha*datum.x + (1-alpha)*maiminus1.x;
            toadd.y = alpha*datum.y + (1-alpha)*maiminus1.y;
            toadd.z = alpha*datum.z + (1-alpha)*maiminus1.z;
            movAvg.add(toadd);
            maiminus1 = toadd;
        }

        return movAvg;
    }

    public List<NormalizedWithTime> normalizer(List<AccelData> input){
        List<NormalizedWithTime> normalized = new ArrayList<>();

        int i = 1;
        while (i < input.size())
        {
            float diffX = Math.abs(input.get(i).x-input.get(i-1).x);
            float diffY = Math.abs(input.get(i).y-input.get(i-1).y);
            float diffZ = Math.abs(input.get(i).z-input.get(i-1).z);
            normalized.add(new NormalizedWithTime(diffX, diffY, diffZ, input.get(i).time));
            i++;
        }

        return normalized;
    }

    public List<Estimations> estimate(List<NormalizedWithTime> input){
        List<Estimations> state = new ArrayList<>();
        int count = 1;
        int j = 1;
        int i = 1;
        int k = 0;
        int len = input.size();
        int num = getFourMinWindown(j, input);

        String startTime, endTime;
        String pred;

        AccelData thres = threshhold(input);
        while (j < len - num)
        {
            startTime = input.get(j).time;
            endTime = input.get(j+num).time;
            while (i <= num)
            {
                if (thresholdCompare(thres, input.get(i+j)))
                    count ++;
                i++;
            }
            if (count < 0.4 * num)
                pred = "sleep";
            else pred = "wake";

            state.add(k, new Estimations(pred, startTime, endTime));

            count = 1;
            i = 1;
            k ++;
            j = j + num;
            num = getFourMinWindown(j,input);
        }

        return state;

    }

    public AccelData stdDeviation(List<NormalizedWithTime> input){
        float lowestX = (float) Double.MAX_VALUE;
        float lowestY = (float) Double.MAX_VALUE;
        float lowestZ = (float) Double.MAX_VALUE;
        float highestX = (float) Double.MIN_VALUE;
        float highestY = (float) Double.MIN_VALUE;
        float highestZ = (float) Double.MIN_VALUE;


        for (NormalizedWithTime n:input)
        {
            if (n.diffX > highestX)
                highestX = n.diffX;
            if (n.diffX < lowestX)
                lowestX = n.diffX;
            if (n.diffY > highestY)
                highestY = n.diffY;
            if (n.diffY < lowestY)
                lowestY = n.diffY;
            if (n.diffZ > highestZ)
                highestZ = n.diffZ;
            if (n.diffZ < lowestZ)
                lowestZ = n.diffZ;
        }
        //return new AccelData(highestX-lowestX, highestY-lowestY, highestZ-lowestZ, "");
        return new AccelData(0,0,0,"");
    }


    public AccelData mean(List<NormalizedWithTime> input){
        float sumX = 0;
        float sumY = 0;
        float sumZ = 0;

        for (NormalizedWithTime n: input)
        {
            sumX += n.diffX;
            sumY += n.diffY;
            sumZ += n.diffZ;
        }

        int div = input.size();
        return new AccelData(sumX / div, sumY / div, sumZ / div, "");
    }

    public AccelData threshhold(List<NormalizedWithTime> input)
    {
        AccelData mean = mean(input);
        AccelData std = stdDeviation(input);

        AccelData output = new AccelData((mean.x+std.x) , (mean.y+std.y) , (mean.z+std.z) , "");

        Log.e("LARS", "stdDeviation, X: "+std.x + " Y: "+ std.x + " Z: " +std.z);
        Log.e("LARS", "mean, X: "+mean.x + " Y: "+ mean.x + " Z: " +mean.z);
        Log.e("LARS", "Threshold, X: "+output.x + " Y: "+ output.x + " Z: " +output.z);
        return output;
    }

    //skal bruge en måde at extract time på
    public int getFourMinWindown (int j, List<NormalizedWithTime> input)
    {
        int i = j+1;
        Date startTime = convertTimeString(input.get(j).time);


        Boolean condition = true;
        while (condition && i < input.size())
        {
            Date startTime2 = convertTimeString(input.get(i).time);
            //240.000 should be 4 minutes in miliseconds
            if (startTime.getTime()+240000 < startTime2.getTime())
                condition = false;
            else i++;
        }
        return i-j;
    }

    public boolean thresholdCompare (AccelData threshold, NormalizedWithTime toCompare)
    {
        if (toCompare.diffX > threshold.x)
            return true;
        if (toCompare.diffY > threshold.y)
            return true;
        if (toCompare.diffZ > threshold.z)
            return true;

        return false;

    }

    public List<Estimations> compress (List<Estimations> input)
    {
        Log.e("LARS", "compressing list of size " + input.size());
        List<Estimations> compressed = new ArrayList<>();
        String currentStart = input.get(0).startTime;
        int i = 0;
        while (i < input.size())
        {
            if (i+1 < input.size()) {
                Estimations current = input.get(i);
                Log.e("LARS", "compressing " +current.prediction + " and " + input.get(i+1).prediction );
                if (!(current.prediction.equals(input.get(i + 1).prediction))) {
                    compressed.add(new Estimations(current.prediction, currentStart, current.endTime));
                    currentStart = input.get(i + 1).startTime;
                    Log.e("LARS", "they didnt match");
                }else Log.e("LARS", " they matched");
            }else compressed.add(new Estimations(input.get(i).prediction, currentStart, input.get(i).endTime));
            i++;
        }
        for (Estimations e:compressed)
        {
            Log.e("LARS", "estimation with " + e.prediction+ " start "+ e.startTime + "end " + e.endTime);
        }
        return compressed;
    }

    public Date convertTimeString(String s){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        Date convertedTime = new Date();
        try {
            convertedTime = dateFormat.parse(s);
        }catch (ParseException e){
            e.printStackTrace();
        }
        return convertedTime;
    }

}
