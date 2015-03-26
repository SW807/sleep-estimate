package dk.aau.cs.psylog.analysis.sleepestimate;

/**
 * Created by Lars on 20-03-2015.
 */
public class Estimations {
    String prediction;
    String startTime;
    String endTime;

    public Estimations(String pred, String st, String et)
    {
        prediction = pred;
        startTime = st;
        endTime = et;
    }
}
