package git.artdeell.mojo.fragments;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.modding.cubo.CuboModrinth;
import org.json.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit, paginated catalogue. No requests on each keystroke or while hidden. */
public class CuboModrinthFragment extends Fragment {
 private static final int BG=0xFF060C09,CARD=0xFF112017,TEXT=0xFFEDF6F0,MUTED=0xFF9AAFA1,GREEN=0xFF35D6A0;
 private final Handler main=new Handler(Looper.getMainLooper());
 private LinearLayout body,results;
 private EditText query;
 private TextView status,instance;
 private Button next,previous,search,optimize;
 private CuboModrinth.Target target;
 private Future<?> request;
 private int generation,offset;
 private boolean onlyOptimization;
 private String lastQuery="";
 private AlertDialog dialog;
 public CuboModrinthFragment(){super();}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private LinearLayout column(){LinearLayout v=new LinearLayout(requireContext());v.setOrientation(LinearLayout.VERTICAL);return v;}
 private TextView text(String s,int size,int color){TextView t=new TextView(requireContext());t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 private GradientDrawable shape(int c){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(18));d.setStroke(dp(1),0xFF2C4133);return d;}
 private Button button(LinearLayout parent,String title,Runnable run){Button b=new Button(requireContext());b.setText(title);b.setAllCaps(false);b.setTextColor(TEXT);b.setBackgroundTintList(null);b.setBackground(shape(CARD));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(10);parent.addView(b,p);b.setOnClickListener(v->run.run());return b;}
 private boolean valid(int id){return isAdded()&&body!=null&&id==generation&&getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED);}
 private int begin(){if(request!=null)request.cancel(true);return ++generation;}
 @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle saved){
  ScrollView scroll=new ScrollView(requireContext());scroll.setBackgroundColor(BG);body=column();body.setPadding(dp(18),dp(20),dp(18),dp(28));scroll.addView(body);
  TextView title=text("Explorar mods",29,TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);body.addView(title);
  body.addView(text("MODRINTH · Catálogo para tu instancia",13,GREEN));
  instance=text("Detectando versión y cargador…",14,MUTED);instance.setPadding(0,dp(12),0,dp(10));body.addView(instance);
  query=new EditText(requireContext());query.setTextColor(TEXT);query.setHintTextColor(MUTED);query.setSingleLine(true);query.setHint("Buscar mods…");query.setTextSize(16);query.setPadding(dp(14),0,dp(14),0);query.setBackground(shape(CARD));query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);body.addView(query,new LinearLayout.LayoutParams(-1,dp(54)));
  search=button(body,"Buscar",()->{offset=0;lastQuery=query.getText().toString().trim();load();});search.setEnabled(false);
  optimize=button(body,"Mostrar solo optimización",()->{onlyOptimization=!onlyOptimization;optimize.setText(onlyOptimization?"Optimización ✓ · Mostrar todos":"Mostrar solo optimización");offset=0;lastQuery=query.getText().toString().trim();load();});optimize.setEnabled(false);
  status=text("",14,MUTED);status.setPadding(0,dp(14),0,dp(8));body.addView(status);
  results=column();body.addView(results);
  previous=button(body,"← Anteriores",()->{offset=Math.max(0,offset-20);load();});previous.setEnabled(false);
  next=button(body,"Siguientes →",()->{offset+=20;load();});next.setEnabled(false);
  query.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH&&target!=null){offset=0;lastQuery=query.getText().toString().trim();load();return true;}return false;});
  if(saved!=null){lastQuery=saved.getString("query","");onlyOptimization=saved.getBoolean("optimization",false);offset=saved.getInt("offset",0);query.setText(lastQuery);optimize.setText(onlyOptimization?"Optimización ✓ · Mostrar todos":"Mostrar solo optimización");}
  return scroll;
 }
 @Override public void onResume(){super.onResume();if(body!=null)detect();}
 @Override public void onSaveInstanceState(@NonNull Bundle b){super.onSaveInstanceState(b);b.putString("query",query==null?lastQuery:query.getText().toString());b.putBoolean("optimization",onlyOptimization);b.putInt("offset",offset);}
 private void detect(){final int id=begin();request=MojoApplication.sExecutorService.submit(()->{
  try{CuboModrinth.Target detected=CuboModrinth.target(Instances.loadSelectedInstance());main.post(()->{if(!valid(id))return;target=detected;instance.setText(detected.name+" · "+detected.minecraft+" · "+detected.loader);search.setEnabled(true);optimize.setEnabled(true);load();});}
  catch(Exception e){main.post(()->{if(valid(id)){target=null;status.setText(e.getMessage());instance.setText("Instancia no preparada para mods");search.setEnabled(false);optimize.setEnabled(false);}});}
 });}
 private void load(){if(target==null)return;final int id=begin();final CuboModrinth.Target chosen=target;final String q=lastQuery;final int page=offset;final boolean filter=onlyOptimization;
  results.removeAllViews();next.setEnabled(false);previous.setEnabled(false);status.setText("Buscando en Modrinth…");
  request=MojoApplication.sExecutorService.submit(()->{try{JSONObject response=CuboModrinth.search(chosen,q,page,filter);main.post(()->{if(valid(id))show(response);});}catch(Exception e){main.post(()->{if(valid(id)){status.setText("No se pudo buscar: "+e.getMessage());previous.setEnabled(page>0);}});}});
 }
 private void show(JSONObject response){
  JSONArray hits=response.optJSONArray("hits");int count=hits==null?0:hits.length(),total=response.optInt("total_hits");
  status.setText(count==0?"Sin resultados compatibles. Prueba otra búsqueda.":"Resultados "+(offset+1)+"–"+(offset+count)+" de "+total);
  for(int i=0;i<count;i++){JSONObject hit=hits.optJSONObject(i);if(hit==null)continue;final String project=hit.optString("project_id");final String name=hit.optString("title","Mod");
   LinearLayout card=column();card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(shape(CARD));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);results.addView(card,p);
   TextView title=text(name,19,TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);card.addView(title);card.addView(text("por "+hit.optString("author")+" · "+hit.optLong("downloads")+" descargas",12,GREEN));
   TextView description=text(hit.optString("description"),14,MUTED);description.setPadding(0,dp(9),0,dp(5));description.setMaxLines(4);description.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(description);
   button(card,"Ver versiones compatibles",()->details(project,name));
  }
  previous.setEnabled(offset>0);next.setEnabled(offset+count<total&&count>0);
 }
 private void details(String project,String title){if(target==null)return;final int id=begin();final CuboModrinth.Target selectedTarget=target;status.setText("Consultando "+title+"…");
  request=MojoApplication.sExecutorService.submit(()->{try{
   JSONObject projectInfo=CuboModrinth.project(project);JSONArray versions=CuboModrinth.versions(project,selectedTarget);final JSONArray choices=new JSONArray();
   for(int i=0;i<versions.length()&&choices.length()<40;i++){JSONObject v=versions.getJSONObject(i);if(!"alpha".equals(v.optString("version_type")))choices.put(v);}
   main.post(()->{if(!valid(id))return;status.setText("Filtrado para "+selectedTarget.minecraft+" / "+selectedTarget.loader);
    if("unsupported".equals(projectInfo.optString("client_side"))){status.setText("Este proyecto no admite instalación en el cliente.");return;}
    if(choices.length()==0){status.setText("No hay versiones estables o beta compatibles disponibles.");return;}
    String[] labels=new String[choices.length()];for(int i=0;i<labels.length;i++){JSONObject v=choices.optJSONObject(i);labels[i]=v.optString("version_number")+" · "+v.optString("version_type");}
    dialog=new AlertDialog.Builder(requireContext()).setTitle(title).setItems(labels,(d,index)->confirm(title,projectInfo,choices.optJSONObject(index),selectedTarget)).setNegativeButton("Cerrar",null).setNeutralButton("Página del proyecto",(d,w)->Tools.openURL(requireActivity(),"https://modrinth.com/mod/"+project)).show();
   });
  }catch(Exception e){main.post(()->{if(valid(id))status.setText("No se pudieron leer las versiones: "+e.getMessage());});}});
 }
 private void confirm(String title,JSONObject project,JSONObject version,CuboModrinth.Target selectedTarget){
  if(CuboModrinth.INSTALLING.get()){status.setText("Ya hay una instalación en curso.");return;}
  String extra="";String slug=project.optString("slug");
  if("sodium".equals(slug)||"iris".equals(slug))extra="\n\nAtención: los mods de renderizado pueden no funcionar con tu GPU o renderizador de Android. Compatibilidad de versión no significa compatibilidad gráfica.";
  JSONObject license=project.optJSONObject("license");String licenseName=license==null?"Ver página del autor":license.optString("id");
  dialog=new AlertDialog.Builder(requireContext()).setTitle("Instalar "+title+"?")
   .setMessage("Instancia: "+selectedTarget.name+"\nMinecraft "+selectedTarget.minecraft+" / "+selectedTarget.loader+"\nVersión: "+version.optString("version_number")+"\nLicencia: "+licenseName+"\n\nSe enviarán a Modrinth las huellas de tus mods instalados para detectar versiones y conflictos; no se envían mundos ni cuentas. Se comprobarán y descargarán las dependencias obligatorias. No se reemplazan archivos ni versiones de mods existentes. Cierra Minecraft antes de continuar. Los mods personalizados pueden tener conflictos no declarados en Modrinth."+extra)
   .setNegativeButton("Cancelar",null).setPositiveButton("Instalar con Minecraft cerrado",(d,w)->install(version,selectedTarget)).show();
 }
 private void install(JSONObject version,CuboModrinth.Target chosen){
  if(!CuboModrinth.INSTALLING.compareAndSet(false,true))return;
  final Context app=requireContext().getApplicationContext();final AtomicBoolean cancel=new AtomicBoolean(false);final int id=generation;
  final TextView progress=text("Comprobando archivos…",14,TEXT);progress.setPadding(dp(20),dp(16),dp(20),dp(16));
  final AlertDialog progressDialog=new AlertDialog.Builder(requireContext()).setTitle("Instalando mods").setView(progress).setCancelable(false).setNegativeButton("Cancelar",(d,w)->cancel.set(true)).create();dialog=progressDialog;progressDialog.show();
  // Detaching dismisses the dialog and requests cancellation; the worker rolls back its new files.
  progressDialog.setOnDismissListener(d->cancel.set(true));
  MojoApplication.sExecutorService.execute(()->{
   String error=null;int amount=0;
   try{amount=CuboModrinth.install(app,chosen,version,cancel,line->main.post(()->{if(valid(id)&&progressDialog.isShowing())progress.setText(line);}));}
   catch(Exception e){error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
   finally{CuboModrinth.INSTALLING.set(false);}
   final String result=error;final int count=amount;
   main.post(()->{progressDialog.dismiss();if(!valid(id))return;status.setText(result==null?(count==0?"Los mods requeridos ya estaban instalados.":"Instalados "+count+" archivos verificados. Reinicia Minecraft para usarlos."):"No se completó la instalación: "+result);});
  });
 }
 @Override public void onPause(){if(request!=null)request.cancel(true);generation++;if(dialog!=null){dialog.dismiss();dialog=null;}super.onPause();}
 @Override public void onDestroyView(){generation++;if(request!=null)request.cancel(true);if(dialog!=null){dialog.dismiss();dialog=null;}super.onDestroyView();body=null;results=null;query=null;status=null;instance=null;search=null;optimize=null;next=null;previous=null;}
}
