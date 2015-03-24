package dk.aau.cs.psylog.analysis.sleepestimate;

/**
 * Created by Lars on 20-03-2015.
 */
public class Estimations {
    String prediction;
    String startTime;
    String endTime;

    public Estimations(int pred, String st, String et)
    {
        if (pred == 1 )
            prediction = "wake";
        else prediction = "sleep";
        startTime = st;
        endTime = et;
    }

    public Estimations(String pred, String st, String et)
    {
        prediction = pred;
        startTime = st;
        endTime = et;
    }
}
