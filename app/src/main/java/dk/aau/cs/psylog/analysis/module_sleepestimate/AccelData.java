package dk.aau.cs.psylog.analysis.module_sleepestimate;

/**
 * Created by Lars on 19-03-2015.
 */
public class AccelData {
    float x;
    float y;
    float z;
    String time;

    public AccelData(float a, float b, float c, String s)
    {
        x = a;
        y = b;
        z = c;
        time = s;
    }

}
