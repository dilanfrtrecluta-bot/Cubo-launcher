package git.artdeell.mojo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import com.kdt.mcgui.ProgressLayout;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.modding.fabric.FabriclikeUtils;
import git.artdeell.mojo.prefs.LauncherPreferences;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/** Opt-in isolated profile; never rewrites user instances or global renderer preferences. */
public final class CuboOptimizedProfile {
 private static final String NAME="Cubo Optimizado 1.20.1";
 private static final String OFFER="cubo.optimized.offer.v1";
 private static final AtomicBoolean BUSY=new AtomicBoolean(false);
 private static final AtomicBoolean PROMPT=new AtomicBoolean(false);
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final String[][] MODS={
  {"lithium-fabric-mc1.20.1-0.11.4.jar","https://cdn.modrinth.com/data/gvQqBUqZ/versions/iEcXOkz4/lithium-fabric-mc1.20.1-0.11.4.jar","31938b7e849609892ffa1710e41f2e163d11876f824452540658c4b53cd13c666dbdad8d200989461932bd9952814c5943e64252530c72bdd5d8641775151500"},
  {"ferritecore-6.0.1-fabric.jar","https://cdn.modrinth.com/data/uXXizFIs/versions/unerR5MN/ferritecore-6.0.1-fabric.jar","9b7dc686bfa7937815d88c7bbc6908857cd6646b05e7a96ddbdcada328a385bd4ba056532cd1d7df9d2d7f4265fd48bd49ff683f217f6d4e817177b87f6bc457"}
 };
 private CuboOptimizedProfile(){}
 private static boolean alive(FragmentActivity a){return a!=null&&!a.isFinishing()&&!a.isDestroyed()&&a.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED);}
 public static void offer(FragmentActivity activity,boolean firstRun,Runnable refreshed){
  if(!alive(activity))return;
  if(firstRun&&LauncherPreferences.DEFAULT_PREF.getBoolean(OFFER,false))return;
  if(BUSY.get()){android.widget.Toast.makeText(activity,"El perfil ya se está preparando.",android.widget.Toast.LENGTH_LONG).show();return;}
  if(!PROMPT.compareAndSet(false,true))return;
  try {
   AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Cubo Optimizado · beta")
    .setMessage("Instancia independiente de Minecraft 1.20.1 + Fabric.\n\nIncluye Lithium 0.11.4 (lógica del juego) y FerriteCore 6.0.1 (memoria). Descarga inicial de mods: aproximadamente 0,8 MB; Minecraft, Java y bibliotecas se descargan aparte al jugar.\n\nNo incluye Sodium ni fuerza drivers. Usa tus ajustes gráficos globales y valores moderados dentro del juego: 6 chunks, simulación 4 y límite de 60 FPS.\n\nNo modifica tus mundos ni instancias existentes. No hay una mejora garantizada de FPS. Necesita Java 17 compatible, conexión y una cuenta válida para jugar.")
    .setPositiveButton("Preparar perfil",(d,w)->install(activity,refreshed))
    .setNegativeButton("Ahora no",null)
    .setNeutralButton("Créditos",(d,w)->credits(activity))
    .create();
   dialog.setOnDismissListener(d->PROMPT.set(false));
   dialog.show();LauncherPreferences.DEFAULT_PREF.edit().putBoolean(OFFER,true).apply();
  }catch(RuntimeException e){PROMPT.set(false);throw e;}
 }
 private static void credits(FragmentActivity a){
  new AlertDialog.Builder(a).setTitle("Mods y fuentes")
   .setMessage("Lithium 0.11.4 · LGPL-3.0-only\nhttps://modrinth.com/mod/lithium\nCódigo: https://github.com/CaffeineMC/lithium-fabric\n\nFerriteCore 6.0.1 · MIT\nhttps://modrinth.com/mod/ferrite-core\nCódigo: https://github.com/malte0811/FerriteCore\n\nFabric Loader 0.16.14\nhttps://fabricmc.net/\n\nLos JAR no se incluyen en el APK: se descargan sin modificar desde Modrinth y se verifican con SHA-512. Minecraft no se incluye. Perfil beta pendiente de pruebas en dispositivos reales.")
   .setPositiveButton("Cerrar",null).show();
 }
 private static void install(FragmentActivity activity,Runnable refreshed){
  if(!ProgressLayout.ensureNoOngoingTasks(activity)||!BUSY.compareAndSet(false,true))return;
  Context app=activity.getApplicationContext();WeakReference<FragmentActivity> owner=new WeakReference<>(activity);
  android.widget.Toast.makeText(activity,"Preparando perfil en segundo plano. No cierres Cubo hasta terminar.",android.widget.Toast.LENGTH_LONG).show();
  MojoApplication.sExecutorService.execute(()->{
   Instance result=null,created=null;String error=null;boolean existing=false;
   File stage=new File(app.getCacheDir(),"cubo-profile-"+UUID.randomUUID());
   try {
    for(Instance candidate:Instances.loadAllInstances()){
     if(NAME.equals(candidate.name)&&!candidate.sharedData&&"fabric-loader-0.16.14-1.20.1".equals(candidate.versionId)){result=candidate;existing=true;break;}
    }
    if(result==null){
     if(!stage.mkdirs())throw new IOException("No se pudo crear el directorio temporal.");
     for(String[] mod:MODS)download(mod,new File(stage,mod[0]));
     String version=FabriclikeUtils.FABRIC_UTILS.install("1.20.1","0.16.14");
     if(version==null||!"fabric-loader-0.16.14-1.20.1".equals(version))throw new IOException("No se pudo verificar la instalación de Fabric.");
     created=Instances.createInstance(instance->{instance.name=NAME;instance.icon="fabric";instance.versionId=version;instance.sharedData=false;},"cubo-optimized-1.20.1");
     File modsDir=new File(created.getGameDirectory(),"mods");
     if(!modsDir.mkdirs())throw new IOException("No se pudo crear la carpeta de mods.");
     for(String[] mod:MODS)copy(new File(stage,mod[0]),new File(modsDir,mod[0]));
     write(new File(created.getGameDirectory(),"options.txt"),"renderDistance:6\nsimulationDistance:4\nmaxFps:60\nenableVsync:true\ngraphicsMode:0\nrenderClouds:false\nparticles:1\n");
     write(new File(created.getGameDirectory(),"CUBO-PERFIL.txt"),"Cubo Optimizado beta - Minecraft 1.20.1 / Fabric Loader 0.16.14\nLithium 0.11.4 (LGPL-3.0-only): https://modrinth.com/mod/lithium\nFerriteCore 6.0.1 (MIT): https://modrinth.com/mod/ferrite-core\nNo sustituye una licencia de Minecraft. No garantiza mayor rendimiento en todos los dispositivos.\nJava 17 requerido. No se ha cambiado el renderizador ni la RAM global.\n");
     result=created;created=null;
    }
   }catch(Exception e){
    error="No se pudo preparar el perfil. Comprueba conexión, espacio y vuelve a intentarlo. Detalle: "+e.getClass().getSimpleName();
    if(created!=null){try{Instances.removeInstance(created);}catch(IOException ignored){error+=" Puede quedar una instancia incompleta nueva; revísala desde Versiones.";}}
   }finally{File[] files=stage.listFiles();if(files!=null)for(File file:files)file.delete();stage.delete();BUSY.set(false);}
   final Instance profile=result;final String failure=error;final boolean found=existing;
   MAIN.post(()->{
    FragmentActivity a=owner.get();
    if(!alive(a)){
     android.widget.Toast.makeText(app,failure==null?"Perfil Cubo Optimizado preparado. Búscalo en Versiones.":failure,android.widget.Toast.LENGTH_LONG).show();return;
    }
    if(failure!=null){new AlertDialog.Builder(a).setTitle("Instalación no completada").setMessage(failure+"\nTus instancias anteriores no se han modificado.").setPositiveButton("Cerrar",null).show();return;}
    new AlertDialog.Builder(a).setTitle(found?"El perfil ya existe":"Perfil preparado")
     .setMessage(found?"No se ha sobrescrito el perfil existente. Puedes seleccionarlo o revisarlo en Versiones.":"Mods descargados y verificados. Al jugar se descargarán los archivos de Minecraft y sus bibliotecas. Comprueba que Java 17 esté disponible.\n\n¿Seleccionar Cubo Optimizado ahora?")
     .setPositiveButton("Usar perfil",(d,w)->{Instances.setSelectedInstance(profile);if(refreshed!=null)refreshed.run();})
     .setNegativeButton("Después",null).show();
   });
  });
 }
 private static void download(String[] mod,File target)throws Exception{
  URL url=new URL(mod[1]);if(!"https".equals(url.getProtocol())||!"cdn.modrinth.com".equals(url.getHost()))throw new IOException("Origen no permitido");
  HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setRequestProperty("User-Agent","CuboLauncher/optimized-profile-beta");
  try {
   if(connection.getResponseCode()!=200)throw new IOException("Descarga no disponible");
   MessageDigest digest=MessageDigest.getInstance("SHA-512");long size=0;
   try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(target)){
    byte[] buffer=new byte[32768];int n;
    while((n=in.read(buffer))!=-1){size+=n;if(size>16L*1024*1024)throw new IOException("Archivo demasiado grande");digest.update(buffer,0,n);out.write(buffer,0,n);}
   }
   StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
   if(!mod[2].equals(hex.toString()))throw new IOException("SHA-512 incorrecto");
   try(ZipFile zip=new ZipFile(target)){if(zip.getEntry("fabric.mod.json")==null)throw new IOException("JAR inválido");}
  }finally{connection.disconnect();}
 }
 private static void copy(File from,File to)throws IOException{
  if(!to.createNewFile())throw new IOException("El archivo ya existe");
  try(InputStream in=new FileInputStream(from);OutputStream out=new FileOutputStream(to)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
 }
 private static void write(File file,String content)throws IOException{try(Writer writer=new OutputStreamWriter(new FileOutputStream(file),"UTF-8")){writer.write(content);}}
}
