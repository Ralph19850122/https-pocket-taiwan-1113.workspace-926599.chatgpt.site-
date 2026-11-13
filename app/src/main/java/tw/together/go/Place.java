package tw.together.go;

import java.util.*;

public class Place {
    public String id = UUID.randomUUID().toString(), name = "", city = "", district = "", address = "", creator = "", creatorName = "", evidence = "";
    public List<String> categories = new ArrayList<>(), links = new ArrayList<>(), images = new ArrayList<>();
    public boolean pending = true;
    public long updatedAt = System.currentTimeMillis();
    public Place() {}
    public Map<String,Object> map() {
        Map<String,Object> m = new HashMap<>();
        m.put("id",id); m.put("name",name); m.put("city",city); m.put("district",district); m.put("address",address);
        m.put("creator",creator); m.put("creatorName",creatorName); m.put("evidence",evidence); m.put("categories",categories);
        m.put("links",links); m.put("pending",pending); m.put("updatedAt",updatedAt);
        return m;
    }
    public static Place from(Map<String,Object> m) {
        Place p = new Place();
        p.id=s(m,"id"); p.name=s(m,"name"); p.city=s(m,"city"); p.district=s(m,"district"); p.address=s(m,"address");
        p.creator=s(m,"creator"); p.creatorName=s(m,"creatorName"); p.evidence=s(m,"evidence");
        p.categories=list(m.get("categories")); p.links=list(m.get("links"));
        p.pending=!Boolean.FALSE.equals(m.get("pending"));
        Object t=m.get("updatedAt"); if(t instanceof Number) p.updatedAt=((Number)t).longValue();
        return p;
    }
    static String s(Map<String,Object> m,String key) { Object v=m.get(key); return v==null?"":v.toString(); }
    static List<String> list(Object o) { List<String> l=new ArrayList<>(); if(o instanceof List<?>) for(Object x:(List<?>)o) if(x!=null) l.add(x.toString()); return l; }
}
