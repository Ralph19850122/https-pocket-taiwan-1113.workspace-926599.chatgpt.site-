package tw.together.go;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class PlaceLogicTest {
    private Place place(String name,String city,String district){Place p=new Place();p.name=name;p.city=city;p.district=district;p.categories.add("美食");return p;}
    @Test public void samePlaceNormalizesTaiwanAndSpaces(){assertTrue(PlaceLogic.duplicate(place("草莓 冰","臺北市","大同區"),place("草莓冰","台北市","大同區")));}
    @Test public void separateBranchesAreNotDuplicates(){assertFalse(PlaceLogic.duplicate(place("草莓冰","臺北市","大同區"),place("草莓冰","臺北市","信義區")));}
    @Test public void emptyDraftsAreNotDuplicates(){assertFalse(PlaceLogic.duplicate(new Place(),new Place()));}
    @Test public void sameUrlWithTrackingIsDuplicate(){Place a=new Place(),b=new Place();a.links.add("https://www.example.com/place?fbclid=123");b.links.add("https://example.com/place");assertTrue(PlaceLogic.duplicate(a,b));}
    @Test public void facebookDistinctPostsStayDistinct(){Place a=new Place(),b=new Place();a.links.add("https://facebook.com/story.php?story_fbid=111&id=5");b.links.add("https://facebook.com/story.php?story_fbid=222&id=5");assertFalse(PlaceLogic.duplicate(a,b));}
    @Test public void searchIncludesPersonalNotesAndAllFilters(){Place p=place("小店","宜蘭縣","宜蘭市");assertTrue(PlaceLogic.matches(p,"草莓冰","宜蘭縣","宜蘭市","美食","下次吃草莓冰"));assertFalse(PlaceLogic.matches(p,"草莓冰","宜蘭縣","羅東鎮","美食","下次吃草莓冰"));assertFalse(PlaceLogic.matches(p,"","宜蘭縣","","住宿",""));}
    @Test public void extractLinksDeduplicatesAndTrimsSentencePunctuation(){assertEquals(Arrays.asList("https://example.com/a"),PlaceLogic.urls("分享 https://example.com/a。\nhttps://example.com/a"));}
    @Test public void unsafeLinksCannotOpen(){assertFalse(PlaceLogic.safeUrl("javascript:alert(1)"));assertFalse(PlaceLogic.safeUrl("file:///etc/passwd"));assertFalse(PlaceLogic.safeUrl("https://"));assertTrue(PlaceLogic.safeUrl("https://maps.google.com/?q=test"));}
    @Test public void candidateExtractionDoesNotInventMetadata(){assertEquals(Arrays.asList("山邊小店","海邊咖啡"),PlaceLogic.candidates("📍山邊小店\nhttps://example.com\n宜蘭縣頭城鎮濱海路1號\n海邊咖啡"));}
}
