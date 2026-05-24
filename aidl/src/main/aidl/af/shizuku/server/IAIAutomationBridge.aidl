package af.shizuku.server;

import android.graphics.Bitmap;

interface IAIAutomationBridge {
    String getWindowHierarchy();
    boolean simulateTouch(float x, float y);
    boolean simulateSwipe(float x1, float y1, float x2, float y2, int duration);
    boolean simulateText(String text);
    int getPixelColor(int x, int y);
    Bitmap captureLayer(int layerId);
}
