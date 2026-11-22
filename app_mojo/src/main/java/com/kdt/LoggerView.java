package com.kdt;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import git.artdeell.mojo.Logger;
import git.artdeell.mojo.R;

/** Bounded live console. File logging remains unchanged. */
public class LoggerView extends ConstraintLayout {
 private static final int MAX_VISIBLE=65536,MAX_PENDING=16384;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final Object lock=new Object();
 private final StringBuilder pending=new StringBuilder();
 private boolean scheduled;
 private volatile boolean collecting;
 private Logger.eventLogListener listener;
 private ToggleButton toggle;
 private DefocusableScrollView scroll;
 private TextView output;
 private final Runnable flush=()->{
  String text;
  synchronized(lock){scheduled=false;if(!collecting){pending.setLength(0);return;}text=pending.toString();pending.setLength(0);}
  output.append(text);
  CharSequence content=output.getText();int excess=content.length()-MAX_VISIBLE;
  if(excess>0){if(content instanceof Editable)((Editable)content).delete(0,excess);else output.setText(content.subSequence(excess,content.length()),TextView.BufferType.EDITABLE);}
  if(scroll.isKeepFocusing())scroll.fullScroll(View.FOCUS_DOWN);
 };
 public LoggerView(@NonNull Context c){this(c,null);}
 public LoggerView(@NonNull Context c,@Nullable AttributeSet attrs){super(c,attrs);init();}
 @Override public void setVisibility(int visibility){super.setVisibility(visibility);if(toggle!=null)toggle.setChecked(visibility==VISIBLE);}
 private void stop(){
  collecting=false;Logger.setLogListener(null);main.removeCallbacks(flush);
  synchronized(lock){pending.setLength(0);scheduled=false;}
 }
 private void start(){collecting=true;Logger.setLogListener(listener);}
 private void init(){
  inflate(getContext(),R.layout.view_logger,this);
  output=findViewById(R.id.content_log_view);output.setTypeface(Typeface.MONOSPACE);output.setMaxLines(1500);output.setEllipsize(null);output.setText("",TextView.BufferType.EDITABLE);output.setVisibility(GONE);
  scroll=findViewById(R.id.content_log_scroll);scroll.setKeepFocusing(true);
  listener=text->{
   if(!collecting||text==null)return;
   synchronized(lock){
    if(!collecting)return;
    int length=text.length();
    if(length>=MAX_PENDING){pending.setLength(0);pending.append(text,length-MAX_PENDING,length);}
    else {int excess=pending.length()+length+1-MAX_PENDING;if(excess>0)pending.delete(0,Math.min(excess,pending.length()));pending.append(text).append('\n');}
    if(!scheduled){scheduled=true;main.postDelayed(flush,200);}
   }
  };
  toggle=findViewById(R.id.content_log_toggle_log);
  toggle.setOnCheckedChangeListener((button,checked)->{output.setVisibility(checked?VISIBLE:GONE);if(checked)start();else{stop();output.setText("",TextView.BufferType.EDITABLE);}});
  toggle.setChecked(false);
  ImageButton cancel=findViewById(R.id.log_view_cancel);cancel.setOnClickListener(v->setVisibility(GONE));
  ToggleButton autoscroll=findViewById(R.id.content_log_toggle_autoscroll);
  autoscroll.setOnCheckedChangeListener((button,checked)->{if(checked)scroll.fullScroll(View.FOCUS_DOWN);scroll.setKeepFocusing(checked);});autoscroll.setChecked(true);
 }
 @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(toggle!=null&&toggle.isChecked()&&getVisibility()==VISIBLE)start();}
}
