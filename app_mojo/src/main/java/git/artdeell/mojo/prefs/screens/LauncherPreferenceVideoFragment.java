package git.artdeell.mojo.prefs.screens;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.game.renderer.RendererCache;
import git.artdeell.mojo.prefs.LauncherPreferences;

/** Native Cubo video screen backed by the original Mojo preference keys. */
public class LauncherPreferenceVideoFragment extends Fragment {
 private static final int BG=0xFF060C09,CARD=0xFF111E18,TEXT=0xFFF0F8F3,MUTED=0xFF9CAFA3,ACCENT=0xFF35D6A0;
 private LinearLayout body, renderers;
 private Switch vsync;
 private boolean binding;
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private SharedPreferences prefs(){return LauncherPreferences.DEFAULT_PREF;}
 private GradientDrawable shape(int color,boolean selected){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(22));d.setStroke(dp(1),selected?ACCENT:0xFF2A3B31);return d;}
 private LinearLayout column(){LinearLayout l=new LinearLayout(requireContext());l.setOrientation(LinearLayout.VERTICAL);return l;}
 private TextView text(String value,int size,int color){TextView t=new TextView(requireContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
 private void title(LinearLayout parent,String value){TextView t=text(value,19,TEXT);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parent.addView(t);}
 private void note(LinearLayout parent,String value){TextView t=text(value,14,MUTED);t.setPadding(0,dp(7),0,dp(6));parent.addView(t);}
 private void section(String value){TextView t=text(value,12,ACCENT);t.setLetterSpacing(0.13f);t.setPadding(dp(3),dp(26),0,dp(4));body.addView(t);}
 private LinearLayout card(LinearLayout parent,boolean selected){LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(shape(CARD,selected));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);parent.addView(c,p);return c;}
 private void saveBoolean(String key,boolean value){prefs().edit().putBoolean(key,value).apply();LauncherPreferences.loadPreferences(requireContext());}
 @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle saved){
  ScrollView scroll=new ScrollView(requireContext());scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
  body=column();body.setPadding(dp(18),dp(22),dp(18),dp(32));scroll.addView(body);
  TextView heading=text("Gráficos",30,TEXT);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);body.addView(heading);
  note(body,"CUBO  /  "+Build.MANUFACTURER+" "+Build.MODEL);
  LinearLayout info=card(body,false);note(info,"Ajusta la imagen a tu dispositivo. Los cambios se aplican al próximo inicio del juego.");
  section("MOTOR DE RENDERIZADO");renderers=column();body.addView(renderers);rendererCards();
  section("CALIDAD DE IMAGEN");LinearLayout image=card(body,false);resolution(image);
  section("PANTALLA Y FLUIDEZ");LinearLayout display=card(body,false);
  toggle(display,"Superficie alternativa","Cambia cómo se presenta la imagen. Puede ayudar en algunos dispositivos.","alternate_surface",LauncherPreferences.PREF_USE_ALTERNATE_SURFACE,value->{if(vsync!=null){vsync.setEnabled(value);vsync.setAlpha(value?1f:0.45f);}});
  vsync=toggle(display,"Sincronización vertical","Intenta sincronizar la imagen con la pantalla. Disponible con superficie alternativa.","force_vsync",LauncherPreferences.PREF_FORCE_VSYNC,null);
  vsync.setEnabled(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);vsync.setAlpha(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE?1f:0.45f);
  if(Build.VERSION.SDK_INT>=24)toggle(display,"Rendimiento sostenido","Solicita un rendimiento más estable durante sesiones largas; el efecto depende del teléfono.","sustainedPerformance",LauncherPreferences.PREF_SUSTAINED_PERFORMANCE,null);
  if(LauncherPreferences.hasNotch(requireActivity()))toggle(display,"Usar zona de la cámara","Permite extender el juego hacia la zona del recorte de pantalla.","ignoreNotch",prefs().getBoolean("ignoreNotch",false),null);
  section("OPCIONES ESPECIALIZADAS");LinearLayout advanced=card(body,false);title(advanced,"Drivers y compatibilidad");note(advanced,"ANGLE, controladores Vulkan y opciones completas del motor. Se conserva el gestor original para estas funciones.");
  Button b=new Button(requireContext());b.setText("Abrir opciones avanzadas  ›");b.setAllCaps(false);b.setTextColor(BG);b.setBackgroundTintList(null);b.setBackground(shape(ACCENT,false));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(56));bp.topMargin=dp(12);advanced.addView(b,bp);
  b.setOnClickListener(v->Tools.swapFragment(requireActivity(),CuboAdvancedVideoFragment.class,"CuboAdvancedVideoFragment",null));
  note(body,"La distancia de renderizado y el límite de FPS se ajustan dentro de Minecraft. No se muestran aquí como controles sin efecto.");
  return scroll;
 }
 private interface Changed {void set(boolean value);}
 private Switch toggle(LinearLayout parent,String label,String description,String key,boolean initial,Changed callback){
  LinearLayout row=column();LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(14);parent.addView(row,rp);
  Switch control=new Switch(requireContext());control.setText(label);control.setTextSize(16);control.setTextColor(TEXT);control.setPadding(0,dp(5),0,dp(5));control.setSwitchMinWidth(dp(48));control.setThumbTintList(ColorStateList.valueOf(0xFFF0F8F3));control.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,0xFF435349}));control.setChecked(initial);row.addView(control,new LinearLayout.LayoutParams(-1,dp(52)));note(row,description);
  control.setOnCheckedChangeListener((v,checked)->{saveBoolean(key,checked);if(callback!=null)callback.set(checked);});return control;
 }
 private void resolution(LinearLayout image){
  title(image,"Resolución del juego");note(image,"Menor resolución reduce la carga gráfica; mayor resolución mejora la nitidez.");
  TextView value=text("",26,ACCENT);value.setTypeface(Typeface.DEFAULT,Typeface.BOLD);image.addView(value);
  SeekBar seek=new SeekBar(requireContext());seek.setMax(15);int current=Math.max(25,Math.min(100,(int)(LauncherPreferences.PREF_SCALE_FACTOR*100)));int progress=Math.round((current-25)/5f);value.setText((25+progress*5)+" %");seek.setProgress(progress);seek.setProgressTintList(ColorStateList.valueOf(ACCENT));seek.setThumbTintList(ColorStateList.valueOf(ACCENT));image.addView(seek,new LinearLayout.LayoutParams(-1,dp(54)));
  seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){value.setText((25+p*5)+" %");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){prefs().edit().putInt("resolutionRatio",25+s.getProgress()*5).apply();LauncherPreferences.loadPreferences(requireContext());}});
 }
 private void rendererCards(){
  renderers.removeAllViews();RendererCache compatible=RendererCache.getCompatibleRenderers(requireContext());String selected=prefs().getString("renderer","opengles2");
  if(compatible.rendererIds.isEmpty()){note(renderers,"No se detectaron renderizadores disponibles. Revisa la instalación del motor.");return;}
  for(int i=0;i<compatible.rendererIds.size();i++){
   final String id=compatible.rendererIds.get(i);String name=compatible.rendererDisplayNames[i];boolean active=id.equals(selected);
   LinearLayout c=card(renderers,active);title(c,(active?"●  ":"○  ")+name);String lower=(id+" "+name).toLowerCase(java.util.Locale.ROOT);
   String desc=lower.contains("zink")?"OpenGL sobre Vulkan. La compatibilidad depende del controlador y del juego.":lower.contains("gl4es")?"Traducción de OpenGL a OpenGL ES.":lower.contains("ltw")?"Capa de traducción gráfica. Prueba su compatibilidad con tu versión.":"Motor disponible según la detección de MojoLauncher.";
   note(c,desc);if(active)note(c,"Seleccionado para el próximo inicio");
   c.setOnClickListener(v->{prefs().edit().putString("renderer",id).apply();LauncherPreferences.loadPreferences(requireContext());rendererCards();});
  }
 }
 @Override public void onDestroyView(){super.onDestroyView();body=null;renderers=null;vsync=null;}
}
