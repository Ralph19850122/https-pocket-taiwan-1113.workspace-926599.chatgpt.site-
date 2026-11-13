package tw.together.go;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

public final class PlaceLogic {
    private PlaceLogic() {}
    public static String normalize(String s) { return s.toLowerCase(Locale.ROOT).replace('臺','台').replaceAll("[\\s\\p{Punct}、，。]", ""); }
    public static List<String> urls(String text) {
        List<String> result=new ArrayList<>();
        Matcher m=Pattern.compile("https?://[^\\s<>\"，。]+",Pattern.CASE_INSENSITIVE).matcher(text);
        while(m.find()) { String u=m.group().replaceAll("[)）]+$", ""); if(!result.contains(u)) result.add(u); }
        return result;
    }
    public static boolean safeUrl(String s) { try { URI u=new URI(s); return ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme())) && u.getHost()!=null; } catch(Exception e){return false;} }
    public static String canonical(String s) {
        try { URI u=new URI(s); List<String> query=new ArrayList<>();if(u.getRawQuery()!=null)for(String part:u.getRawQuery().split("&"))if(!part.matches("(?i)(utm_[^=]*|fbclid)=.*")&&!part.isEmpty())query.add(part);Collections.sort(query);return u.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "") + (u.getPath()==null?"":u.getPath().replaceAll("/$", "")) + (query.isEmpty()?"":"?"+String.join("&",query)); } catch(Exception e){ return s; }
    }
    public static boolean duplicate(Place a,Place b) {
        for(String x:a.links) for(String y:b.links) if(canonical(x).equals(canonical(y))) return true;
        return !a.name.isBlank() && !a.city.isBlank() && normalize(a.name).equals(normalize(b.name)) && normalize(a.city).equals(normalize(b.city)) && normalize(a.district).equals(normalize(b.district));
    }
    public static boolean matches(Place p,String query,String city,String district,String category,String notes) {
        return (city.isEmpty()||city.equals(p.city)) && (district.isEmpty()||district.equals(p.district)) && (category.isEmpty()||p.categories.contains(category)) && normalize(p.name+p.address+p.evidence+notes).contains(normalize(query));
    }
    public static List<String> candidates(String text) {
        List<String> out=new ArrayList<>();
        for(String line:text.split("\\r?\\n")) {
            String s=line.replaceFirst("^[\\s#•📍・\\d.、]+", "").trim();
            if(!s.isEmpty() && s.length()<=40 && !s.contains("http") && !s.matches(".*[縣市].*[路街巷].*")) out.add(s);
            if(out.size()==8) break;
        }
        return out;
    }
}
