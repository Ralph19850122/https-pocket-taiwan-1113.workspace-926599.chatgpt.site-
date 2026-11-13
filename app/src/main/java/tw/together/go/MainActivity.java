package tw.together.go;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.credentials.*;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.DocumentSnapshot;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    final int BG=Color.rgb(247,247,242), INK=Color.rgb(32,54,45), GREEN=Color.rgb(40,100,84), MUTED=Color.rgb(112,122,115), LINE=Color.rgb(223,229,220), PALE=Color.rgb(231,237,224);
    Store store;
    LinearLayout shell,body,content;
    String tab="收藏", query="", city="",district="", category="";
    boolean pendingOnly=false, accepted=false;
    Place selected;
    Place attachmentDraft;
    TextView attachmentLabel;
    final LinkedHashMap<String,String[]> regions=new LinkedHashMap<>();
    final String[] categories={"美食","景點","住宿","活動"};
    String sharedText="";
    final ArrayList<String> sharedImages=new ArrayList<>();
    final Handler handler=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle b){
        super.onCreate(b);store=new Store(this);store.error=this::toast;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(getAssets().open("regions.txt"),java.nio.charset.StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null){String[] a=l.split("\\|");regions.put(a[0],a[1].split(","));}}catch(IOException e){toast("地區資料無法載入");}
        store.changed=()->{if(accepted){if(getCurrentFocus() instanceof EditText)return;if(selected!=null){Place fresh=find(selected.id);if(fresh==null){selected=null;toast("此收藏已被移除。");}else selected=fresh;}render();}};
        if(b!=null){sharedText=b.getString("sharedText","");ArrayList<String> images=b.getStringArrayList("sharedImages");if(images!=null)sharedImages.addAll(images);}
        else receive(getIntent());
        accepted=store.cloud()||getPreferences(0).getBoolean("demoAccepted",false);
        if(store.cloud()){store.places.clear();store.records.clear();store.listId="";store.listName="正在載入共用清單…";store.admin="";}
        if(accepted){store.start();render();openIncoming();}else welcome();
    }
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("sharedText",sharedText);b.putStringArrayList("sharedImages",sharedImages);}
    @Override protected void onStart(){super.onStart();if(store!=null&&accepted)store.start();}
    @Override protected void onStop(){super.onStop();if(store!=null)store.stop();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);receive(i);if(accepted)openIncoming();}
    @Override public void onBackPressed(){if(selected!=null){selected=null;render();}else if(!tab.equals("收藏")){tab="收藏";render();}else super.onBackPressed();}
    void receive(Intent i){
        if(i==null||(!Intent.ACTION_SEND.equals(i.getAction())&&!Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())))return;
        sharedText=Objects.toString(i.getCharSequenceExtra(Intent.EXTRA_TEXT),"");
        if(sharedText.isBlank())sharedText=Objects.toString(i.getStringExtra(Intent.EXTRA_SUBJECT),"");
        ArrayList<Uri> uris=new ArrayList<>();
        if(Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())){ArrayList<Uri> a=i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(a!=null)uris.addAll(a);}else{Uri u=i.getParcelableExtra(Intent.EXTRA_STREAM);if(u!=null)uris.add(u);}
        for(Uri u:uris){if(sharedImages.size()>=5)break;String path=copyImage(u);if(path!=null)sharedImages.add(path);}
    }
    String copyImage(Uri u){
        try{String mime=getContentResolver().getType(u);if(mime==null||!mime.startsWith("image/"))return null;File dir=new File(getFilesDir(),"evidence");dir.mkdirs();File f=new File(dir,UUID.randomUUID()+".img");
            try(InputStream in=getContentResolver().openInputStream(u);OutputStream out=new FileOutputStream(f)){if(in==null)return null;byte[] data=new byte[8192];int n,total=0;while((n=in.read(data))!=-1){total+=n;if(total>12*1024*1024)throw new IOException("Too large");out.write(data,0,n);}}return f.getAbsolutePath();
        }catch(Exception e){toast("圖片無法匯入，請使用 12 MB 以下的圖片。");return null;}
    }
    void openIncoming(){if(!sharedText.isEmpty()||!sharedImages.isEmpty()){handler.post(()->editor(null,sharedText,new ArrayList<>(sharedImages)));}}
    void clearIncoming(){sharedText="";sharedImages.clear();setIntent(new Intent(this,MainActivity.class));}
    Place find(String id){for(Place p:store.places)if(p.id.equals(id))return p;return null;}
    int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1f);if(bold)t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));return t;}
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    void gap(LinearLayout l,int n){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    void add(LinearLayout parent,View v){parent.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    void weighted(LinearLayout parent,View v){parent.addView(v,new LinearLayout.LayoutParams(0,-2,1));}
    TextView action(String label,boolean primary,Runnable run){TextView v=text(label,14,primary?Color.WHITE:GREEN,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(14),dp(14),dp(14),dp(14));v.setMinHeight(dp(48));v.setBackground(bg(primary?GREEN:PALE,16));v.setOnClickListener(w->run.run());v.setContentDescription(label);return v;}
    void pill(LinearLayout l,String title,boolean active,Runnable run){TextView t=action(title,active,run);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,0,dp(8),0);l.addView(t,p);}
    void tag(LinearLayout l,String s){TextView t=text(s,12,GREEN,true);t.setBackground(bg(PALE,7));t.setPadding(dp(8),dp(4),dp(8),dp(4));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,0,dp(6),0);l.addView(t,p);}
    LinearLayout card(LinearLayout parent){LinearLayout c=col();c.setPadding(dp(18),dp(18),dp(18),dp(18));GradientDrawable d=bg(Color.WHITE,22);d.setStroke(dp(1),LINE);c.setBackground(d);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(12));parent.addView(c,lp);return c;}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    void base(){
        shell=col();shell.setBackgroundColor(BG);shell.setFitsSystemWindows(true);setContentView(shell);
        shell.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout header=row();header.setPadding(dp(22),dp(14),dp(22),dp(12));
        TextView brand=text("▣  一起收",22,INK,true);weighted(header,brand);TextView avatar=action(store.cloud()?"已登入":"體驗版",false,()->{tab="我的";selected=null;render();});header.addView(avatar);add(shell,header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=col();body.setPadding(dp(22),dp(10),dp(22),dp(24));scroll.addView(body);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=row();nav.setPadding(dp(14),dp(8),dp(14),dp(8));nav.setBackgroundColor(Color.WHITE);
        for(String t:new String[]{"收藏","待補","清單","我的"}){TextView item=text((t.equals("收藏")?"▣":t.equals("待補")?"◷":t.equals("清單")?"☷":"○")+"\n"+t,12,tab.equals(t)?GREEN:MUTED,tab.equals(t));item.setGravity(Gravity.CENTER);item.setPadding(0,dp(7),0,dp(7));item.setMinHeight(dp(52));item.setOnClickListener(v->{selected=null;tab=t;render();});weighted(nav,item);}add(shell,nav);
    }
    void welcome(){
        accepted=false;shell=col();shell.setBackgroundColor(BG);shell.setPadding(dp(28),dp(56),dp(28),dp(32));shell.setGravity(Gravity.CENTER_VERTICAL);setContentView(shell);
        add(shell,text("▣  一起收",24,GREEN,true));gap(shell,36);add(shell,text("把想去的地方，\n留給下一次出發。",34,INK,true));gap(shell,18);add(shell,text("和朋友一起收藏台灣吃喝玩樂。\n從分享開始，讓好地方不再滑過就忘。",16,MUTED,false));gap(shell,32);
        add(shell,action("使用 Google 帳號繼續",true,this::signIn));gap(shell,12);add(shell,action("先體驗看看",false,()->{accepted=true;getPreferences(0).edit().putBoolean("demoAccepted",true).apply();store.localMode();store.start();render();openIncoming();}));gap(shell,20);add(shell,text("不主動推播。你的心得由你編輯。\n體驗資料只儲存在這台裝置。",12,MUTED,false));
    }
    void signIn(){
        if(!store.configured()){new AlertDialog.Builder(this).setTitle("尚未連接 Google 登入").setMessage("此安裝版本可先使用本機體驗。啟用真正的 Google 登入與共用同步，需要先設定 Firebase 專案、google-services.json 及 OAuth 用戶端，詳見專案 README。").setPositiveButton("知道了",null).show();return;}
        int res=getResources().getIdentifier("default_web_client_id","string",getPackageName());if(res==0){toast("缺少 Google Web Client ID 設定。");return;}
        GetGoogleIdOption opt=new GetGoogleIdOption.Builder().setServerClientId(getString(res)).setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build();
        GetCredentialRequest request=new GetCredentialRequest.Builder().addCredentialOption(opt).build();
        CredentialManager.create(this).getCredentialAsync(this,request,new CancellationSignal(),handler::post,new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
            @Override public void onResult(GetCredentialResponse response){
                try{Credential credential=response.getCredential();if(!(credential instanceof CustomCredential)||!credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)){toast("不支援的登入憑證。");return;}
                    GoogleIdTokenCredential google=GoogleIdTokenCredential.createFrom(credential.getData());
                    FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(google.getIdToken(),null)).addOnSuccessListener(r->{store.stop();store.places.clear();store.records.clear();store.lists.clear();store.listId="";accepted=true;store.start();render();openIncoming();}).addOnFailureListener(e->toast("登入未完成，請確認 Firebase 設定與網路。"));
                }catch(Exception e){toast("Google 憑證處理失敗，請重試。");}
            }
            @Override public void onError(GetCredentialException e){toast("Google 登入未完成，可稍後重試。");}
        });
    }
    void render(){if(!accepted)return;base();if(selected!=null){detail(selected);return;}switch(tab){case "清單":groups();break;case "我的":profile();break;default:home();}}
    void heading(String eyebrow,String title,String subtitle){add(body,text(eyebrow,11,GREEN,true));gap(body,8);add(body,text(title,30,INK,true));gap(body,8);add(body,text(subtitle,14,MUTED,false));gap(body,22);}
    void home(){
        pendingOnly=tab.equals("待補");heading("YOUR NEXT LITTLE ADVENTURE",pendingOnly?"先收著，慢慢整理":"好地方，一起收。",pendingOnly?"不必一次填完，靈感先留下。":"下一次出發，就從大家的口袋名單開始。");
        if(!sharedText.isEmpty()||!sharedImages.isEmpty()){add(body,action("繼續儲存剛才的分享",true,this::openIncoming));gap(body,12);}
        LinearLayout group=card(body);LinearLayout gr=row();weighted(gr,text(store.listName,16,INK,true));gr.addView(action("切換 ▾",false,this::chooseList));add(group,gr);gap(group,6);add(group,text(store.cloud()?"共用清單 · 開啟 App 時同步":"本機體驗 · 範例地點皆為虛構",12,MUTED,false));
        EditText search=input("搜尋地點、備註，例如：草莓冰",query);add(body,search);gap(body,12);
        LinearLayout filter=row();pill(filter,city.isEmpty()?"全部縣市 ▾":city+" ▾",!city.isEmpty(),()->pickCity(false,null));pill(filter,district.isEmpty()?"鄉鎮市區 ▾":district+" ▾",!district.isEmpty(),()->pickDistrict(false,null));add(body,filter);gap(body,10);
        HorizontalScrollView cats=new HorizontalScrollView(this);cats.setHorizontalScrollBarEnabled(false);LinearLayout catrow=row();pill(catrow,"全部",category.isEmpty(),()->{category="";render();});for(String c:categories)pill(catrow,c,c.equals(category),()->{category=c;render();});cats.addView(catrow);add(body,cats);gap(body,20);
        LinearLayout title=row();weighted(title,text(pendingOnly?"待補資料":"大家的收藏",18,INK,true));title.addView(action("＋ 新增收藏",true,()->editor(null,"",new ArrayList<>())));add(body,title);gap(body,14);
        content=col();add(body,content);renderCards();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();renderCards();}public void afterTextChanged(Editable e){}});
    }
    void renderCards(){
        if(content==null)return;content.removeAllViews();ArrayList<Place> result=new ArrayList<>();
        for(Place p:store.places)if((!pendingOnly||p.pending)&&PlaceLogic.matches(p,query,city,district,category,store.notes(p)))result.add(p);
        result.sort((a,b)->Long.compare(b.updatedAt,a.updatedAt));add(content,text(result.size()+" 個收藏",12,MUTED,false));gap(content,12);
        if(result.isEmpty()){LinearLayout empty=card(content);add(empty,text("還沒有符合的收藏",20,INK,true));gap(empty,10);add(empty,text("試試其他地區或關鍵字，也可以把剛看到的好地方收進來。",14,MUTED,false));gap(empty,14);add(empty,action("清除篩選",false,()->{query="";city="";district="";category="";render();}));return;}
        for(Place p:result){LinearLayout c=card(content);LinearLayout top=row();TextView symbol=text(p.pending?"◷":p.categories.contains("美食")?"食":p.categories.contains("景點")?"遊":p.categories.contains("住宿")?"宿":"玩",24,GREEN,true);symbol.setGravity(Gravity.CENTER);symbol.setBackground(bg(PALE,14));top.addView(symbol,new LinearLayout.LayoutParams(dp(54),dp(54)));LinearLayout info=col();info.setPadding(dp(12),0,0,0);add(info,text(p.name.isBlank()?"尚未命名的收藏":p.name,18,INK,true));add(info,text(p.city.isEmpty()?"地區待補":p.city+" · "+p.district,12,MUTED,false));weighted(top,info);add(c,top);gap(c,14);LinearLayout tags=row();for(String cat:p.categories)tag(tags,cat);if(p.pending)tag(tags,"待補資料");if(!p.links.isEmpty())tag(tags,p.links.size()+" 個來源");add(c,tags);gap(c,10);String notes=store.notes(p);String snippet=notes.isBlank()?p.evidence:notes;if(!snippet.isBlank()){TextView desc=text(snippet,13,MUTED,false);desc.setMaxLines(2);desc.setEllipsize(TextUtils.TruncateAt.END);add(c,desc);gap(c,10);}add(c,text(p.creatorName+" 收藏   ·   "+relative(p.updatedAt)+"   ↗",11,MUTED,false));c.setOnClickListener(v->{selected=p;render();});}
    }
    String relative(long at){long days=(System.currentTimeMillis()-at)/86400000;return days<=0?"今天更新":days+" 天前更新";}
    EditText input(String hint,String value){EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextSize(15);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setMinHeight(dp(50));e.setBackground(bg(Color.WHITE,12));return e;}
    void chooseList(){if(!store.cloud()){toast("體驗模式只有本機清單。登入 Google 後可建立共用清單。");return;}ArrayList<String> ids=new ArrayList<>(store.lists.keySet());String[] names=new String[ids.size()];for(int i=0;i<ids.size();i++)names[i]=Place.s(store.lists.get(ids.get(i)),"name");new AlertDialog.Builder(this).setTitle("切換共用清單").setItems(names,(d,w)->{selected=null;store.select(ids.get(w));}).setNeutralButton("管理清單",(d,w)->{tab="清單";render();}).show();}
    void pickCity(boolean editing,java.util.function.Consumer<String> chosen){ArrayList<String> a=new ArrayList<>();a.add(editing?"地區待補":"全部縣市");a.addAll(regions.keySet());new AlertDialog.Builder(this).setTitle("選擇縣市").setItems(a.toArray(new String[0]),(d,w)->{String v=w==0?"":a.get(w);if(editing)chosen.accept(v);else{city=v;district="";render();}}).show();}
    void pickDistrict(boolean editing,java.util.function.Consumer<String> chosen){chooseDistrict(city,editing,chosen);}
    void chooseDistrict(String c,boolean editing,java.util.function.Consumer<String> chosen){if(!regions.containsKey(c)){toast("請先選擇縣市。");return;}ArrayList<String>a=new ArrayList<>();a.add(editing?"鄉鎮市區待補":"全部鄉鎮市區");a.addAll(Arrays.asList(regions.get(c)));new AlertDialog.Builder(this).setTitle(c).setItems(a.toArray(new String[0]),(d,w)->{String v=w==0?"":a.get(w);if(editing)chosen.accept(v);else{district=v;render();}}).show();}
    void openUrl(String u){if(!PlaceLogic.safeUrl(u)){toast("連結格式不正確，請使用 http 或 https。");return;}try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(ActivityNotFoundException e){toast("這台裝置沒有可開啟連結的應用程式。");}}
    void detail(Place p){
        add(body,action("‹ 返回收藏",false,()->{selected=null;render();}));gap(body,20);heading(p.pending?"WAITING TO BE DISCOVERED":"SAVED TOGETHER",p.name.isBlank()?"尚未命名的收藏":p.name,p.city+" "+p.district);
        LinearLayout c=card(body);LinearLayout tags=row();for(String cat:p.categories)tag(tags,cat);if(p.pending)tag(tags,"待補資料");add(c,tags);gap(c,14);add(c,text(p.address.isBlank()?"地址待補":p.address,15,INK,false));gap(c,14);
        add(c,action("在 Google Maps 查看 ↗",true,()->{String q=(!p.address.isBlank()?p.address:p.city+p.district+" "+p.name).trim();if(q.isEmpty()){toast("請先補上地點名稱或地址。");return;}openUrl("https://www.google.com/maps/search/?api=1&query="+Uri.encode(q));}));gap(c,10);add(c,action("編輯地點資料",false,()->editor(p,"",new ArrayList<>())));
        if(!p.evidence.isBlank()){LinearLayout e=card(body);add(e,text("分享文字／辨識線索",15,INK,true));gap(e,8);add(e,text(p.evidence,14,MUTED,false));}
        LinearLayout sources=card(body);add(sources,text("收藏來源",16,INK,true));gap(sources,10);if(p.links.isEmpty())add(sources,text("尚未加入連結",13,MUTED,false));for(String link:p.links){TextView t=text("↗  "+link,13,GREEN,false);t.setPadding(0,dp(8),0,dp(8));t.setOnClickListener(v->openUrl(link));add(sources,t);}
        if(!p.images.isEmpty()){gap(sources,12);add(sources,text("補充截圖 · 僅儲存於此裝置",12,MUTED,false));for(String path:p.images){File f=new File(path);if(f.exists()){ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setMaxHeight(dp(240));image.setImageURI(Uri.fromFile(f));image.setContentDescription("收藏補充截圖");add(sources,image);}}}
        LinearLayout mine=card(body);add(mine,text("我的紀錄",18,INK,true));add(mine,text("署名："+store.userName()+" · 只有你能編輯",12,MUTED,false));gap(mine,12);
        Map<String,Object> rec=store.records.get(p.id+"_"+store.uid());EditText note=input("備註、心得、想吃的招牌菜…",rec==null?"":Place.s(rec,"text"));note.setMinLines(3);note.setGravity(Gravity.TOP);add(mine,note);CheckBox visited=new CheckBox(this);visited.setText("去過了");visited.setChecked(rec!=null&&Boolean.TRUE.equals(rec.get("visited")));add(mine,visited);CheckBox again=new CheckBox(this);again.setText("想再去");again.setChecked(rec!=null&&Boolean.TRUE.equals(rec.get("again")));add(mine,again);add(mine,action("儲存我的紀錄",true,()->store.record(p,note.getText().toString(),visited.isChecked(),again.isChecked(),()->{toast("我的紀錄已儲存");render();})));
        LinearLayout friends=card(body);add(friends,text("朋友的心得",17,INK,true));int count=0;for(Map<String,Object> r:store.records.values())if(p.id.equals(r.get("placeId"))&&!store.uid().equals(r.get("uid"))){gap(friends,14);add(friends,text(Place.s(r,"name")+(Boolean.TRUE.equals(r.get("visited"))?" · 去過":"")+(Boolean.TRUE.equals(r.get("again"))?" · 想再去":""),14,GREEN,true));add(friends,text(Place.s(r,"text"),14,INK,false));count++;}if(count==0){gap(friends,10);add(friends,text("等朋友來寫下第一則心得。",13,MUTED,false));}
        add(body,text("由 "+p.creatorName+" 新增 · "+relative(p.updatedAt),12,MUTED,false));gap(body,12);
        if(store.canDelete(p))add(body,action("刪除此收藏",false,()->new AlertDialog.Builder(this).setTitle("刪除此收藏？").setMessage("將從共用清單移除此地點，所有成員都不再看到它。").setNegativeButton("取消",null).setPositiveButton("刪除",(d,w)->store.delete(p,()->{selected=null;toast("已刪除收藏");render();})).show()));
    }
    void groups(){
        heading("BETTER WITH FRIENDS","口袋名單，一起存。","加入需管理員同意。每個人都能修正地點資料。");
        LinearLayout c=card(body);add(c,text(store.listName,21,INK,true));gap(c,10);add(c,text(store.cloud()?"你的身分："+(store.isAdmin()?"管理員":"成員"):"目前為本機體驗，尚未與朋友共用。",14,MUTED,false));
        if(store.cloud()&&!store.listId.isEmpty()){gap(c,14);add(c,text("清單代碼："+store.listId,13,INK,false));gap(c,10);add(c,action("分享邀請代碼",true,()->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,"一起收｜邀請加入「"+store.listName+"」\n清單代碼："+store.listId+"\n在 App「清單」輸入代碼，等待管理員同意。");startActivity(Intent.createChooser(i,"分享清單邀請"));}));gap(c,10);add(c,action("切換清單",false,this::chooseList));if(store.isAdmin()){gap(c,10);add(c,action("查看加入申請",false,this::reviewRequests));}}
        LinearLayout controls=card(body);add(controls,text("開始新的共用收藏",18,INK,true));gap(controls,14);add(controls,action("＋ 建立清單",true,()->ask("建立共用清單","例如：週末出走小隊",name->{if(name.isBlank()||name.length()>50){toast("清單名稱需為 1–50 字。");return;}store.createList(name,()->{toast("清單已建立");render();});})));gap(controls,10);add(controls,action("輸入代碼，申請加入",false,()->ask("申請加入清單","貼上朋友給你的清單代碼",id->{if(!id.matches("[A-Za-z0-9_-]{10,80}")){toast("清單代碼格式不正確。");return;}store.join(id,()->toast("申請已送出，管理員同意後清單會出現。若代碼有誤，請向朋友確認。"));})));
        LinearLayout rules=card(body);add(rules,text("一起收藏的小默契",17,INK,true));gap(rules,12);add(rules,text("• 所有成員都能編輯地點資料\n• 新增者、管理員能刪除地點\n• 個人備註與心得只有本人能修改\n• 不推播，開啟 App 查看最新收藏",14,MUTED,false));
    }
    void reviewRequests(){if(!store.cloud()||!store.isAdmin())return;store.requests(docs->{if(docs.isEmpty()){toast("目前沒有待審核申請。");return;}String[] names=new String[docs.size()];for(int i=0;i<docs.size();i++)names[i]=Objects.toString(docs.get(i).getString("name"),"朋友");new AlertDialog.Builder(this).setTitle("待審核加入申請").setItems(names,(d,w)->{DocumentSnapshot doc=docs.get(w);new AlertDialog.Builder(this).setTitle("讓「"+names[w]+"」加入？").setMessage("同意後，對方可以查看與編輯這份清單的地點資料。").setNegativeButton("稍後",null).setNeutralButton("拒絕",(x,y)->store.decide(doc.getId(),false,()->toast("已拒絕"))).setPositiveButton("同意加入",(x,y)->store.decide(doc.getId(),true,()->toast("成員已加入"))).show();}).show();});}
    void ask(String title,String hint,java.util.function.Consumer<String> done){EditText e=input(hint,"");LinearLayout wrap=col();wrap.setPadding(dp(24),dp(10),dp(24),dp(10));add(wrap,e);new AlertDialog.Builder(this).setTitle(title).setView(wrap).setNegativeButton("取消",null).setPositiveButton("確定",(d,w)->done.accept(e.getText().toString().trim())).show();}
    void profile(){
        heading("YOUR LITTLE COLLECTION","每一趟，都值得記得。",store.userName());LinearLayout c=card(body);add(c,text(store.cloud()?"Google 帳號已登入":"目前使用本機體驗",20,INK,true));gap(c,10);add(c,text(store.cloud()?"收藏由 Firebase 同步。個人心得以帳號署名。":"示範地點為虛構資料，可自由新增、編輯與刪除。體驗資料與 Google 共用清單分開儲存。",14,MUTED,false));gap(c,16);add(c,action(store.cloud()?"登出 Google 帳號":"登入 Google，開始共用",true,()->{if(store.cloud()){store.stop();FirebaseAuth.getInstance().signOut();store.localMode();getPreferences(0).edit().putBoolean("demoAccepted",false).apply();welcome();}else signIn();}));
        LinearLayout stats=card(body);int visited=0,again=0;for(Map<String,Object> r:store.records.values())if(store.uid().equals(r.get("uid"))&&find(Place.s(r,"placeId"))!=null){if(Boolean.TRUE.equals(r.get("visited")))visited++;if(Boolean.TRUE.equals(r.get("again")))again++;}add(stats,text(store.places.size()+" 個收藏    "+visited+" 個去過    "+again+" 個想再去",16,GREEN,true));gap(stats,10);add(stats,text("統計範圍：目前清單",12,MUTED,false));
        LinearLayout how=card(body);add(how,text("看到好地方，這樣收",18,INK,true));gap(how,12);add(how,text("1. 在 FB 或瀏覽器點「分享」\n2. 選擇「一起收」\n3. 確認地點，或先存到待補資料\n4. 補上分類與自己的備註",14,MUTED,false));gap(how,14);add(how,text("連結本身無法保證辨識 FB 貼文內容。可補上分享文字或截圖協助整理；目前截圖只保留在本機，尚未提供 OCR 或雲端圖片同步。",12,MUTED,false));
    }
    void editor(Place original,String incoming,ArrayList<String> images){
        if(store.cloud()&&store.listId.isEmpty()){toast("請先建立或加入共用清單，再收藏地點。");tab="清單";render();return;}
        Place draft=original==null?new Place():Place.from(original.map());if(original!=null)draft.images.addAll(original.images);draft.images.addAll(images);
        if(original==null){draft.creator=store.uid();draft.creatorName=store.userName();draft.evidence=incoming;draft.links.addAll(PlaceLogic.urls(incoming));infer(draft,incoming);}
        LinearLayout form=col();form.setPadding(dp(22),dp(16),dp(22),dp(24));ScrollView scroll=new ScrollView(this);scroll.addView(form);
        add(form,text(original==null?"靈感先留下，細節慢慢補。":"一起把好地方整理好。",19,INK,true));gap(form,8);add(form,text("儲存至「"+store.listName+"」",13,MUTED,false));gap(form,18);
        if(!incoming.isBlank()){add(form,text("分享文字已帶入，請確認。僅有連結時，無法直接讀取 FB 內容。",13,GREEN,false));gap(form,12);}
        EditText evidence=field(form,"分享文字／辨識線索",draft.evidence,"貼上店名、地址或原貼文文字");evidence.setMinLines(2);
        EditText name=field(form,"地點或整篇貼文名稱",draft.name,"例如：想去吃的草莓冰店");
        LinearLayout region=row();TextView cityBtn=action(draft.city.isBlank()?"縣市 ▾":draft.city,false,()->{}),districtBtn=action(draft.district.isBlank()?"鄉鎮市區 ▾":draft.district,false,()->{});
        cityBtn.setOnClickListener(v->pickCity(true,c->{draft.city=c;draft.district="";cityBtn.setText(c.isBlank()?"縣市 ▾":c);districtBtn.setText("鄉鎮市區 ▾");}));districtBtn.setOnClickListener(v->chooseDistrict(draft.city,true,d->{draft.district=d;districtBtn.setText(d.isBlank()?"鄉鎮市區 ▾":d);}));weighted(region,cityBtn);weighted(region,districtBtn);add(form,region);gap(form,14);
        EditText address=field(form,"地址",draft.address,"可稍後補上");add(form,text("分類 · 可複選",13,INK,true));LinearLayout catrow=row();for(String cat:categories){CheckBox cb=new CheckBox(this);cb.setText(cat);cb.setTextSize(13);cb.setChecked(draft.categories.contains(cat));cb.setOnCheckedChangeListener((button,on)->{if(on&&!draft.categories.contains(cat))draft.categories.add(cat);if(!on)draft.categories.remove(cat);});weighted(catrow,cb);}add(form,catrow);gap(form,12);
        EditText links=field(form,"來源連結 · 每行一個",String.join("\n",draft.links),"https://…");links.setMinLines(2);
        EditText myNote=original==null?field(form,"我的署名備註","","例如：想吃草莓冰，下次揪大家"):null;
        attachmentLabel=text(draft.images.size()+" 張補充截圖 · 僅此裝置可見",12,MUTED,false);add(form,attachmentLabel);attachmentDraft=draft;gap(form,8);add(form,action("＋ 補一張截圖",false,()->{Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);picker.setType("image/*");picker.addCategory(Intent.CATEGORY_OPENABLE);picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);try{startActivityForResult(picker,3001);}catch(ActivityNotFoundException ex){toast("找不到圖片選擇器，請改用分享圖片。");}}));gap(form,12);
        add(form,action("從文字找候選地點／拆分貼文",false,()->{
            infer(draft,evidence.getText().toString());cityBtn.setText(draft.city.isBlank()?"縣市 ▾":draft.city);districtBtn.setText(draft.district.isBlank()?"鄉鎮市區 ▾":draft.district);if(address.getText().toString().isBlank())address.setText(draft.address);
            List<String> candidates=PlaceLogic.candidates(evidence.getText().toString());
            if(candidates.isEmpty()){toast("請先貼上含店名的文字。也可以先存整篇貼文。");return;}
            new AlertDialog.Builder(this).setTitle("選一個候選名稱（請自行確認）").setItems(candidates.toArray(new String[0]),(d,w)->name.setText(candidates.get(w))).setMessage(null).setNegativeButton("取消",null).show();
        }));gap(form,12);add(form,text("多地點貼文可先整篇儲存，再於詳情編輯複製來源、逐一拆成地點。候選名稱只是文字線索，不代表已查證。",12,MUTED,false));gap(form,12);
        CheckBox pending=new CheckBox(this);pending.setText("先存到待補資料");pending.setChecked(original==null||draft.pending);add(form,pending);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(original==null?"新增收藏":"編輯收藏").setView(scroll).setNegativeButton("取消",null).setNeutralButton("儲存並繼續拆分",null).setPositiveButton("儲存收藏",null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->saveEditor(draft,original,false,name,address,evidence,links,myNote,pending,dialog));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w->saveEditor(draft,original,true,name,address,evidence,links,myNote,pending,dialog));
        });dialog.setOnDismissListener(d->{attachmentDraft=null;attachmentLabel=null;});dialog.show();
    }
    void infer(Place p,String evidence){
        String normalized=evidence.replace('台','臺');
        for(String c:regions.keySet())if(normalized.contains(c)){p.city=c;p.district="";for(String d:regions.get(c))if(normalized.contains(d)){p.district=d;break;}break;}
        if(p.address.isBlank()&&!p.city.isBlank())for(String line:evidence.split("\\r?\\n"))if(line.replace('台','臺').contains(p.city)&&line.matches(".*[路街巷].*")){p.address=line.trim();break;}
    }
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);if(code!=3001||result!=RESULT_OK||data==null||attachmentDraft==null)return;ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());for(Uri uri:uris){if(attachmentDraft.images.size()>=5){toast("每筆最多保留 5 張截圖。");break;}String path=copyImage(uri);if(path!=null)attachmentDraft.images.add(path);}if(attachmentLabel!=null)attachmentLabel.setText(attachmentDraft.images.size()+" 張補充截圖 · 僅此裝置可見");}
    EditText field(LinearLayout parent,String label,String value,String hint){add(parent,text(label,13,INK,true));gap(parent,6);EditText e=input(hint,value);add(parent,e);gap(parent,14);return e;}
    void saveEditor(Place draft,Place original,boolean another,EditText name,EditText address,EditText evidence,EditText links,EditText myNote,CheckBox pending,AlertDialog dialog){
        draft.name=name.getText().toString().trim();draft.address=address.getText().toString().trim();draft.evidence=evidence.getText().toString().trim();draft.links=new ArrayList<>();
        for(String line:links.getText().toString().split("\\r?\\n")){String s=line.trim();if(!s.isEmpty()){if(!PlaceLogic.safeUrl(s)){toast("來源連結格式不正確，請每行填寫一個 http／https 連結。");return;}if(!draft.links.contains(s))draft.links.add(s);}}
        draft.pending=pending.isChecked();if(!draft.pending&&(draft.name.isBlank()||draft.city.isBlank()||draft.district.isBlank()||draft.categories.isEmpty())){toast("完成整理需要名稱、縣市、鄉鎮市區與至少一個分類；或勾選待補資料先存。");return;}
        if(draft.name.isBlank()&&draft.links.isEmpty()&&draft.evidence.isBlank()&&draft.images.isEmpty()){toast("至少填入名稱、連結、文字或截圖。");return;}
        if(draft.name.length()>120||draft.evidence.length()>12000||draft.address.length()>500||draft.links.size()>30){toast("資料過長：名稱最多 120 字，分享文字 12000 字，地址 500 字，來源 30 個。");return;}
        Place duplicate=null;for(Place p:store.places)if((original==null||!p.id.equals(original.id))&&PlaceLogic.duplicate(p,draft)){duplicate=p;break;}
        String note=myNote==null?"":myNote.getText().toString().trim();
        Runnable saved=()->{if(original==null)clearIncoming();toast("收藏已儲存"+(draft.pending?"，稍後可在待補資料整理":""));if(another){editor(null,draft.evidence,new ArrayList<>(draft.images));}else{selected=find(draft.id);render();}};
        if(duplicate!=null){Place existing=duplicate;new AlertDialog.Builder(this).setTitle("清單裡可能已有這個地方").setMessage("「"+existing.name+"」可能是同一個地點。可以將新來源與你的備註加入原收藏。").setNegativeButton("回去確認",null).setNeutralButton("仍新增一筆",(d,w)->commit(draft,note,dialog,saved)).setPositiveButton("加入原收藏",(d,w)->merge(existing,draft,note,dialog,()->{if(original==null)clearIncoming();selected=existing;render();toast("來源與備註已加入原收藏");})).show();}
        else commit(draft,note,dialog,saved);
    }
    void commit(Place p,String note,AlertDialog dialog,Runnable done){
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        store.saveWithNote(p,note,()->{dialog.dismiss();done.run();});
        handler.postDelayed(()->{if(dialog.isShowing())dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);},3000);
    }
    void merge(Place existing,Place draft,String note,AlertDialog dialog,Runnable done){
        Place p=Place.from(existing.map());p.images.addAll(existing.images);for(String s:draft.links)if(!p.links.contains(s))p.links.add(s);p.images.addAll(draft.images);
        if(!draft.evidence.isBlank()&&!p.evidence.contains(draft.evidence))p.evidence+=(p.evidence.isBlank()?"":"\n\n")+draft.evidence;
        commit(p,note,dialog,done);
    }
}

