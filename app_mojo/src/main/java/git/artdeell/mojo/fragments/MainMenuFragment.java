package git.artdeell.mojo.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.fragment.app.Fragment;
import com.kdt.mcgui.ProgressLayout;
import git.artdeell.mojo.*;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.contracts.OpenDocumentWithExtension;
import git.artdeell.mojo.extra.*;
import git.artdeell.mojo.instances.*;
import git.artdeell.mojo.prefs.screens.*;
import java.io.*;
import java.util.Arrays;

/** Cubo native shell. Uses Mojo's existing launch, account and installation services. */
public class MainMenuFragment extends Fragment {
 public static final String TAG="MainMenuFragment";
 private static final int BG=0xFF060B08, CARD=0xFF101A15, GREEN=0xFF35D6A0, WHITE=0xFFEDF6F0, MUTED=0xFF93A69A;
 private LinearLayout body, nav;
 private int tab=0;
 private File folder;
 private java.util.concurrent.Future<?> screenTask,accountTask;
 private boolean accountBusy;
 private long lastListingMs=-1;
 private int lastListingCount;
 private boolean validScreen(int generation){return isAdded()&&body!=null&&screenGeneration==generation&&getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED);}
 private void cancelScreenWork(){
  if(screenTask!=null){screenTask.cancel(true);screenTask=null;}
  if(accountTask!=null){accountTask.cancel(true);accountTask=null;}
  accountBusy=false;
 }

 private int screenGeneration;
 private int visibleLimit=40;
 private java.util.concurrent.Future<?> pendingListing;
 private static class Entry {
  final File file; final boolean directory; final long size;
  Entry(File file){this.file=file;directory=file.isDirectory();size=directory?0:file.length();}
 }
 private interface ListingReady { void show(Entry[] entries); }
 private void listAsync(File directory,boolean modsOnly,ListingReady ready){
  final int generation=screenGeneration;
  if(pendingListing!=null)pendingListing.cancel(true);
  final TextView loading=text("Leyendo archivos…",14,MUTED);body.addView(loading);
  pendingListing=MojoApplication.sExecutorService.submit(()->{
   final long started=android.os.SystemClock.elapsedRealtime();
   try {
    File[] files=directory.listFiles();
    if(files==null&&directory.exists())throw new IOException("Carpeta no accesible");java.util.ArrayList<Entry> entries=new java.util.ArrayList<>();
    if(files!=null)for(File file:files){
     if(Thread.currentThread().isInterrupted())return;
     if(modsOnly&&(!file.isFile()||(!file.getName().endsWith(".jar")&&!file.getName().endsWith(".jar.disabled"))))continue;
     entries.add(new Entry(file));
    }
    entries.sort((a,b)->a.directory!=b.directory?(a.directory?-1:1):a.file.getName().compareToIgnoreCase(b.file.getName()));
    Entry[] result=entries.toArray(new Entry[0]);
    final long duration=android.os.SystemClock.elapsedRealtime()-started;
    Tools.runOnUiThread(()->{if(!isAdded()||body==null||screenGeneration!=generation)return;body.removeView(loading);lastListingMs=duration;lastListingCount=result.length;ready.show(result);});
   }catch(Exception e){Tools.runOnUiThread(()->{if(isAdded()&&body!=null&&screenGeneration==generation){body.removeView(loading);hint("No se pudo leer la carpeta: "+e.getMessage());}});}
  });
 }
 private void showEntries(Entry[] entries,boolean modsOnly,File base,int start){
  int end=Math.min(start+40,entries.length);
  for(int i=start;i<end;i++){
   Entry entry=entries[i];File f=entry.file;
   if(modsOnly){boolean disabled=f.getName().endsWith(".disabled");
    row(f.getName(),disabled?"Desactivado · toca para activar":"Activo · toca para desactivar",()->{
     if(!ProgressLayout.ensureNoOngoingTasks(requireContext()))return;
     new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(disabled?"¿Activar mod?":"¿Desactivar mod?").setMessage(f.getName()).setPositiveButton("Confirmar",(d,w)->{
      File dest=new File(f.getParentFile(),disabled?f.getName().substring(0,f.getName().length()-9):f.getName()+".disabled");
      if(dest.exists()||!f.renameTo(dest))message("No se pudo cambiar el estado del mod");render();
     }).setNegativeButton("Cancelar",null).show();
    });
   }else{
    row(f.getName(),entry.directory?"Carpeta":(entry.size/1024)+" KB",()->{
     if(entry.directory){try{if(!f.getCanonicalPath().startsWith(base.getCanonicalPath()+File.separator)){message("Ruta fuera de la instancia");return;}}catch(IOException e){return;}folder=f;render();}
     else Tools.openPath(requireContext(),f,false);
    });
   }
  }
  if(end<entries.length){
   Button next=new Button(requireContext());next.setText("Cargar 40 más ("+(entries.length-end)+" restantes)");next.setAllCaps(false);next.setTextColor(GREEN);
   body.addView(next,new LinearLayout.LayoutParams(-1,dp(56)));
   next.setOnClickListener(v->{body.removeView(next);showEntries(entries,modsOnly,base,end);});
  }
 }
 private final ActivityResultLauncher<Object> installer=registerForActivityResult(new OpenDocumentWithExtension("jar"),uri->{
  if(uri!=null) Tools.launchModInstaller(requireContext(),uri);
 });
 private final ActivityResultLauncher<Object> modImport=registerForActivityResult(new OpenDocumentWithExtension("jar"),uri->{
  if(uri==null)return;
  Instance inst=Instances.loadSelectedInstance();
  if(inst==null){message("Selecciona una instancia primero");return;}
  Context context=requireContext().getApplicationContext();
  File dir=new File(inst.getGameDirectory(),"mods");
  MojoApplication.sExecutorService.execute(()->{
   String error=null;
   try {
    if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("No se pudo crear la carpeta mods");
    String name=Tools.getFileName(context,uri);
    if(name==null||!name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))throw new IOException("Selecciona un archivo .jar");
    name=new File(name).getName();
    File dest=new File(dir,name);
    if(!dest.createNewFile())throw new IOException("Ya existe un mod con ese nombre; no se ha reemplazado");
    boolean ok=false;
    try(InputStream in=context.getContentResolver().openInputStream(uri); OutputStream out=new FileOutputStream(dest)) {
     if(in==null)throw new IOException("No se pudo leer el archivo");
     byte[] buffer=new byte[32768]; int n;
     while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
     ok=true;
    } finally { if(!ok)dest.delete(); }
   }catch(Exception e){error=e.getMessage();}
   final String result=error;
   Tools.runOnUiThread(()->{if(isAdded()&&getView()!=null){message(result==null?"Mod importado. Comprueba su compatibilidad con tu versión y cargador.":result);render();}});
  });
 });
 public MainMenuFragment(){super();}
 private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),0xFF27362D);return d;}
 private LinearLayout column(){LinearLayout l=new LinearLayout(requireContext());l.setOrientation(LinearLayout.VERTICAL);return l;}
 private TextView text(String s,int size,int color){TextView t=new TextView(requireContext());t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
 private void gap(LinearLayout l,int n){View v=new View(requireContext());l.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
 private TextView heading(String s){TextView t=text(s,27,WHITE);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);body.addView(t);return t;}
 private void hint(String s){TextView t=text(s,14,MUTED);t.setPadding(0,dp(8),0,dp(12));body.addView(t);}
 private LinearLayout card(LinearLayout parent){LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(shape(CARD,22));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);parent.addView(c,p);return c;}
 private void button(LinearLayout parent,String label,boolean primary,Runnable action){
  Button b=new Button(requireContext());b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(primary?BG:WHITE);b.setBackgroundTintList(null);b.setBackground(shape(primary?GREEN:0xFF15251C,16));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(10);parent.addView(b,p);b.setOnClickListener(v->action.run());
 }
 private void row(String title,String subtitle,Runnable action){LinearLayout c=card(body);TextView t=text(title+"  ›",18,WHITE);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);c.addView(t);TextView sub=text(subtitle,13,MUTED);sub.setPadding(0,dp(6),0,0);c.addView(sub);c.setOnClickListener(v->action.run());}
 private void message(String s){Toast.makeText(requireContext(),s,Toast.LENGTH_LONG).show();}
 private void open(Class<? extends Fragment> c){if(!getParentFragmentManager().isStateSaved())Tools.swapFragment(requireActivity(),c,c.getSimpleName(),null);}
 @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle saved){
  if(saved!=null)tab=saved.getInt("cubo_tab",0);
  LinearLayout root=column();root.setBackgroundColor(BG);
  ScrollView scroll=new ScrollView(requireContext());scroll.setFillViewport(true);scroll.setClipToPadding(false);
  body=column();body.setPadding(dp(18),dp(22),dp(18),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  nav=new LinearLayout(requireContext());nav.setPadding(dp(4),dp(8),dp(4),dp(8));nav.setBackgroundColor(0xFF0B140F);root.addView(nav,new LinearLayout.LayoutParams(-1,dp(72)));
  return root;
 }
 @Override public void onSaveInstanceState(@NonNull Bundle out){super.onSaveInstanceState(out);out.putInt("cubo_tab",tab);}
 @Override public void onResume(){super.onResume();if(body!=null)render();ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER,true);
  if(body!=null)body.post(()->{if(isAdded()&&isResumed()&&isVisible()&&body!=null)CuboOptimizedProfile.offer(requireActivity(),true,()->{if(isAdded()&&body!=null)render();});});
 }
 private void select(int n){tab=n;folder=null;render();}
 private void render(){if(body==null||!isAdded())return;screenGeneration++;cancelScreenWork();if(pendingListing!=null)pendingListing.cancel(true);body.removeAllViews();nav.removeAllViews();
  String[] labels={"Inicio","Versiones","Mods","Archivos","Más"};
  for(int i=0;i<5;i++){final int n=i;LinearLayout item=column();item.setGravity(Gravity.CENTER);if(tab==i)item.setBackground(shape(0xFF103324,18));
   View icon=new NavIcon(requireContext(),i,tab==i?GREEN:MUTED);item.addView(icon,new LinearLayout.LayoutParams(dp(23),dp(23)));
   TextView label=text(labels[i],11,tab==i?GREEN:MUTED);label.setGravity(Gravity.CENTER);item.addView(label);nav.addView(item,new LinearLayout.LayoutParams(0,-1,1));item.setOnClickListener(v->select(n));}
  switch(tab){case 1:versions();break;case 2:mods();break;case 3:files();break;case 4:more();break;default:home();}
 }
 private void home(){
  final int generation=screenGeneration;
  hint("Cargando tu instancia…");
  screenTask=MojoApplication.sExecutorService.submit(()->{
   try {
    Instance inst=Instances.loadSelectedInstance();Account account=Accounts.getCurrent();
    Tools.runOnUiThread(()->{if(!validScreen(generation))return;body.removeAllViews();showHome(inst,account);});
   }catch(Exception e){Tools.runOnUiThread(()->{if(validScreen(generation)){body.removeAllViews();heading("CUBO");hint("No se pudo leer la configuración local. No se han borrado datos.");button(body,"Reintentar",true,this::render);}});}
  });
 }
 private void showHome(Instance inst,Account account){
  TextView brand=heading("CUBO");brand.setLetterSpacing(0.15f);hint("MINECRAFT JAVA EDITION");
  LinearLayout main=card(body);
  main.addView(text("TU INSTANCIA",12,GREEN));gap(main,10);
  TextView version=text(inst==null?"Elige una versión":inst.name,26,WHITE);version.setTypeface(Typeface.DEFAULT,Typeface.BOLD);main.addView(version);
  main.addView(text(inst==null?"Crea tu primera instancia para empezar":String.valueOf(inst.versionId),14,MUTED));
  button(main,account==null?"Añadir cuenta de Minecraft":"Cuenta: "+account.username,false,this::accountMenu);
  button(main,"▶  Jugar",true,()->{
   if(Accounts.getCurrent()==null){accountMenu();return;}
   if(Instances.loadSelectedInstance()==null){select(1);return;}
   ExtraCore.setValue(ExtraConstants.LAUNCH_GAME,true);
  });
  button(main,"Cambiar versión",false,()->select(1));
  row("Controles","Botones táctiles, perfiles y mando",()->startActivity(new Intent(requireContext(),CustomControlsActivity.class)));
  row("Gráficos y rendimiento","Renderizador, resolución y compatibilidad",()->open(LauncherPreferenceVideoFragment.class));
  hint("Cubo Launcher · basado en MojoLauncher");
 }
 private void accountMenu(){
  if(accountBusy)return;
  accountBusy=true;final int generation=screenGeneration;
  accountTask=MojoApplication.sExecutorService.submit(()->{
   try {
    Accounts accounts=Accounts.load();String[] names=new String[accounts.accounts.size()+1];names[0]="Añadir cuenta";
    for(int i=0;i<accounts.accounts.size();i++)names[i+1]=accounts.accounts.get(i).username;
    Tools.runOnUiThread(()->{
     if(!validScreen(generation))return;accountBusy=false;
     new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Cuenta de Minecraft").setItems(names,(d,index)->{
      if(index==0)ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD,true);
      else {Accounts.setCurrent(accounts.accounts.get(index-1));ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER,true);render();}
     }).setNegativeButton("Cerrar",null).show();
    });
   }catch(Exception e){Tools.runOnUiThread(()->{if(validScreen(generation)){accountBusy=false;message("No se pudieron leer las cuentas. No se ha cambiado ningún dato.");}});}
  });
 }
 private void versions(){heading("Versiones");hint("Instancias instaladas. Cada una conserva su configuración.");
  button(body,"+ Crear o instalar versión",true,()->open(ProfileTypeSelectFragment.class));
  button(body,"Preparar Cubo Optimizado 1.20.1",false,()->CuboOptimizedProfile.offer(requireActivity(),false,()->{if(isAdded()&&body!=null)render();}));
  final int generation=screenGeneration;final TextView loading=text("Leyendo instancias…",14,MUTED);body.addView(loading);
  screenTask=MojoApplication.sExecutorService.submit(()->{
   try {
    Instances all=Instances.loadDisplay();
    Tools.runOnUiThread(()->{if(!validScreen(generation))return;body.removeView(loading);showVersions(all.list,0);});
   }catch(Exception e){Tools.runOnUiThread(()->{if(validScreen(generation)){body.removeView(loading);hint("No se pudieron leer las instancias. Pulsa otra pestaña y vuelve para reintentar.");}});}
  });
 }
 private void showVersions(java.util.List<DisplayInstance> list,int start){
  int end=Math.min(start+30,list.size());
  for(int i=start;i<end;i++){DisplayInstance inst=list.get(i);
   row(inst.name,String.valueOf(inst.versionId),()->{Instances.setSelectedInstance(inst);new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(inst.name).setItems(new String[]{"Usar esta instancia","Editar configuración"},(d,n)->{if(n==0)select(0);else open(InstanceEditorFragment.class);}).show();});
  }
  if(end<list.size()){
   Button next=new Button(requireContext());next.setText("Cargar más instancias");next.setAllCaps(false);next.setTextColor(GREEN);body.addView(next,new LinearLayout.LayoutParams(-1,dp(56)));
   next.setOnClickListener(v->{body.removeView(next);showVersions(list,end);});
  }
 }
 private File game(){Instance i=Instances.loadSelectedInstance();return i==null?null:i.getGameDirectory();}
 private void mods(){heading("Mods");hint("Mods de la instancia seleccionada. Deben ser compatibles con su versión y cargador.");
  button(body,"Explorar Modrinth · mods y optimización",true,()->open(CuboModrinthFragment.class));
  File base=game();if(base==null){button(body,"Seleccionar instancia",true,()->select(1));return;}
  button(body,"+ Importar mod .jar",true,()->{if(ProgressLayout.ensureNoOngoingTasks(requireContext()))modImport.launch(null);});
  button(body,"Buscar modpacks (gestor de Mojo)",false,()->open(SearchModFragment.class));
  listAsync(new File(base,"mods"),true,entries->{
   if(entries.length==0)hint("No hay mods instalados en esta instancia.");
   else showEntries(entries,true,base,0);
  });
 }

 private void files(){heading("Archivos");File base=game();if(base==null){hint("Selecciona una instancia primero.");return;}
  if(folder==null)folder=base;
  hint(folder.equals(base)?"Carpeta de la instancia":folder.getName());
  if(!folder.equals(base))button(body,"← Carpeta anterior",false,()->{folder=folder.getParentFile();render();});
  final File current=folder;
  button(body,"Abrir en gestor de archivos",false,()->Tools.openPath(requireContext(),current,false));
  listAsync(folder,false,entries->{
   if(entries.length==0)hint("Carpeta vacía o no disponible.");
   else showEntries(entries,false,base,0);
  });
 }

 private void more(){heading("Más");hint("Configuración clara, agrupada por función.");
  row("Cubo Optimizado 1.20.1","Preparar perfil Fabric con Lithium y FerriteCore · beta",()->CuboOptimizedProfile.offer(requireActivity(),false,()->{if(isAdded()&&body!=null)render();}));
  row("Cuenta de Minecraft","Añadir o cambiar de cuenta",this::accountMenu);
  row("Controles","Editor de botones táctiles",()->startActivity(new Intent(requireContext(),CustomControlsActivity.class)));
  row("Gráficos y rendimiento","Renderizador, resolución y opciones de vídeo",()->open(LauncherPreferenceVideoFragment.class));
  row("Java y memoria","Entornos Java y opciones de la máquina virtual",()->open(LauncherPreferenceJavaFragment.class));
  row("Preferencias de controles","Comportamiento de entrada y mando",()->open(LauncherPreferenceControlFragment.class));
  row("Configuración avanzada","Preferencias completas del proyecto base",()->open(LauncherPreferenceFragment.class));
  row("Diagnóstico local","Memoria, espacio libre y tiempos de carga; sin datos de cuentas",this::diagnostics);
  row("Consola y registros","Compartir registros para diagnóstico",()->Tools.shareLog(requireContext()));
  row("Instalador Java","Ejecutar un instalador .jar",()->{if(ProgressLayout.ensureNoOngoingTasks(requireContext()))installer.launch(null);});
  hint("Cubo es un derivado de MojoLauncher. Las pantallas de instalación, autenticación y ajustes detallados conservan por ahora el diseño del proyecto base.");
 }
 private void diagnostics(){
  final Context context=requireContext().getApplicationContext();final int generation=screenGeneration;
  final long listingTime=lastListingMs;final int listingCount=lastListingCount;
  if(screenTask!=null)screenTask.cancel(true);
  screenTask=MojoApplication.sExecutorService.submit(()->{
   try {
    android.app.ActivityManager manager=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
    android.app.ActivityManager.MemoryInfo memory=new android.app.ActivityManager.MemoryInfo();manager.getMemoryInfo(memory);
    Runtime runtime=Runtime.getRuntime();long mb=1024L*1024L;
    StringBuilder report=new StringBuilder("CUBO · DIAGNÓSTICO LOCAL\n");
    report.append("Versión: ").append(BuildConfig.VERSION_NAME).append("\n");
    report.append("Dispositivo: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
    report.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
    report.append("RAM total del sistema: ").append(memory.totalMem/mb).append(" MB\n");
    report.append("RAM disponible del sistema: ").append(memory.availMem/mb).append(" MB\n");
    report.append("Sistema con poca memoria: ").append(memory.lowMemory?"sí":"no").append("\n");
    report.append("Heap Java usado de este proceso: ").append((runtime.totalMemory()-runtime.freeMemory())/mb).append(" MB\n");
    report.append("Límite de heap de este proceso: ").append(runtime.maxMemory()/mb).append(" MB\n");
    report.append("Espacio utilizable en almacenamiento de la app: ").append(context.getFilesDir().getUsableSpace()/mb).append(" MB\n");
    if(android.os.Build.VERSION.SDK_INT>=29){android.os.PowerManager power=(android.os.PowerManager)context.getSystemService(Context.POWER_SERVICE);report.append("Estado térmico Android (0 sin restricción, 6 apagado): ").append(power.getCurrentThermalStatus()).append("\n");}
    if(listingTime>=0)report.append("Última lectura y ordenación de carpeta: ").append(listingTime).append(" ms; ").append(listingCount).append(" elementos\n");
    report.append("\nNo mide FPS ni memoria nativa de Minecraft. El tiempo de carpeta no incluye el dibujado de la interfaz.\nSin cuentas, tokens, nombres de archivos ni rutas personales. No se envía automáticamente.");
    final String result=report.toString();
    Tools.runOnUiThread(()->{if(!validScreen(generation))return;
     TextView content=text(result,14,WHITE);content.setPadding(dp(20),dp(12),dp(20),dp(12));content.setTextIsSelectable(true);
     ScrollView scroll=new ScrollView(requireContext());scroll.addView(content);
     new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Diagnóstico de Cubo").setView(scroll).setNegativeButton("Cerrar",null).setPositiveButton("Compartir",(d,w)->{
      Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");share.putExtra(Intent.EXTRA_TEXT,result);share.putExtra(Intent.EXTRA_SUBJECT,"Diagnóstico de Cubo");startActivity(Intent.createChooser(share,"Compartir diagnóstico"));
     }).show();
    });
   }catch(Exception e){Tools.runOnUiThread(()->{if(validScreen(generation))message("No se pudo obtener el diagnóstico en este dispositivo.");});}
  });
 }
 private static class NavIcon extends View {
  private final Paint p=new Paint(3);private final int kind,color;
  NavIcon(Context c,int k,int color){super(c);kind=k;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);Path path=new Path();
   if(kind==0){path.moveTo(3,11);path.lineTo(12,3);path.lineTo(21,11);path.moveTo(6,9);path.lineTo(6,21);path.lineTo(10,21);path.lineTo(10,15);path.lineTo(14,15);path.lineTo(14,21);path.lineTo(18,21);path.lineTo(18,9);canvas.drawPath(path,p);}
   else if(kind==1){path.moveTo(3,8);path.lineTo(12,3);path.lineTo(21,8);path.lineTo(12,13);path.close();path.moveTo(3,13);path.lineTo(12,18);path.lineTo(21,13);path.moveTo(3,18);path.lineTo(12,23);path.lineTo(21,18);canvas.drawPath(path,p);}
   else if(kind==2){canvas.drawRoundRect(4,6,20,21,3,3,p);canvas.drawCircle(12,5,3,p);canvas.drawLine(9,14,15,14,p);canvas.drawLine(12,11,12,17,p);}
   else if(kind==3){path.moveTo(3,7);path.lineTo(3,20);path.lineTo(21,20);path.lineTo(21,7);path.lineTo(11,7);path.lineTo(9,4);path.lineTo(3,4);path.close();canvas.drawPath(path,p);}
   else {for(int x=4;x<=18;x+=7)for(int y=4;y<=18;y+=7)canvas.drawRoundRect(x,y,x+3,y+3,0.5f,0.5f,p);}
   canvas.restore();
  }
 }
 @Override public void onDestroyView(){screenGeneration++;cancelScreenWork();if(pendingListing!=null)pendingListing.cancel(true);super.onDestroyView();body=null;nav=null;}
}
