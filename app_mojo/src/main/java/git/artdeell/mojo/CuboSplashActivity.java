package git.artdeell.mojo;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Lightweight branding screen. Original storage checks run immediately afterwards. */
public class CuboSplashActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean opened;
    private final Runnable enter = () -> {
        if (isFinishing() || opened) return;
        opened = true;
        startActivity(new Intent(this, TestStorageActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    };
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(Color.rgb(5,10,8));
        getWindow().setNavigationBarColor(Color.rgb(5,10,8));
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFF050A08, 0xFF09261C, 0xFF050A08});
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setGradientRadius(dp(260));
        root.setBackground(bg);
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        root.addView(brand,center);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.cubo_icon);
        brand.addView(icon,new LinearLayout.LayoutParams(dp(150),dp(150)));
        TextView name = new TextView(this);
        name.setText("CUBO"); name.setTextSize(32); name.setLetterSpacing(0.22f);
        name.setTextColor(0xFFF2FAF6); name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams title = new LinearLayout.LayoutParams(-1,-2);
        title.topMargin=dp(22); brand.addView(name,title);
        TextView sub = new TextView(this);
        sub.setText("LAUNCHER"); sub.setTextSize(12); sub.setLetterSpacing(0.4f);
        sub.setTextColor(0xFF35D6A0); sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitle = new LinearLayout.LayoutParams(-1,-2);
        subtitle.topMargin=dp(8); brand.addView(sub,subtitle);
        setContentView(root);
        boolean animate = android.provider.Settings.Global.getFloat(getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        if (animate) {
            brand.setAlpha(0f); brand.setScaleX(0.94f); brand.setScaleY(0.94f);
            brand.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(550).start();
        }
        handler.postDelayed(enter,animate ? 1050 : 0);
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(enter);
        super.onDestroy();
    }
}
