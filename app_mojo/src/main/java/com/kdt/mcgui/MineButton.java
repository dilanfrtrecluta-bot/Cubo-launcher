package com.kdt.mcgui;
import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Typeface;
import git.artdeell.mojo.R;
public class MineButton extends androidx.appcompat.widget.AppCompatButton {
 public MineButton(Context c){this(c,null);}
 public MineButton(Context c,AttributeSet a){super(c,a);init();}
 public void init(){setTypeface(Typeface.DEFAULT,Typeface.BOLD);setAllCaps(false);setTextSize(16);setTextColor(0xFF042216);setSupportBackgroundTintList(null);setBackgroundResource(R.drawable.cubo_button_primary);setMinHeight((int)(52*getResources().getDisplayMetrics().density));}
}
