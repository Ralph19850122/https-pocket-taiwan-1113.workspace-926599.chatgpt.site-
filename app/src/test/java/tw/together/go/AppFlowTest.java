package tw.together.go;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.EditText;
import android.widget.CheckBox;
import android.view.View;
import android.view.ViewGroup;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AppFlowTest {
    MainActivity app;
    @Before public void setup(){app=Robolectric.buildActivity(MainActivity.class).setup().get();app.accepted=true;app.store.localMode();app.render();}
    @After public void cleanup(){app.finish();}
    @Test public void demoHasAllCategoriesAndPendingItems(){assertEquals(5,app.store.places.size());app.tab="待補";app.render();assertEquals(1,app.content.getChildCount()-2);}
    @Test public void textSharePreservesUrlAndEvidence(){Intent i=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"海邊咖啡\n宜蘭縣頭城鎮濱海路1號\nhttps://example.com/post");app.receive(i);assertTrue(app.sharedText.contains("https://example.com/post"));Place p=new Place();app.infer(p,app.sharedText);assertEquals("宜蘭縣",p.city);assertEquals("頭城鎮",p.district);assertTrue(p.address.contains("濱海路"));}
    @Test public void addingLinkOnlyCreatesPendingPlace(){int count=app.store.places.size();app.editor(null,"https://example.com/new",new java.util.ArrayList<>());Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(count+1,app.store.places.size());Place p=app.store.places.get(app.store.places.size()-1);assertTrue(p.pending);assertEquals("https://example.com/new",p.links.get(0));}
    @Test public void savedPersonalRecordIsSearchableAndSurvivesReload(){Place p=app.store.places.get(0);app.store.record(p,"草莓冰下次必吃",true,true,()->{});Store reloaded=new Store(app);assertTrue(reloaded.notes(p).contains("草莓冰"));assertTrue(PlaceLogic.matches(p,"必吃","","","",reloaded.notes(p)));}
    @Test public void memberCannotDeleteOtherCreatorsInLocalPolicy(){Place p=new Place();p.creator="friend";app.store.admin="other-admin";assertFalse(app.store.canDelete(p));app.store.admin=app.store.uid();assertTrue(app.store.canDelete(p));}
}
