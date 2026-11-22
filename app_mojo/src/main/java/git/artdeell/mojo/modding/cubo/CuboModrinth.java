package git.artdeell.mojo.modding.cubo;

import android.content.Context;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.tasks.mojson.MoJsonExtras;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;

/** Bounded Modrinth client. No background polling and no global game-setting changes. */
public final class CuboModrinth {
 public static final AtomicBoolean INSTALLING=new AtomicBoolean(false);
 private static final String API="https://api.modrinth.com/v2/";
 public interface Progress {void update(String text);}
 public static final class Target {
  public final File root;public final String minecraft,loader,name;
  private Target(File root,String mc,String loader,String name){this.root=root;this.minecraft=mc;this.loader=loader;this.name=name;}
 }
 private CuboModrinth(){}
 public static Target target(Instance instance)throws Exception{
  if(instance==null)throw new IOException("Selecciona una instancia primero.");
  String id=MoJsonExtras.normalizeVersionId(instance.versionId), mc=id, loader=null;
  HashSet<String> seen=new HashSet<>();
  for(int depth=0;depth<8&&id!=null&&seen.add(id);depth++){
   if(!id.matches("[A-Za-z0-9._+\\-]+"))throw new IOException("Identificador de versión no válido.");
   File meta=new File(new File(Tools.DIR_HOME_VERSION,id),id+".json");
   String evidence=id;
   JSONObject obj=null;
   if(meta.isFile()){obj=new JSONObject(readSmall(meta));evidence+=" "+obj.optString("mainClass")+" "+obj.optJSONArray("libraries");}
   evidence=evidence.toLowerCase(Locale.ROOT);
   if(loader==null){if(evidence.contains("neoforge"))loader="neoforge";else if(evidence.contains("quilt"))loader="quilt";else if(evidence.contains("fabric"))loader="fabric";else if(evidence.contains("forge"))loader="forge";}
   String parent=obj==null?null:obj.optString("inheritsFrom",null);
   if(parent==null||parent.isEmpty()){mc=id;break;}mc=parent;id=parent;
  }
  if(loader==null)throw new IOException("La instancia necesita Fabric, Forge, NeoForge o Quilt para instalar mods. Crea una versión con cargador desde Versiones.");
  if(!mc.matches("[0-9][0-9A-Za-z._+\\-]*"))throw new IOException("No se pudo identificar Minecraft con seguridad. Completa primero la instalación de la instancia.");
  return new Target(instance.getGameDirectory().getCanonicalFile(),mc,loader,instance.name);
 }
 private static String readSmall(File f)throws IOException{try(InputStream in=new FileInputStream(f)){return read(in,4*1024*1024);}}
 private static String read(InputStream in,int max)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
  while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>max)throw new IOException("Respuesta demasiado grande.");out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);
 }
 public static String encode(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
 private static String request(String path,JSONObject body)throws Exception{
  HttpsURLConnection c=(HttpsURLConnection)new URL(API+path).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","CuboLauncher/Modrinth-beta (github.com/dilanfrtrecluta-bot/Cubo-launcher)");
  try{
   if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
   int code=c.getResponseCode();if(code==429)throw new IOException("Modrinth ha limitado las consultas. Espera un poco antes de reintentar.");if(code!=200)throw new IOException("Modrinth respondió HTTP "+code+".");
   try(InputStream in=c.getInputStream()){return read(in,4*1024*1024);}
  }finally{c.disconnect();}
 }
 private static void identifier(String id)throws IOException{if(id==null||!id.matches("[A-Za-z0-9_-]+"))throw new IOException("Identificador de Modrinth no válido.");}
 public static JSONObject search(Target t,String query,int offset,boolean optimize)throws Exception{
  JSONArray facets=new JSONArray();facets.put(new JSONArray().put("project_type:mod"));facets.put(new JSONArray().put("versions:"+t.minecraft));facets.put(new JSONArray().put("categories:"+t.loader));if(optimize)facets.put(new JSONArray().put("categories:optimization"));
  return new JSONObject(request("search?limit=20&index=downloads&offset="+offset+"&query="+encode(query)+"&facets="+encode(facets.toString()),null));
 }
 public static JSONArray versions(String project,Target t)throws Exception{
  identifier(project);return new JSONArray(request("project/"+project+"/version?loaders="+encode(new JSONArray().put(t.loader).toString())+"&game_versions="+encode(new JSONArray().put(t.minecraft).toString()),null));
 }
 public static JSONObject project(String id)throws Exception{identifier(id);return new JSONObject(request("project/"+id,null));}
 private static JSONObject version(String id)throws Exception{identifier(id);return new JSONObject(request("version/"+id,null));}
 private static boolean contains(JSONArray a,String value){if(a==null)return false;for(int i=0;i<a.length();i++)if(value.equals(a.optString(i)))return true;return false;}
 private static JSONObject file(JSONObject v)throws Exception{
  JSONArray files=v.getJSONArray("files");JSONObject fallback=null;
  for(int i=0;i<files.length();i++){JSONObject f=files.getJSONObject(i);String name=f.getString("filename");if(!name.toLowerCase(Locale.ROOT).endsWith(".jar"))continue;if(f.optBoolean("primary"))return f;fallback=f;}
  if(fallback==null)throw new IOException("Esta versión no ofrece un JAR instalable.");return fallback;
 }
 private static void checkVersion(JSONObject v,Target t)throws Exception{
  if(!contains(v.optJSONArray("game_versions"),t.minecraft)||!contains(v.optJSONArray("loaders"),t.loader))throw new IOException("Mod o dependencia incompatible con "+t.minecraft+" / "+t.loader+".");
  JSONObject env=v.optJSONObject("env");if(env!=null&&"unsupported".equals(env.optString("client")))throw new IOException("Esta dependencia no es para el cliente.");
 }
 private static void resolve(JSONObject v,Target t,LinkedHashMap<String,JSONObject> plan,AtomicBoolean cancel)throws Exception{
  cancelled(cancel);checkVersion(v,t);String project=v.getString("project_id");
  if(plan.containsKey(project)){if(!plan.get(project).getString("id").equals(v.getString("id")))throw new IOException("Dos dependencias requieren versiones distintas del mismo proyecto.");return;}
  if(plan.size()>=32)throw new IOException("Demasiadas dependencias para esta instalación. Instálalas por separado.");plan.put(project,v);
  JSONArray deps=v.optJSONArray("dependencies");if(deps==null)return;
  for(int i=0;i<deps.length();i++){
   JSONObject d=deps.getJSONObject(i);if(!"required".equals(d.optString("dependency_type")))continue;
   JSONObject dependency;String exact=d.optString("version_id","");
   if(!exact.isEmpty()&&!"null".equals(exact))dependency=version(exact);
   else {String pid=d.optString("project_id","");if(pid.isEmpty()||"null".equals(pid))throw new IOException("Dependencia obligatoria sin identificador descargable.");JSONArray options=versions(pid,t);dependency=null;
    for(int j=0;j<options.length();j++)if("release".equals(options.getJSONObject(j).optString("version_type"))){dependency=options.getJSONObject(j);break;}
    if(dependency==null)throw new IOException("No hay una dependencia estable compatible disponible.");}
   resolve(dependency,t,plan,cancel);
  }
 }
 private static void cancelled(AtomicBoolean cancel)throws InterruptedIOException{if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Instalación cancelada.");}
 private static String hash(File f,AtomicBoolean cancel)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-512");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){cancelled(cancel);digest.update(b,0,n);}}
  StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
 }
 private static Map<String,JSONObject> installed(File dir,AtomicBoolean cancel)throws Exception{
  LinkedHashMap<String,JSONObject> result=new LinkedHashMap<>();File[] files=dir.listFiles(f->f.isFile()&&f.getName().endsWith(".jar"));if(files==null||files.length==0)return result;
  if(files.length>512)throw new IOException("La instancia tiene más de 512 mods; revisa las dependencias manualmente antes de instalar.");JSONArray hashes=new JSONArray();
  for(File f:files){cancelled(cancel);hashes.put(hash(f,cancel));}
  JSONObject found=new JSONObject(request("version_files",new JSONObject().put("hashes",hashes).put("algorithm","sha512")));
  Iterator<String> keys=found.keys();while(keys.hasNext()){JSONObject v=found.getJSONObject(keys.next());String pid=v.getString("project_id");if(result.containsKey(pid)&&!result.get(pid).getString("id").equals(v.getString("id")))throw new IOException("Hay dos versiones instaladas del mismo mod. Resuélvelo antes de continuar.");result.put(pid,v);}return result;
 }
 private static void conflicts(JSONObject v,Map<String,JSONObject> all)throws Exception{
  JSONArray deps=v.optJSONArray("dependencies");if(deps==null)return;
  for(int i=0;i<deps.length();i++){JSONObject d=deps.getJSONObject(i);if(!"incompatible".equals(d.optString("dependency_type")))continue;
   String pid=d.optString("project_id","");String vid=d.optString("version_id","");
   if(!pid.isEmpty()&&!"null".equals(pid)&&all.containsKey(pid)){
    if(vid.isEmpty()||"null".equals(vid)||vid.equals(all.get(pid).optString("id")))throw new IOException("Modrinth declara una incompatibilidad con otro mod de esta instancia.");
   }else if(!vid.isEmpty()&&!"null".equals(vid))for(JSONObject other:all.values())if(vid.equals(other.optString("id")))throw new IOException("Modrinth declara una incompatibilidad de versiones.");
  }
 }
 private static void download(JSONObject info,File dest,AtomicBoolean cancel)throws Exception{
  URL url=new URL(info.getString("url"));if(!"https".equals(url.getProtocol())||!"cdn.modrinth.com".equals(url.getHost()))throw new IOException("Origen de descarga no permitido.");
  String expected=info.getJSONObject("hashes").getString("sha512");if(!expected.matches("[a-fA-F0-9]{128}"))throw new IOException("Falta una huella SHA-512 válida.");
  HttpsURLConnection c=(HttpsURLConnection)url.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","CuboLauncher/Modrinth-beta");
  try {if(c.getResponseCode()!=200)throw new IOException("No se pudo descargar el archivo.");long size=0;try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(dest)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){cancelled(cancel);size+=n;if(size>64L*1024*1024)throw new IOException("Mod demasiado grande para el instalador móvil (64 MB).");out.write(b,0,n);}}
   if(info.has("size")&&size!=info.getLong("size"))throw new IOException("Descarga incompleta.");if(!expected.equalsIgnoreCase(hash(dest,cancel)))throw new IOException("La verificación SHA-512 falló.");
   try(java.util.zip.ZipFile jar=new java.util.zip.ZipFile(dest)){if(jar.size()==0)throw new IOException("El JAR está vacío.");}
  }finally{c.disconnect();}
 }
 /** All downloads verified before commit. Never replaces an existing file or a different installed version. */
 public static int install(Context app,Target target,JSONObject selected,AtomicBoolean cancel,Progress progress)throws Exception{
  File stage=null;ArrayList<File> committed=new ArrayList<>();boolean success=false;
  try {
   progress.update("Comprobando dependencias…");LinkedHashMap<String,JSONObject> plan=new LinkedHashMap<>();resolve(selected,target,plan,cancel);
   File mods=new File(target.root,"mods");if(mods.exists()&&!mods.isDirectory())throw new IOException("La ruta mods no es una carpeta.");
   Map<String,JSONObject> existing=installed(mods,cancel);Map<String,JSONObject> all=new HashMap<>(existing);
   for(Map.Entry<String,JSONObject> entry:plan.entrySet()){
    if(existing.containsKey(entry.getKey())&&!existing.get(entry.getKey()).getString("id").equals(entry.getValue().getString("id")))throw new IOException("Ya hay otra versión de un mod requerido. No se reemplaza automáticamente.");all.put(entry.getKey(),entry.getValue());
   }
   for(JSONObject v:all.values())conflicts(v,all);
   if(!target.root.isDirectory())throw new IOException("La carpeta de la instancia ya no existe.");
   stage=new File(target.root,".cubo-modrinth-"+UUID.randomUUID());if(!stage.mkdir())throw new IOException("No se pudo crear el directorio temporal.");
   LinkedHashMap<File,File> moves=new LinkedHashMap<>();HashSet<String> names=new HashSet<>();long total=0;int index=0;
   for(Map.Entry<String,JSONObject> entry:plan.entrySet()){
    cancelled(cancel);if(existing.containsKey(entry.getKey()))continue;JSONObject info=file(entry.getValue());String name=info.getString("filename");
    if(name.contains("/")||name.contains("\\")||name.equals(".")||name.equals("..")||!name.endsWith(".jar"))throw new IOException("Nombre de archivo no seguro.");
    if(!names.add(name))throw new IOException("Dos mods tienen el mismo nombre de archivo.");File dest=new File(mods,name);if(dest.exists())throw new IOException("Ya existe "+name+"; no se sobrescribirá.");
    total+=Math.max(0,info.optLong("size",0));if(total>192L*1024*1024)throw new IOException("El conjunto supera 192 MB. Instala menos mods a la vez.");
    if(stage.getUsableSpace()<total+32L*1024*1024)throw new IOException("No hay suficiente espacio libre.");
    progress.update("Descargando "+(++index)+" / "+plan.size()+": "+name);File temporary=new File(stage,"mod-"+index+".jar");download(info,temporary,cancel);moves.put(temporary,dest);
   }
   cancelled(cancel);progress.update("Instalando archivos verificados…");if(!mods.isDirectory()&&!mods.mkdir())throw new IOException("No se pudo crear la carpeta mods.");
   for(Map.Entry<File,File> move:moves.entrySet()){
    cancelled(cancel);File dest=move.getValue();
    // O_EXCL reserves a new file without replacing an existing mod (also on emulated storage).
    java.io.FileDescriptor fd;
    try{fd=android.system.Os.open(dest.getAbsolutePath(),android.system.OsConstants.O_WRONLY|android.system.OsConstants.O_CREAT|android.system.OsConstants.O_EXCL,0600);}
    catch(android.system.ErrnoException e){throw new IOException("No se pudo crear el mod sin reemplazar archivos existentes.",e);}
    committed.add(dest);
    try(InputStream in=new FileInputStream(move.getKey());OutputStream out=new FileOutputStream(fd)){
     byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){cancelled(cancel);out.write(buffer,0,n);}
    }
   }
   success=true;return committed.size();
  }finally{
   if(!success)for(File f:committed)f.delete();
   if(stage!=null){File[] list=stage.listFiles();if(list!=null)for(File f:list)f.delete();stage.delete();}
  }
 }
}
