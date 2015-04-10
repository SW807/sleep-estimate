package dk.aau.cs.psylog.analysis.sleepestimate;

/**
 * Created by Lars on 23-03-2015.
 */
public class NormalizedWithTime {
    float diffX, diffY, diffZ;
    String time;

    public NormalizedWithTime(float x, float y, float z, String t)
    {
        diffX = x;
        diffY = y;
        diffZ = z;
        time = t;
    }
}
