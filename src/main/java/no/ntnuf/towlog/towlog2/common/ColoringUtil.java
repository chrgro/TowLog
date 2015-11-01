package no.ntnuf.towlog.towlog2.common;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;

/**
 * Android coloring is horribly broken, so need to support different coloring functionality
 * for different API levels...
 */
public class ColoringUtil {

    public static void colorMe(View view, int color) {
        if (Build.VERSION.SDK_INT == 19) {
            // For rounded edges buttons (looks mostly like android 5.0)
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(2);

            // Dim the color a bit down, to match color filter multiply
            int r = (Color.red(color) * 9) / 11;
            int g = (Color.green(color) * 9) / 11;
            int b = (Color.blue(color) * 9) / 11;
            shape.setColor(Color.argb(255, r, g, b));

            view.setBackground(shape);

            // For square buttons:
            //view.setBackgroundColor(color);
        } else {
            // Much nicer in non-kitkat versions. This does not work in API 19 (kitkat)
            view.getBackground().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        }
    }
}
