package git.artdeell.mojo.prefs.screens;


import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.R;
import git.artdeell.mojo.prefs.LauncherPreferences;

/**
 * Preference for the main screen, any sub-screen should inherit this class for consistent behavior,
 * overriding only onCreatePreferences
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected Runnable mVisibilityUpdater = () -> {};

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.setBackgroundColor(getResources().getColor(R.color.background_app));
        super.onViewCreated(view, savedInstanceState);
        simplify(getPreferenceScreen());
        setDivider(null);
        androidx.recyclerview.widget.RecyclerView list=getListView();
        list.setClipToPadding(false);
        int pad=px(14); list.setPadding(pad,px(8),pad,px(20));
        list.addOnChildAttachStateChangeListener(new androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener(){
            @Override public void onChildViewAttachedToWindow(@NonNull View child){styleRow(child,list);}
            @Override public void onChildViewDetachedFromWindow(@NonNull View child){}
        });
        list.post(()->{for(int i=0;i<list.getChildCount();i++)styleRow(list.getChildAt(i),list);});
    }

    private int px(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void simplify(androidx.preference.Preference preference){
        if(preference==null)return;
        preference.setIcon(null);preference.setIconSpaceReserved(false);
        if(preference instanceof git.artdeell.mojo.prefs.BackButtonPreference)preference.setVisible(false);
        if(preference instanceof androidx.preference.PreferenceGroup){
            androidx.preference.PreferenceGroup group=(androidx.preference.PreferenceGroup)preference;
            for(int i=0;i<group.getPreferenceCount();i++)simplify(group.getPreference(i));
        }
    }
    private void styleRow(View row,androidx.recyclerview.widget.RecyclerView list){
        int pos=list.getChildAdapterPosition(row);
        androidx.preference.Preference pref=null;
        if(pos!=androidx.recyclerview.widget.RecyclerView.NO_POSITION && list.getAdapter() instanceof androidx.preference.PreferenceGroupAdapter)
            pref=((androidx.preference.PreferenceGroupAdapter)list.getAdapter()).getItem(pos);
        boolean category=pref instanceof androidx.preference.PreferenceCategory;
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(category?0xFF060C09:0xFF112017);background.setCornerRadius(px(20));
        if(!category)background.setStroke(px(1),0xFF2C4133);
        row.setBackground(background);row.setPadding(px(16),px(12),px(16),px(12));
        if(row.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams){
            android.view.ViewGroup.MarginLayoutParams p=(android.view.ViewGroup.MarginLayoutParams)row.getLayoutParams();
            p.topMargin=px(category?14:6);p.bottomMargin=px(6);row.setLayoutParams(p);
        }
        android.widget.TextView title=row.findViewById(android.R.id.title);
        android.widget.TextView summary=row.findViewById(android.R.id.summary);
        if(title!=null){title.setSingleLine(false);title.setMaxLines(6);title.setEllipsize(null);title.setTextSize(category?12:17);title.setTextColor(category?0xFF35D6A0:0xFFEDF6F0);title.setTypeface(android.graphics.Typeface.DEFAULT,category?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);}
        if(summary!=null){summary.setSingleLine(false);summary.setMaxLines(10);summary.setTextSize(13);summary.setTextColor(0xFF9AAFA1);}
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        mVisibilityUpdater = this::updateVisibility;
        addPreferencesFromResource(R.xml.pref_main);
        setupNotificationRequestPreference();
    }

    private void updateVisibility(){
        requirePreference("notification_permission_request").setVisible(!getLauncherActivity().checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS));
    }

    private void setupNotificationRequestPreference() {
        Preference mRequestNotificationPermissionPreference = requirePreference("notification_permission_request");
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            mRequestNotificationPermissionPreference.setOnPreferenceClickListener(preference -> {
                ((LauncherActivity) activity).askForPermission(33, Manifest.permission.POST_NOTIFICATIONS);
                return true;
            });
        }else{
            mRequestNotificationPermissionPreference.setVisible(false);
        }
        updateVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mVisibilityUpdater.run();
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if(preference != null) return preference;
        throw new IllegalStateException("Preference "+key+" is null");
    }
    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if(preferenceClass.isInstance(preference)) return (T)preference;
        throw new IllegalStateException("Preference "+key+" is not an instance of "+preferenceClass.getSimpleName());
    }
    protected LauncherActivity getLauncherActivity(){
        return ((LauncherActivity) getActivity());
    }
}
