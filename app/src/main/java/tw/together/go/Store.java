package tw.together.go;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

public class Store {
    public final Context context;
    public final List<Place> places=new ArrayList<>();
    public final Map<String,Map<String,Object>> records=new HashMap<>();
    public final Map<String,Map<String,Object>> lists=new LinkedHashMap<>();
    public String listId="demo", listName="週末出走小隊", admin="demo-user";
    public Runnable changed=()->{};
    public Consumer<String> error=s->{};
    private final List<ListenerRegistration> subscriptions=new ArrayList<>();
    private ListenerRegistration listsListener;
    private boolean active=false;
    public Store(Context c){context=c; loadLocal();}
    public boolean configured(){return !FirebaseApp.getApps(context).isEmpty();}
    public boolean cloud(){return configured() && FirebaseAuth.getInstance().getCurrentUser()!=null;}
    public String uid(){return cloud()?FirebaseAuth.getInstance().getCurrentUser().getUid():"demo-user";}
    public String userName(){return cloud()?Objects.toString(FirebaseAuth.getInstance().getCurrentUser().getDisplayName(),"朋友"):"我（體驗模式）";}
    public boolean isAdmin(){return admin.equals(uid());}
    public boolean canDelete(Place p){return isAdmin()||uid().equals(p.creator);}
    FirebaseFirestore db(){return FirebaseFirestore.getInstance();}
    DocumentReference group(){return db().collection("lists").document(listId);}
    void fail(Exception e){error.accept("操作未完成，請確認網路與權限後重試。" );}
    public void start(){
        active=true;
        if(!cloud()){changed.run();return;}
        if(listsListener!=null)listsListener.remove();
        listsListener=db().collection("lists").whereArrayContains("members",uid()).addSnapshotListener((snap,e)->{
            if(e!=null){fail(e);return;} if(snap==null)return;
            lists.clear(); for(DocumentSnapshot d:snap.getDocuments())lists.put(d.getId(),d.getData());
            if(lists.isEmpty()){stopGroup();listId="";listName="尚未加入清單";admin="";places.clear();records.clear();changed.run();}
            else if(!lists.containsKey(listId)){select(lists.keySet().iterator().next());}
            else {Map<String,Object> m=lists.get(listId); listName=Place.s(m,"name");admin=Place.s(m,"admin");changed.run();}
        });
        if(!listId.isEmpty()&&!listId.equals("demo"))listenGroup();
    }
    public void stop(){active=false;stopGroup();if(listsListener!=null){listsListener.remove();listsListener=null;}}
    private void stopGroup(){for(ListenerRegistration s:subscriptions)s.remove();subscriptions.clear();}
    public void select(String id){stopGroup();listId=id;places.clear();records.clear();Map<String,Object> m=lists.get(id);if(m!=null){listName=Place.s(m,"name");admin=Place.s(m,"admin");}if(active&&cloud())listenGroup();changed.run();}
    private void listenGroup(){
        stopGroup(); if(listId.isEmpty())return;
        subscriptions.add(group().collection("places").addSnapshotListener((s,e)->{if(e!=null){fail(e);return;}places.clear();if(s!=null)for(DocumentSnapshot d:s.getDocuments()){Place p=Place.from(d.getData());String raw=context.getSharedPreferences("attachments",0).getString(listId+"_"+p.id,"");if(!raw.isEmpty())p.images=new ArrayList<>(Arrays.asList(raw.split("\\n")));places.add(p);}changed.run();}));
        subscriptions.add(group().collection("records").addSnapshotListener((s,e)->{if(e!=null){fail(e);return;}records.clear();if(s!=null)for(DocumentSnapshot d:s.getDocuments())records.put(d.getId(),d.getData());changed.run();}));
    }
    public void createList(String name,Runnable done){
        if(!cloud()){error.accept("請先登入 Google，才能建立共用清單。");return;}
        DocumentReference ref=db().collection("lists").document();
        Map<String,Object> m=new HashMap<>();m.put("name",name);m.put("admin",uid());m.put("members",Collections.singletonList(uid()));m.put("createdAt",System.currentTimeMillis());
        ref.set(m).addOnSuccessListener(v->{lists.put(ref.getId(),m);select(ref.getId());done.run();}).addOnFailureListener(this::fail);
    }
    public void save(Place p,Runnable done){
        saveWithNote(p,"",done);
    }
    public void saveWithNote(Place p,String note,Runnable done){
        if(cloud()&&listId.isEmpty()){error.accept("請先建立或加入清單。");return;}
        long expected=p.updatedAt;
        p.updatedAt=System.currentTimeMillis();
        if(cloud()){
            String savingList=listId, author=uid(), displayName=userName();
            DocumentReference target=group().collection("places").document(p.id), recordRef=group().collection("records").document(p.id+"_"+author);
            db().runTransaction(t->{
                DocumentSnapshot current=t.get(target);
                if(current.exists()&&current.getLong("updatedAt")!=expected)throw new IllegalStateException("conflict");
                DocumentSnapshot prior=note.isBlank()?null:t.get(recordRef);
                t.set(target,p.map());
                if(prior!=null){Map<String,Object> r=new HashMap<>();String old=prior.exists()?Objects.toString(prior.getString("text"),""):"";r.put("uid",author);r.put("placeId",p.id);r.put("name",displayName);r.put("text",old.isBlank()?note:old+"\n"+note);r.put("visited",prior.exists()&&Boolean.TRUE.equals(prior.getBoolean("visited")));r.put("again",prior.exists()&&Boolean.TRUE.equals(prior.getBoolean("again")));r.put("updatedAt",p.updatedAt);t.set(recordRef,r);}
                return null;
            }).addOnSuccessListener(v->{context.getSharedPreferences("attachments",0).edit().putString(savingList+"_"+p.id,String.join("\n",p.images)).apply();done.run();}).addOnFailureListener(e->{p.updatedAt=expected;error.accept("儲存未完成。可能有朋友剛更新此地點，或目前沒有網路。請重新開啟地點確認後再試。");});
        }
        else {places.removeIf(x->x.id.equals(p.id));places.add(p);if(!note.isBlank()){Map<String,Object> r=records.get(p.id+"_"+uid());String old=r==null?"":Place.s(r,"text");record(p,old.isBlank()?note:old+"\n"+note,r!=null&&Boolean.TRUE.equals(r.get("visited")),r!=null&&Boolean.TRUE.equals(r.get("again")),()->{});}persist();done.run();}
    }
    public void delete(Place p,Runnable done){if(!canDelete(p)){error.accept("僅新增者與管理員可以刪除。");return;}if(cloud())group().collection("places").document(p.id).delete().addOnSuccessListener(v->done.run()).addOnFailureListener(this::fail);else{places.removeIf(x->x.id.equals(p.id));persist();done.run();}}
    public void record(Place p,String text,boolean visited,boolean again,Runnable done){
        Map<String,Object> m=new HashMap<>();m.put("uid",uid());m.put("placeId",p.id);m.put("name",userName());m.put("text",text);m.put("visited",visited);m.put("again",again);m.put("updatedAt",System.currentTimeMillis());
        String key=p.id+"_"+uid();
        if(cloud())group().collection("records").document(key).set(m).addOnSuccessListener(v->done.run()).addOnFailureListener(this::fail);else{records.put(key,m);persist();done.run();}
    }
    public void join(String id,Runnable done){
        if(!cloud()){error.accept("請先登入 Google。");return;}
        Map<String,Object> m=new HashMap<>();m.put("uid",uid());m.put("name",userName());m.put("createdAt",System.currentTimeMillis());
        db().collection("lists").document(id).collection("requests").document(uid()).set(m).addOnSuccessListener(v->done.run()).addOnFailureListener(this::fail);
    }
    public void requests(Consumer<List<DocumentSnapshot>> done){group().collection("requests").get().addOnSuccessListener(s->done.accept(s.getDocuments())).addOnFailureListener(this::fail);}
    public void decide(String user,boolean approve,Runnable done){
        WriteBatch batch=db().batch();if(approve)batch.update(group(),"members",FieldValue.arrayUnion(user));batch.delete(group().collection("requests").document(user));batch.commit().addOnSuccessListener(v->done.run()).addOnFailureListener(this::fail);
    }
    public String notes(Place p){StringBuilder b=new StringBuilder();for(Map<String,Object> r:records.values())if(p.id.equals(r.get("placeId")))b.append(Place.s(r,"text"));return b.toString();}
    public void localMode(){stop();listId="demo";listName="週末出走小隊";admin="demo-user";places.clear();records.clear();loadLocal();}
    private void loadLocal(){
        String raw=context.getSharedPreferences("local-demo",0).getString("data","");
        if(raw.isEmpty()){seed();return;}
        try {JSONObject o=new JSONObject(raw);JSONArray a=o.getJSONArray("places");for(int i=0;i<a.length();i++){Map<String,Object> m=jsonMap(a.getJSONObject(i));Place p=Place.from(m);p.images=Place.list(m.get("images"));places.add(p);}JSONObject rs=o.getJSONObject("records");Iterator<String> keys=rs.keys();while(keys.hasNext()){String k=keys.next();records.put(k,jsonMap(rs.getJSONObject(k)));}}catch(Exception e){error.accept("本機資料讀取失敗，請保留資料並重啟。");}
    }
    static Map<String,Object> jsonMap(JSONObject o)throws JSONException{Map<String,Object> m=new HashMap<>();Iterator<String> it=o.keys();while(it.hasNext()){String k=it.next();Object v=o.get(k);if(v instanceof JSONArray){List<String> l=new ArrayList<>();for(int i=0;i<((JSONArray)v).length();i++)l.add(((JSONArray)v).getString(i));v=l;}m.put(k,v);}return m;}
    private void persist(){JSONArray a=new JSONArray();for(Place p:places){Map<String,Object> m=p.map();m.put("images",p.images);a.put(new JSONObject(m));}JSONObject o=new JSONObject();try{o.put("places",a);JSONObject r=new JSONObject();for(String k:records.keySet())r.put(k,new JSONObject(records.get(k)));o.put("records",r);context.getSharedPreferences("local-demo",0).edit().putString("data",o.toString()).apply();}catch(JSONException e){error.accept("本機儲存失敗。");}}
    private void seed(){
        sample("山邊的草莓冰店","宜蘭縣","宜蘭市","美食","草莓冰、老屋與午後陽光。示範地點，非真實店家資訊。");
        sample("海風散步路線","宜蘭縣","頭城鎮","景點","找個不趕時間的下午去看海。示範收藏。");
        sample("森林裡的小屋","南投縣","魚池鄉","住宿","下次連假一起住一晚。示範收藏。");
        sample("週末手作市集","臺北市","大同區","活動","想去逛逛陶器和小攤。示範收藏。");
        Place p=new Place();p.name="朋友分享的新口袋名單";p.creator=uid();p.creatorName="我";p.evidence="這是待補資料示範，補上名稱、地區和分類就能完成整理。";places.add(p);persist();
    }
    private void sample(String name,String city,String district,String category,String text){Place p=new Place();p.name=name;p.city=city;p.district=district;p.categories.add(category);p.creator="demo-user";p.creatorName="我";p.pending=false;p.evidence=text;places.add(p);}
}
